package com.carterstach.deployerhold;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.config.server.physics.SimPhysics;
import dev.simulated_team.simulated.content.blocks.handle.HandleBlockEntity;
import dev.simulated_team.simulated.service.SimConfigService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;

/**
 * Runtime grip-session state for a Deployer in Grip: Pull / Grip: Hitch.
 * <p>
 * Lifecycle: extend → latch handle → retract while constrained → stay latched
 * until redstone powers the deployer (ungrip). Latch identity is the handle's
 * {@link BlockPos} (plot-local or overworld) and survives leave/rejoin.
 * <ul>
 *   <li>Pull: handle's sub-level is pulled toward the moving tip</li>
 *   <li>Hitch: deployer's sub-level is pulled toward the handle</li>
 * </ul>
 */
public class DeployerHoldController {
    /** Skip rebuild when the world-space goal hasn't moved (~1 mm). */
    private static final double GOAL_EPSILON_SQ = 1.0e-6;
    /** Facing vectors within this dot are treated as parallel (~2.5°). */
    private static final double PARALLEL_DOT = 0.999;
    /** Skip rebuild when facing bases haven't rotated (~2.5°). */
    private static final double FACING_EPSILON_SQ = 1.0e-4;

    private final DeployerBlockEntity deployer;
    private final DeployerHoldAccess access;

    private @Nullable HandleBlockEntity heldHandle;
    /** Exact handle block position (plot-local or overworld) — not a sub-level id. */
    private @Nullable BlockPos heldHandlePos;
    /** True when the latched handle lived in a Sable sub-level (plot-local BlockPos). */
    private boolean heldHandleWasInSubLevel;
    /** Sub-level UUID of the latched handle, when it lived in a plot. */
    private @Nullable UUID heldHandleSubLevelId;
    private boolean holding;
    /**
     * True after NBT load until the saved handle BlockPos is resolved again.
     * Keeps latch status across leave/rejoin while sub-levels finish loading.
     */
    private boolean pendingRestore;
    private @Nullable PhysicsConstraintHandle constraintHandle;
    /** True once Sable has called this Deployer's physics tick for the active grip. */
    private boolean physicsPathActive;
    private boolean lastConstraintHitch;
    private @Nullable BlockPos lastConstraintHandlePos;
    private final Vector3d lastWorldGoal = new Vector3d();
    private final Vector3d lastLocalAnchor = new Vector3d();
    private final Vector3d lastWorldFacing = new Vector3d();
    private final Vector3d lastLocalFacing = new Vector3d();
    private boolean hasLastConstraintPose;

    public DeployerHoldController(DeployerBlockEntity deployer) {
        this.deployer = deployer;
        this.access = DeployerHoldAccess.of(deployer);
    }

    public boolean isHolding() {
        return holding;
    }

    public @Nullable BlockPos getHeldHandlePos() {
        return heldHandlePos;
    }

    /** For goggles: a latchable handle is currently within tip range. */
    public boolean hasHandleInRange() {
        return findHandleAhead(deployer) != null;
    }

    /**
     * Grip-mode goggle latch line. Only meaningful while in a grip mode
     * (caller must gate on {@link DeployerHoldModes#isGrip}).
     */
    public LatchStatus latchStatus() {
        // Hitch moves the deployer — surface lock even with no handle in range.
        if (DeployerHoldModes.isHitch(access.deployerhold$getMode()) && isDeployerSubLevelLocked())
            return LatchStatus.TARGET_LOCKED;

        HandleBlockEntity handle = holding ? resolveHeldHandle() : findHandleAhead(deployer);
        if (handle != null && isMovableTargetLocked(handle))
            return LatchStatus.TARGET_LOCKED;
        if (holding)
            return LatchStatus.LATCHED;
        if (handle != null)
            return LatchStatus.READY;
        return LatchStatus.OPEN;
    }

    /**
     * True when the sub-level this mode would move is Physics-Staff locked.
     * Pull moves the handle's sub-level; Hitch moves the deployer's.
     */
    public boolean isMovableTargetLocked(@Nullable HandleBlockEntity handle) {
        if (DeployerHoldModes.isHitch(access.deployerhold$getMode()))
            return isDeployerSubLevelLocked();
        if (handle == null)
            return false;
        SubLevelAccess moving = Sable.HELPER.getContaining(handle);
        return moving instanceof SubLevel subLevel && SubLevelLocks.isLocked(subLevel);
    }

