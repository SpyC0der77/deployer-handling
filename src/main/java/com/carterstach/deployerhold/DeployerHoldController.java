package com.carterstach.deployerhold;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Runtime grip-session state for a Deployer in Grip: Pull / Grip: Hitch.
 * <p>
 * Lifecycle: extend → latch handle → retract while constrained → stay latched
 * until redstone powers the deployer (ungrip).
 * <ul>
 *   <li>Pull: handle's sub-level is pulled toward the moving tip</li>
 *   <li>Hitch: deployer's sub-level is pulled toward the handle</li>
 * </ul>
 */
public class DeployerHoldController {
    /** Skip rebuild when the world-space goal hasn't moved (~1 mm). */
    private static final double GOAL_EPSILON_SQ = 1.0e-6;

    private final DeployerBlockEntity deployer;
    private final DeployerHoldAccess access;

    private @Nullable HandleBlockEntity heldHandle;
    private @Nullable BlockPos heldHandlePos;
    private boolean holding;
    private @Nullable PhysicsConstraintHandle constraintHandle;
    /** True once Sable has called this Deployer's physics tick for the active grip. */
    private boolean physicsPathActive;
    private boolean lastConstraintHitch;
    private @Nullable BlockPos lastConstraintHandlePos;
    private final Vector3d lastWorldGoal = new Vector3d();
    private boolean hasLastWorldGoal;

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
        removeConstraint();
        holding = false;
        heldHandle = null;
        heldHandlePos = null;
        physicsPathActive = false;
        lastConstraintHandlePos = null;
        hasLastWorldGoal = false;
    }

    public void release() {
        clear();
    }

    public void onModeChanged() {
        clear();
    }

    public void write(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("DeployerHoldHolding", holding);
        if (heldHandlePos != null)
            tag.put("DeployerHoldHandle", NbtUtils.writeBlockPos(heldHandlePos));
    }

    public void read(CompoundTag tag, HolderLookup.Provider registries) {
        holding = tag.getBoolean("DeployerHoldHolding");
        heldHandlePos = tag.contains("DeployerHoldHandle")
                ? NbtUtils.readBlockPos(tag, "DeployerHoldHandle").orElse(null)
                : null;
        heldHandle = null;
        removeConstraint();
        physicsPathActive = false;
        // Sub-level handles can't be restored from a bare BlockPos; re-latch on next extend.
        if (holding && resolveHeldHandle() == null)
            clear();
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

        if (!maintainHold()) {
            release();
            access.deployerhold$setState(stateNamed("WAITING"));
            access.deployerhold$setTimer(500);
            deployer.sendData();
            deployer.setChanged();
            return;
        }

        if (!physicsPathActive)
            updateConstraintFromGameTick();
    }

    public void physicsTick(ServerSubLevel deployerSubLevel) {
        if (!holding)
            return;
        physicsPathActive = true;
        updateConstraint(deployerSubLevel);
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
            release();
            return;
        }

        SubLevelAccess handleAccess = Sable.HELPER.getContaining(handle);
        if (!(handleAccess instanceof ServerSubLevel handleServerSubLevel)) {
            release();
            return;
        }

        Vector3d grip = getDeployerGripPoint();
        Vector3d grab = handle.getGrabCenter();
        Vector3d worldGoal = Sable.HELPER.projectOutOfSubLevel(level, grip, new Vector3d());
        rebuildConstraint(null, handleServerSubLevel, false, grip, grab, worldGoal);
    }

    private void updateConstraint(ServerSubLevel deployerSubLevel) {
        if (!holding)
            return;

        Level level = deployer.getLevel();
        if (level == null || level.isClientSide)
            return;

        HandleBlockEntity handle = resolveHeldHandle();
        if (handle == null) {
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
            rebuildConstraint(deployerSubLevel, null, true, grip, grab, worldGoal);
            return;
        }

        if (handleServerSubLevel == deployerSubLevel) {
            removeConstraint();
            return;
        }

        boolean hitch = DeployerHoldModes.isHitch(access.deployerhold$getMode());
        Vector3d grip = getDeployerGripPoint();
        Vector3d grab = handle.getGrabCenter();
        Vector3d worldGoal = hitch
                ? Sable.HELPER.projectOutOfSubLevel(level, grab, new Vector3d())
                : Sable.HELPER.projectOutOfSubLevel(level, grip, new Vector3d());

        boolean stable = constraintHandle != null
                && constraintHandle.isValid()
                && hitch == lastConstraintHitch
                && heldHandlePos != null
                && heldHandlePos.equals(lastConstraintHandlePos)
                && hasLastWorldGoal
                && worldGoal.distanceSquared(lastWorldGoal) < GOAL_EPSILON_SQ;
        if (stable)
            return;

        rebuildConstraint(deployerSubLevel, handleServerSubLevel, hitch, grip, grab, worldGoal);
    }

    private boolean maintainHold() {
        HandleBlockEntity handle = resolveHeldHandle();
        if (handle == null)
            return false;

        Level level = deployer.getLevel();
        if (level == null)
            return false;

        SubLevelAccess deployerSubLevel = Sable.HELPER.getContaining(deployer);
        SubLevelAccess handleSubLevel = Sable.HELPER.getContaining(handle);
        if (deployerSubLevel == handleSubLevel)
            return false;

        Vector3d grip = getDeployerGripPoint();
        Vector3d grab = handle.getGrabCenter();
        double distanceSq = Sable.HELPER.distanceSquaredWithSubLevels(level, grip, grab);
        double holdRange = DeployerHoldConfig.holdRange();
        return distanceSq <= holdRange * holdRange;
    }

    private void tryGrabAtExtension() {
        HandleBlockEntity handle = findHandleAhead(deployer);
        if (handle == null) {
            failGrab();
            return;
        }

        SubLevelAccess deployerSubLevel = Sable.HELPER.getContaining(deployer);
        SubLevelAccess handleSubLevel = Sable.HELPER.getContaining(handle);
        if (deployerSubLevel == handleSubLevel) {
            failGrab();
            return;
        }

        if (isMovableTargetLocked(handle)) {
            failGrab();
            return;
        }

        heldHandle = handle;
        heldHandlePos = handle.getBlockPos().immutable();
        holding = true;
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

        Level level = deployer.getLevel();
        if (level == null)
            return null;

        BlockEntity be = level.getBlockEntity(heldHandlePos);
        if (be instanceof HandleBlockEntity handle && isCrossSubLevelTarget(deployer, handle)) {
            heldHandle = handle;
            return handle;
        }

        // Sub-level handle: rediscover near the tip.
        HandleBlockEntity found = findHandleAhead(deployer);
        if (found != null && found.getBlockPos().equals(heldHandlePos)) {
            heldHandle = found;
            return found;
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
        return Sable.HELPER.getContaining(deployer) != Sable.HELPER.getContaining(handle);
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

        constraintHandle = physicsSystem.getPipeline().addConstraint(
                null,
                constrained,
                new FreeConstraintConfiguration(worldGoal, localAnchor, new Quaterniond())
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
        double angularDamping = DeployerHoldConfig.constraintAngularDamping();
        for (ConstraintJointAxis axis : ConstraintJointAxis.LINEAR) {
            constraintHandle.setMotor(axis, 0.0, stiffness, damping, true, maxForce);
        }
        for (ConstraintJointAxis axis : ConstraintJointAxis.ANGULAR) {
            constraintHandle.setMotor(axis, 0.0, 0.0, angularDamping, true, maxForce);
        }

        constraintHandle.setContactsEnabled(true);
        lastConstraintHitch = hitch;
        lastConstraintHandlePos = heldHandlePos;
        lastWorldGoal.set(worldGoal);
        hasLastWorldGoal = true;
    }

    private void removeConstraint() {
        if (constraintHandle != null) {
            if (constraintHandle.isValid())
                constraintHandle.remove();
            constraintHandle = null;
        }
        lastConstraintHandlePos = null;
        hasLastWorldGoal = false;
    }
}