    private boolean isDeployerSubLevelLocked() {
        SubLevelAccess containing = Sable.HELPER.getContaining(deployer);
        return containing instanceof SubLevel subLevel && SubLevelLocks.isLocked(subLevel);
    }

    public void clear() {
        // Clear latch flags before removing the joint so a remove() failure
        // cannot leave holding=true and permanently block arm extension.
        holding = false;
        pendingRestore = false;
        heldHandle = null;
        heldHandlePos = null;
        heldHandleWasInSubLevel = false;
        heldHandleSubLevelId = null;
        physicsPathActive = false;
        lastConstraintHandlePos = null;
        hasLastConstraintPose = false;
        removeConstraint();
    }

    /**
     * Drop the live physics joint only. Keeps {@code holding} / {@code heldHandlePos}
     * so chunk unload + world save still persist the latch for leave/rejoin.
     */
    public void detachConstraint() {
        heldHandle = null;
        physicsPathActive = false;
        lastConstraintHandlePos = null;
        hasLastConstraintPose = false;
        removeConstraint();
        if (holding && heldHandlePos != null)
            pendingRestore = true;
    }

    public void release() {
        clear();
    }

    public void onModeChanged() {
        clear();
        access.deployerhold$setState(stateNamed("WAITING"));
        access.deployerhold$setTimer(0);
        deployer.sendData();
        deployer.setChanged();
    }

    public void write(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("DeployerHoldHolding", holding);
        if (heldHandlePos != null) {
            tag.putIntArray("DeployerHoldHandle", new int[]{
                    heldHandlePos.getX(), heldHandlePos.getY(), heldHandlePos.getZ()
            });
            tag.putBoolean("DeployerHoldHandleSubLevel", heldHandleWasInSubLevel);
            if (heldHandleSubLevelId != null)
                tag.putUUID("DeployerHoldHandleSubLevelId", heldHandleSubLevelId);
            else
                tag.remove("DeployerHoldHandleSubLevelId");
        } else {
            tag.remove("DeployerHoldHandle");
            tag.remove("DeployerHoldHandleSubLevel");
            tag.remove("DeployerHoldHandleSubLevelId");
        }
    }

    public void read(CompoundTag tag, HolderLookup.Provider registries) {
        // Partial client sync packets without our keys must not wipe a live latch.
        if (!tag.contains("DeployerHoldHolding"))
            return;

        holding = tag.getBoolean("DeployerHoldHolding");
        heldHandlePos = readHandlePos(tag);
        heldHandleWasInSubLevel = tag.getBoolean("DeployerHoldHandleSubLevel");
        heldHandleSubLevelId = tag.hasUUID("DeployerHoldHandleSubLevelId")
                ? tag.getUUID("DeployerHoldHandleSubLevelId")
                : null;
        heldHandle = null;
        removeConstraint();
        physicsPathActive = false;
        // Keep the latch across leave/rejoin. Resolve by handle BlockPos once the
        // target chunk / sub-level plot is loaded — never key by sub-level id alone
        // (one sub-level can hold many handles).
        if (holding && heldHandlePos == null) {
            clear();
            return;
        }
        pendingRestore = holding;
        if (holding)
            resolveHeldHandle();
    }

    private static @Nullable BlockPos readHandlePos(CompoundTag tag) {
        if (!tag.contains("DeployerHoldHandle"))
            return null;
        // Current format: int[3]
        int[] coords = tag.getIntArray("DeployerHoldHandle");
        if (coords.length == 3)
            return new BlockPos(coords[0], coords[1], coords[2]);
        // Legacy: NbtUtils BlockPos tag
        return NbtUtils.readBlockPos(tag, "DeployerHoldHandle").orElse(null);
    }

    /**
     * Called when the deployer arm finishes extending in a grip mode.
     * Latches a handle if one is in range; Create then retracts normally.
     */
    public void onArmFullyExtended() {
        if (holding)
            return;
        tryGrabAtExtension();
    }

    /**
     * Called every server tick while a grip is active.
     * Keeps the constraint following the animated tip; does not freeze the arm.
     */
    public void tickHold() {
        if (!holding)
            return;

        Level level = deployer.getLevel();
        if (level == null || level.isClientSide)
            return;

        HandleBlockEntity handle = resolveHeldHandle();
        if (handle == null) {
            // Saved latch: wait while the handle's chunk/plot may still be loading.
            // Drop only once the target is loaded and confirmed empty (not on rejoin
            // when plot-local coords would look like empty overworld blocks).
            if (pendingRestore) {
                if (isHeldHandleConfirmedMissing(level))
                    releaseAndParkArm();
                return;
            }
            releaseAndParkArm();
            return;
        }

        // Rejoin: reconnect the joint as soon as the handle exists. Skip holdRange
        // until the constraint is back — poses/distances are unreliable during load,
        // and drifted bodies must be yanked back rather than auto-dropped.
        if (pendingRestore) {
            physicsPathActive = false;
            updateConstraintFromGameTick();
            if (constraintHandle != null && constraintHandle.isValid()) {
                pendingRestore = false;
                deployer.sendData();
            }
            return;
        }

        if (!maintainHold(handle)) {
            releaseAndParkArm();
            return;
        }

        if (!physicsPathActive)
            updateConstraintFromGameTick();
    }

    /** Drop the latch and leave the arm in a startable WAITING state. */
    private void releaseAndParkArm() {
        release();
        access.deployerhold$setState(stateNamed("WAITING"));
        access.deployerhold$setTimer(0);
        deployer.sendData();
        deployer.setChanged();
    }

    public void physicsTick(ServerSubLevel deployerSubLevel) {
        if (!holding)
            return;
        updateConstraint(deployerSubLevel);
        // Only suppress the game-tick fallback once a live joint exists. A failed
        // rebuild during load must not permanently disable reconnect attempts.
        if (constraintHandle != null && constraintHandle.isValid())
            physicsPathActive = true;
    }

    private void updateConstraintFromGameTick() {
        Level level = deployer.getLevel();
        if (level == null || level.isClientSide)
            return;

        SubLevelAccess containing = Sable.HELPER.getContaining(deployer);
        if (containing instanceof ServerSubLevel serverDeployerSubLevel)
            updateConstraint(serverDeployerSubLevel);
        else
            updateConstraintOverworldDeployer();
    }

    /** Pull-mode only: overworld deployer can still yank a handle's sub-level. */
    private void updateConstraintOverworldDeployer() {
        if (!holding)
            return;
        if (DeployerHoldModes.isHitch(access.deployerhold$getMode()))
            return;

        Level level = deployer.getLevel();
        if (level == null || level.isClientSide)
            return;

        HandleBlockEntity handle = resolveHeldHandle();
        if (handle == null) {
            if (!pendingRestore)
                release();
            return;
        }

        SubLevelAccess handleAccess = Sable.HELPER.getContaining(handle);
        if (!(handleAccess instanceof ServerSubLevel handleServerSubLevel)) {
            if (!pendingRestore)
                release();
            return;
        }

        Vector3d grip = getDeployerGripPoint();
        Vector3d grab = handle.getGrabCenter();
        Vector3d worldGoal = Sable.HELPER.projectOutOfSubLevel(level, grip, new Vector3d());
        rebuildConstraint(null, handleServerSubLevel, false, handle, grip, grab, worldGoal);
    }

    private void updateConstraint(ServerSubLevel deployerSubLevel) {
        if (!holding)
            return;

        Level level = deployer.getLevel();
        if (level == null || level.isClientSide)
            return;

        HandleBlockEntity handle = resolveHeldHandle();
        if (handle == null) {
            if (!pendingRestore)
                release();
            return;
        }

        SubLevelAccess handleAccess = Sable.HELPER.getContaining(handle);
        if (!(handleAccess instanceof ServerSubLevel handleServerSubLevel)) {
            // Handle in overworld while deployer is on a sub-level: hitch only.
            if (!DeployerHoldModes.isHitch(access.deployerhold$getMode())) {
                removeConstraint();
                return;
            }
            Vector3d grip = getDeployerGripPoint();
            Vector3d grab = handle.getGrabCenter();
            Vector3d worldGoal = Sable.HELPER.projectOutOfSubLevel(level, grab, new Vector3d());
            rebuildConstraint(deployerSubLevel, null, true, handle, grip, grab, worldGoal);
            return;
        }

        if (handleServerSubLevel == deployerSubLevel) {
            removeConstraint();
            return;
        }

        boolean hitch = DeployerHoldModes.isHitch(access.deployerhold$getMode());
        Vector3d grip = getDeployerGripPoint();
        Vector3d grab = handle.getGrabCenter();
        // Pull: rider=handle, anchor=moving tip (worldGoal tracks retract).
        // Hitch: rider=deployer, anchor=handle; localAnchor must track the
        // retracting tip or the latch pose stays coincident and never yanks.
        Vector3d worldGoal = hitch
                ? Sable.HELPER.projectOutOfSubLevel(level, grab, new Vector3d())
                : Sable.HELPER.projectOutOfSubLevel(level, grip, new Vector3d());
        Vector3d localAnchor = hitch ? grip : grab;
        // Antiparallel goal: rider faces the anchor (negate local so bases oppose).
        Vector3d worldFacing = hitch ? worldFacingOf(handle) : worldFacingOf(deployer);
        Vector3d localFacing = (hitch ? localFacingOf(deployer) : localFacingOf(handle)).negate();

        boolean stable = constraintHandle != null
                && constraintHandle.isValid()
                && hitch == lastConstraintHitch
                && heldHandlePos != null
                && heldHandlePos.equals(lastConstraintHandlePos)
                && hasLastConstraintPose
                && worldGoal.distanceSquared(lastWorldGoal) < GOAL_EPSILON_SQ
                && localAnchor.distanceSquared(lastLocalAnchor) < GOAL_EPSILON_SQ
                && worldFacing.distanceSquared(lastWorldFacing) < FACING_EPSILON_SQ
                && localFacing.distanceSquared(lastLocalFacing) < FACING_EPSILON_SQ;
        if (stable)
            return;

        rebuildConstraint(deployerSubLevel, handleServerSubLevel, hitch, handle, grip, grab, worldGoal);
    }

    private boolean maintainHold(HandleBlockEntity handle) {
        Level level = deployer.getLevel();
        if (level == null)
            return false;

        SubLevelAccess deployerSubLevel = Sable.HELPER.getContaining(deployer);
        SubLevelAccess handleSubLevel = Sable.HELPER.getContaining(handle);
        // Only drop when we know both sides and they're the same. During load,
        // getContaining can be null briefly — that must not clear a saved latch.
        if (deployerSubLevel != null && deployerSubLevel == handleSubLevel)
            return false;

        Vector3d grip = getDeployerGripPoint();
        Vector3d grab = handle.getGrabCenter();
        double distanceSq = Sable.HELPER.distanceSquaredWithSubLevels(level, grip, grab);
        double holdRange = DeployerHoldConfig.holdRange();
        if (distanceSq <= holdRange * holdRange)
            return true;

        // Out of range but still constrained: keep the latch so the joint can
        // pull bodies back after leave/rejoin drift instead of dropping to Open.
        return constraintHandle != null && constraintHandle.isValid();
    }

    private void tryGrabAtExtension() {
        HandleBlockEntity handle = findHandleAhead(deployer);
        if (handle == null) {
            failGrab();
            return;
        }

        SubLevelAccess deployerSubLevel = Sable.HELPER.getContaining(deployer);
        SubLevelAccess handleSubLevel = Sable.HELPER.getContaining(handle);
        if (deployerSubLevel != null && deployerSubLevel == handleSubLevel) {
            failGrab();
            return;
        }

        if (isMovableTargetLocked(handle)) {
            failGrab();
            return;
        }

        heldHandle = handle;
        heldHandlePos = handle.getBlockPos().immutable();
        SubLevelAccess handleContaining = Sable.HELPER.getContaining(handle);
        heldHandleWasInSubLevel = handleContaining instanceof SubLevel;
        heldHandleSubLevelId = handleContaining != null ? handleContaining.getUniqueId() : null;
        holding = true;
        pendingRestore = false;
        physicsPathActive = false;
        updateConstraintFromGameTick();
        deployer.sendData();
        deployer.setChanged();
    }

    private void failGrab() {
        access.deployerhold$setState(stateNamed("RETRACTING"));
        access.deployerhold$setTimer(1000);
        deployer.sendData();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object stateNamed(String name) {
        Object state = access.deployerhold$getState();
        if (state == null)
            throw new IllegalStateException("Deployer state unavailable");
        return Enum.valueOf((Class) state.getClass(), name);
    }

    private @Nullable HandleBlockEntity resolveHeldHandle() {
        if (heldHandle != null && !heldHandle.isRemoved())
            return heldHandle;
        heldHandle = null;
        if (heldHandlePos == null)
            return null;

        HandleBlockEntity found = findHandleAt(deployer.getLevel(), heldHandlePos);
        if (found == null)
            return null;

        // During restore, accept the saved handle even if containing sub-levels
        // are not queryable yet (both null would fail a strict != check).
        if (pendingRestore || isCrossSubLevelTarget(deployer, found)) {
            heldHandle = found;
            return found;
        }
        return null;
    }

    /**
     * True when the saved latch target is loaded and no longer a handle.
     * Returns false while the chunk / sub-level may still be loading.
     */
    private boolean isHeldHandleConfirmedMissing(Level level) {
        if (heldHandlePos == null)
            return true;

        if (heldHandleWasInSubLevel) {
            if (heldHandleSubLevelId == null)
                return false; // legacy NBT: cannot scope which plot is "loaded"
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null)
                return false;
            SubLevel subLevel = container.getSubLevel(heldHandleSubLevelId);
            if (subLevel == null)
                return false; // plot not registered yet
            if (subLevel.isRemoved())
                return true; // assembly torn down
            return findHandleInSubLevel(subLevel, heldHandlePos) == null;
        }

        if (!level.isLoaded(heldHandlePos))
            return false;
        return !(level.getBlockEntity(heldHandlePos) instanceof HandleBlockEntity);
    }

    /**
     * Resolve a specific handle by its block position (plot-local or overworld).
     * One sub-level may contain many handles — position selects which one.
     * Saved sub-level handles skip the overworld lookup so plot-local coords
     * cannot latch onto a coincidental overworld HandleBlockEntity.
     */
    private @Nullable HandleBlockEntity findHandleAt(@Nullable Level level, BlockPos pos) {
        if (level == null)
            return null;

        if (heldHandleWasInSubLevel) {
            if (heldHandleSubLevelId != null) {
                SubLevelContainer container = SubLevelContainer.getContainer(level);
                if (container == null)
                    return null;
                SubLevel subLevel = container.getSubLevel(heldHandleSubLevelId);
                if (subLevel == null || subLevel.isRemoved())
                    return null;
                return findHandleInSubLevel(subLevel, pos);
            }
            // Legacy saves without UUID: scan every loaded plot, never overworld.
            return findHandleInAnySubLevel(level, pos);
        }

        // Overworld handle (when that chunk is loaded).
        if (level.isLoaded(pos)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof HandleBlockEntity handle)
                return handle;
        }

        // Legacy / ambiguous: also scan sub-levels (flag false or missing).
        return findHandleInAnySubLevel(level, pos);
    }

    private static @Nullable HandleBlockEntity findHandleInAnySubLevel(Level level, BlockPos pos) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null)
            return null;
        for (SubLevel subLevel : container.getAllSubLevels()) {
            HandleBlockEntity found = findHandleInSubLevel(subLevel, pos);
            if (found != null)
                return found;
        }
        return null;
    }

    private static @Nullable HandleBlockEntity findHandleInSubLevel(SubLevel subLevel, BlockPos pos) {
        BlockEntity embedded = subLevel.getPlot().getEmbeddedLevelAccessor().getBlockEntity(pos);
        if (embedded instanceof HandleBlockEntity handle)
            return handle;

        for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof HandleBlockEntity handle && handle.getBlockPos().equals(pos))
                return handle;
        }
        return null;
    }

    /**
     * Finds the nearest latchable handle near the deployer tip, including handles
     * that only exist inside other Sable sub-levels (not visible via overworld getBlockEntity).
     */
    private static @Nullable HandleBlockEntity findHandleAhead(DeployerBlockEntity deployer) {
        Level level = deployer.getLevel();
        if (level == null)
            return null;

        BlockState state = deployer.getBlockState();
        if (!state.hasProperty(DirectionalKineticBlock.FACING))
            return null;

        Direction facing = state.getValue(DirectionalKineticBlock.FACING);
        double tipDistance = DeployerHoldConfig.tipDistance();
        double grabTolerance = DeployerHoldConfig.grabTolerance();
        int searchRadius = DeployerHoldConfig.tipSearchRadius();
        Vector3d tipLocal = JOMLConversion.atCenterOf(deployer.getBlockPos())
                .fma(tipDistance, JOMLConversion.atLowerCornerOf(facing.getNormal()));
        Vector3d tipWorld = Sable.HELPER.projectOutOfSubLevel(level, tipLocal, new Vector3d());
        double toleranceSq = grabTolerance * grabTolerance;
        SubLevelAccess deployerSubLevel = Sable.HELPER.getContaining(deployer);

        HandleBlockEntity best = null;
        double bestDistSq = Double.MAX_VALUE;

        // 1) Sable-aware ray along facing (same path players use via clip mixins).
        if (deployer.getPlayer() != null) {
            Vec3 rayStart = new Vec3(tipWorld.x, tipWorld.y, tipWorld.z)
                    .subtract(facing.getStepX() * 0.5, facing.getStepY() * 0.5, facing.getStepZ() * 0.5);
            Vec3 rayEnd = rayStart.add(
                    facing.getStepX() * (tipDistance + grabTolerance),
                    facing.getStepY() * (tipDistance + grabTolerance),
                    facing.getStepZ() * (tipDistance + grabTolerance));
            BlockHitResult hit = level.clip(new ClipContext(
                    rayStart, rayEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, deployer.getPlayer()));
            if (hit.getType() == HitResult.Type.BLOCK) {
                HandleBlockEntity fromRay = resolveHandleAtHit(level, deployerSubLevel, hit);
                if (fromRay != null) {
                    double distSq = Sable.HELPER.distanceSquaredWithSubLevels(level, tipLocal, fromRay.getGrabCenter());
                    if (distSq <= toleranceSq) {
                        best = fromRay;
                        bestDistSq = distSq;
                    }
                }
            }
        }

        // 2) Enumerate handle actors on every sub-level intersecting the tip search volume.
        BoundingBox3d search = new BoundingBox3d(
                tipWorld.x - grabTolerance, tipWorld.y - grabTolerance, tipWorld.z - grabTolerance,
                tipWorld.x + grabTolerance, tipWorld.y + grabTolerance, tipWorld.z + grabTolerance
        );
        for (SubLevelAccess access : Sable.HELPER.getAllIntersecting(level, search)) {
            if (!(access instanceof SubLevel subLevel))
                continue;
            if (subLevel == deployerSubLevel)
                continue;

            for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
                if (!(actor instanceof HandleBlockEntity handle))
                    continue;
                double distSq = Sable.HELPER.distanceSquaredWithSubLevels(level, tipLocal, handle.getGrabCenter());
                if (distSq > toleranceSq || distSq >= bestDistSq)
                    continue;
                bestDistSq = distSq;
                best = handle;
            }
        }

        // 3) Overworld / same-level fallback via world block scan (handles not in a plot).
        if (deployerSubLevel != null) {
            BlockPos tipPos = BlockPos.containing(tipWorld.x, tipWorld.y, tipWorld.z);
            for (BlockPos pos : BlockPos.betweenClosed(
                    tipPos.offset(-searchRadius, -searchRadius, -searchRadius),
                    tipPos.offset(searchRadius, searchRadius, searchRadius))) {
                BlockEntity be = level.getBlockEntity(pos);
                if (!(be instanceof HandleBlockEntity handle))
                    continue;
                if (Sable.HELPER.getContaining(handle) != null)
                    continue; // already covered by actor scan
                double distSq = Sable.HELPER.distanceSquaredWithSubLevels(level, tipLocal, handle.getGrabCenter());
                if (distSq > toleranceSq || distSq >= bestDistSq)
                    continue;
                bestDistSq = distSq;
                best = handle;
            }
        }

        return best;
    }

    private static @Nullable HandleBlockEntity resolveHandleAtHit(
            Level level,
            @Nullable SubLevelAccess deployerSubLevel,
            BlockHitResult hit
    ) {
        BlockPos hitPos = hit.getBlockPos();
        BlockEntity direct = level.getBlockEntity(hitPos);
        if (direct instanceof HandleBlockEntity handle && Sable.HELPER.getContaining(handle) != deployerSubLevel)
            return handle;

        // Hit landed in a sub-level; map the world hit into each intersecting plot.
        Vector3d hitVec = new Vector3d(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
        BoundingBox3d point = new BoundingBox3d(hitVec.x, hitVec.y, hitVec.z, hitVec.x, hitVec.y, hitVec.z).expand(0.1);
        for (SubLevelAccess access : Sable.HELPER.getAllIntersecting(level, point)) {
            if (!(access instanceof SubLevel subLevel) || subLevel == deployerSubLevel)
                continue;
            for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
                if (actor instanceof HandleBlockEntity handle && handle.getBlockPos().equals(hitPos))
                    return handle;
            }
            BlockEntity embedded = subLevel.getPlot().getEmbeddedLevelAccessor().getBlockEntity(hitPos);
            if (embedded instanceof HandleBlockEntity handle)
                return handle;
        }
        return null;
    }

    private static boolean isCrossSubLevelTarget(DeployerBlockEntity deployer, HandleBlockEntity handle) {
        SubLevelAccess deployerSubLevel = Sable.HELPER.getContaining(deployer);
        SubLevelAccess handleSubLevel = Sable.HELPER.getContaining(handle);
        // Both unknown: not enough info to reject (restore path uses pendingRestore).
        if (deployerSubLevel == null && handleSubLevel == null)
            return false;
        return deployerSubLevel != handleSubLevel;
    }

    /**
     * Tip position along facing, animated with expand/retract progress so pull
     * tracks the hand as it draws back. Matches Create's holding-hand offset
     * (face + reach + hand length) minus configurable pull-in to close the visual gap.
     */
    private Vector3d getDeployerGripPoint() {
        Direction facing = deployer.getBlockState().getValue(DirectionalKineticBlock.FACING);
        double tipOffset = Math.max(0.0, DeployerHoldConfig.gripTipOffset(getAnimatedReach()));
        return JOMLConversion.atCenterOf(deployer.getBlockPos())
                .fma(tipOffset, JOMLConversion.atLowerCornerOf(facing.getNormal()));
    }

    private float getAnimatedReach() {
        float base = Math.max(access.deployerhold$getReach(), 0.75f);
        Object state = access.deployerhold$getState();
        String name = state instanceof Enum<?> e ? e.name() : String.valueOf(state);
        float timer = access.deployerhold$getTimer();
        if ("RETRACTING".equals(name))
            return base * Mth.clamp(timer / 1000f, 0f, 1f);
        if ("EXPANDING".equals(name))
            return base * Mth.clamp(1f - timer / 1000f, 0f, 1f);
        if (holding)
            return 0f;
        return base;
    }

    private void rebuildConstraint(
            @Nullable ServerSubLevel deployerSubLevel,
            @Nullable ServerSubLevel handleSubLevel,
            boolean hitch,
            HandleBlockEntity handle,
            Vector3d grip,
            Vector3d grab,
            Vector3d worldGoal
    ) {
        removeConstraint();

        ServerSubLevel constrained = hitch ? deployerSubLevel : handleSubLevel;
        if (constrained == null)
            return;

        ServerSubLevelContainer container = SubLevelContainer.getContainer(constrained.getLevel());
        if (container == null)
            return;

        SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        Vector3d localAnchor = hitch ? grip : grab;

        // Face each other: joint +Z follows world facing and the negated local facing.
        // Pitch/yaw motors match those axes; twist around facing stays free.
        Vector3d worldFacing = hitch ? worldFacingOf(handle) : worldFacingOf(deployer);
        Vector3d localFacing = (hitch ? localFacingOf(deployer) : localFacingOf(handle)).negate();
        Quaterniond worldBasis = basisFromForward(worldFacing);
        Quaterniond localBasis = basisFromForward(localFacing);

        constraintHandle = physicsSystem.getPipeline().addConstraint(
                null,
                constrained,
                new GenericConstraintConfiguration(worldGoal, localAnchor, worldBasis, localBasis)
        );

        if (constraintHandle == null)
            return;

        double maxForce = 120.0;
        if (SimConfigService.INSTANCE.serverLoaded()) {
            SimPhysics physics = SimConfigService.INSTANCE.server().physics;
            maxForce = physics.handleMaxForce.getF();
        }

        double stiffness = DeployerHoldConfig.constraintStiffness();
        double damping = DeployerHoldConfig.constraintDamping();
        double angularStiffness = DeployerHoldConfig.constraintAngularStiffness();
        double angularDamping = DeployerHoldConfig.constraintAngularDamping();
        for (ConstraintJointAxis axis : ConstraintJointAxis.LINEAR) {
            constraintHandle.setMotor(axis, 0.0, stiffness, damping, true, maxForce);
        }
        // Drive pitch/yaw toward facing alignment; leave twist (ANGULAR_Z) free.
        constraintHandle.setMotor(ConstraintJointAxis.ANGULAR_X, 0.0, angularStiffness, angularDamping, true, maxForce);
        constraintHandle.setMotor(ConstraintJointAxis.ANGULAR_Y, 0.0, angularStiffness, angularDamping, true, maxForce);
        constraintHandle.setMotor(ConstraintJointAxis.ANGULAR_Z, 0.0, 0.0, angularDamping, true, maxForce);

        constraintHandle.setContactsEnabled(true);
        lastConstraintHitch = hitch;
        lastConstraintHandlePos = heldHandlePos;
        lastWorldGoal.set(worldGoal);
        lastLocalAnchor.set(localAnchor);
        lastWorldFacing.set(worldFacing);
        lastLocalFacing.set(localFacing);
        hasLastConstraintPose = true;
    }

    /** Block facing in the block entity's local / plot space. */
    private static Vector3d localFacingOf(BlockEntity be) {
        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(DirectionalBlock.FACING)
                ? state.getValue(DirectionalBlock.FACING)
                : Direction.SOUTH;
        return JOMLConversion.atLowerCornerOf(facing.getNormal());
    }

    /** Block facing transformed into world space via the containing sub-level pose. */
    private static Vector3d worldFacingOf(BlockEntity be) {
        Vector3d local = localFacingOf(be);
        SubLevelAccess containing = Sable.HELPER.getContaining(be);
        if (containing instanceof SubLevel subLevel)
            return subLevel.logicalPose().transformNormal(local, new Vector3d());
        return local;
    }

    /**
     * Orthonormal basis with +Z along {@code forward}. If {@code forward} is parallel
     * to the preferred up axis, falls back to another reference so the basis stays a
     * well-defined line frame instead of collapsing.
     */
    private static Quaterniond basisFromForward(Vector3dc forward) {
        Vector3d z = new Vector3d(forward);
        if (z.lengthSquared() < 1.0e-12)
            return new Quaterniond();
        z.normalize();

        Vector3d ref = Math.abs(z.y) > PARALLEL_DOT
                ? new Vector3d(1.0, 0.0, 0.0)
                : new Vector3d(0.0, 1.0, 0.0);
        Vector3d x = new Vector3d();
        ref.cross(z, x);
        if (x.lengthSquared() < 1.0e-12) {
            ref.set(0.0, 0.0, 1.0);
            ref.cross(z, x);
        }
        x.normalize();
        Vector3d y = z.cross(x, new Vector3d()).normalize();
        return new Quaterniond().setFromNormalized(new Matrix3d().set(x, y, z));
    }

    private void removeConstraint() {
        if (constraintHandle != null) {
            if (constraintHandle.isValid())
                constraintHandle.remove();
            constraintHandle = null;
        }
        lastConstraintHandlePos = null;
        hasLastConstraintPose = false;
    }
}
