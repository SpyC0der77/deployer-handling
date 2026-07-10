package com.simibubi.create.content.kinetics.deployer;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity.State;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.config.server.physics.SimPhysics;
import dev.simulated_team.simulated.content.blocks.handle.HandleBlock;
import dev.simulated_team.simulated.content.blocks.handle.HandleBlockEntity;
import dev.simulated_team.simulated.index.SimTags;
import dev.simulated_team.simulated.service.SimConfigService;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Runtime grip-session state for a Deployer in Grip: Pull / Grip: Hitch.
 * Links the Deployer's sub-level to a Simulated handle on another sub-level
 * using a Sable free constraint (same approach as player handle grabs).
 */
public class DeployerHoldController {
    /** Matches Simulated's HandleBlockEntity.MAX_HANDLE_RANGE */
    private static final double BOUNDING = 5.0;
    private static final double CONSTRAINT_DAMPING = 30.0;
    private static final double CONSTRAINT_STIFFNESS = 240.0;

    private final DeployerBlockEntity deployer;

    private @Nullable BlockPos heldHandlePos;
    private boolean holding;
    private @Nullable PhysicsConstraintHandle constraintHandle;
    /** True once Sable has called this Deployer's physics tick for the active grip. */
    private boolean physicsPathActive;
    private boolean lastConstraintHitch;
    private @Nullable BlockPos lastConstraintHandlePos;

    public DeployerHoldController(DeployerBlockEntity deployer) {
        this.deployer = deployer;
    }

    public boolean isHolding() {
        return holding;
    }

    public @Nullable BlockPos getHeldHandlePos() {
        return heldHandlePos;
    }

    public void clear() {
        removeConstraint();
        holding = false;
        heldHandlePos = null;
        physicsPathActive = false;
        lastConstraintHandlePos = null;
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
        removeConstraint();
        physicsPathActive = false;
    }

    /**
     * Called when the deployer arm finishes extending in a grip mode.
     */
    public void onArmFullyExtended() {
        if (holding) {
            sustainHold();
            return;
        }
        tryGrabAtExtension();
    }

    /**
     * Called every tick while a grip is active (skips Create's expand/retract cycle).
     */
    public void sustainHold() {
        if (!holding)
            return;

        if (!maintainHold()) {
            release();
            deployer.state = State.WAITING;
            deployer.timer = 500;
            deployer.sendData();
            deployer.setChanged();
            return;
        }

        keepArmExtended();
        // Game-tick fallback only until Sable's physics callback owns the joint.
        if (!physicsPathActive)
            updateConstraintFromGameTick();
    }

    public void physicsTick(ServerSubLevel deployerSubLevel) {
        physicsPathActive = true;
        updateConstraint(deployerSubLevel, true);
    }

    private void updateConstraintFromGameTick() {
        Level level = deployer.getLevel();
        if (level == null || level.isClientSide)
            return;

        SubLevel deployerSubLevel = Sable.HELPER.getContaining(deployer);
        if (deployerSubLevel instanceof ServerSubLevel serverDeployerSubLevel)
            updateConstraint(serverDeployerSubLevel, false);
    }

    private void updateConstraint(ServerSubLevel deployerSubLevel, boolean physicsTick) {
        if (!holding || heldHandlePos == null)
            return;

        Level level = deployer.getLevel();
        if (level == null || level.isClientSide)
            return;

        HandleBlockEntity handle = getHandle(heldHandlePos);
        if (handle == null) {
            release();
            return;
        }

        SubLevel handleSubLevel = Sable.HELPER.getContaining(handle);
        if (!(handleSubLevel instanceof ServerSubLevel handleServerSubLevel)) {
            release();
            return;
        }

        if (handleServerSubLevel == deployerSubLevel) {
            removeConstraint();
            return;
        }

        boolean hitch = DeployerHoldModes.isHitch(deployer.mode);
        boolean endpointsUnchanged = constraintHandle != null
                && constraintHandle.isValid()
                && hitch == lastConstraintHitch
                && heldHandlePos.equals(lastConstraintHandlePos);

        // Physics ticks must refresh world-space goals each step (matching Simulated
        // player grabs). Game-tick fallback skips rebuild when the joint is already set.
        if (!physicsTick && endpointsUnchanged)
            return;

        rebuildConstraint(deployerSubLevel, handleServerSubLevel, handle, hitch);
    }

    public static boolean hasHandleTarget(DeployerBlockEntity deployer) {
        return findHandleAhead(deployer) != null;
    }

    private boolean maintainHold() {
        if (heldHandlePos == null)
            return false;

        HandleBlockEntity handle = getHandle(heldHandlePos);
        if (handle == null)
            return false;

        Level level = deployer.getLevel();
        if (level == null)
            return false;

        SubLevel deployerSubLevel = Sable.HELPER.getContaining(deployer);
        SubLevel handleSubLevel = Sable.HELPER.getContaining(handle);
        if (deployerSubLevel == null || handleSubLevel == null)
            return false;
        if (deployerSubLevel == handleSubLevel)
            return false;

        Vector3d grip = getDeployerGripPoint();
        Vector3d grab = handle.getGrabCenter();
        double distanceSq = Sable.HELPER.distanceSquaredWithSubLevels(level, grip, grab);
        return distanceSq <= BOUNDING * BOUNDING;
    }

    private void tryGrabAtExtension() {
        BlockPos target = findHandleAhead(deployer);
        if (target == null) {
            failGrab();
            return;
        }

        HandleBlockEntity handle = getHandle(target);
        if (handle == null) {
            failGrab();
            return;
        }

        SubLevel deployerSubLevel = Sable.HELPER.getContaining(deployer);
        SubLevel handleSubLevel = Sable.HELPER.getContaining(handle);
        if (deployerSubLevel == null || handleSubLevel == null || deployerSubLevel == handleSubLevel) {
            failGrab();
            return;
        }

        heldHandlePos = target.immutable();
        holding = true;
        keepArmExtended();
        deployer.sendData();
        deployer.setChanged();
    }

    private void failGrab() {
        deployer.state = State.RETRACTING;
        deployer.timer = 1000;
        deployer.sendData();
    }

    private void keepArmExtended() {
        deployer.state = State.EXPANDING;
        deployer.timer = 0;
        deployer.reach = Math.max(deployer.reach, 0.75f);
    }

    private static @Nullable BlockPos findHandleAhead(DeployerBlockEntity deployer) {
        Level level = deployer.getLevel();
        if (level == null)
            return null;

        BlockState state = deployer.getBlockState();
        if (!state.hasProperty(DirectionalKineticBlock.FACING))
            return null;

        Direction facing = state.getValue(DirectionalKineticBlock.FACING);
        for (int dist = 2; dist <= 3; dist++) {
            BlockPos pos = deployer.getBlockPos().relative(facing, dist);
            if (isHoldableHandle(level, pos) && isCrossSubLevelTarget(deployer, pos))
                return pos;
        }

        Vec3 origin = VecHelper.getCenterOf(deployer.getBlockPos())
                .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5));
        Vec3 tip = origin.add(Vec3.atLowerCornerOf(facing.getNormal()).scale(2.5));
        BlockPos tipPos = BlockPos.containing(tip);
        for (BlockPos pos : BlockPos.betweenClosed(tipPos.offset(-1, -1, -1), tipPos.offset(1, 1, 1))) {
            if (isHoldableHandle(level, pos) && isCrossSubLevelTarget(deployer, pos))
                return pos.immutable();
        }
        return null;
    }

    private static boolean isHoldableHandle(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(SimTags.Blocks.HANDLES) && !(state.getBlock() instanceof HandleBlock))
            return false;
        return level.getBlockEntity(pos) instanceof HandleBlockEntity;
    }

    private static boolean isCrossSubLevelTarget(DeployerBlockEntity deployer, BlockPos handlePos) {
        Level level = deployer.getLevel();
        if (level == null)
            return false;
        BlockEntity be = level.getBlockEntity(handlePos);
        if (!(be instanceof HandleBlockEntity handle))
            return false;
        SubLevel deployerSubLevel = Sable.HELPER.getContaining(deployer);
        SubLevel handleSubLevel = Sable.HELPER.getContaining(handle);
        return deployerSubLevel != null && handleSubLevel != null && deployerSubLevel != handleSubLevel;
    }

    private @Nullable HandleBlockEntity getHandle(BlockPos pos) {
        Level level = deployer.getLevel();
        if (level == null)
            return null;
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof HandleBlockEntity handle ? handle : null;
    }

    private Vector3d getDeployerGripPoint() {
        Direction facing = deployer.getBlockState().getValue(DirectionalKineticBlock.FACING);
        float handReach = Math.max(deployer.reach, 0.75f);
        return JOMLConversion.atCenterOf(deployer.getBlockPos())
                .fma(1.0 + handReach, JOMLConversion.atLowerCornerOf(facing.getNormal()));
    }

    private void rebuildConstraint(
            ServerSubLevel deployerSubLevel,
            ServerSubLevel handleSubLevel,
            HandleBlockEntity handle,
            boolean hitch
    ) {
        removeConstraint();

        Level level = deployer.getLevel();
        if (level == null)
            return;

        ServerSubLevelContainer container = SubLevelContainer.getContainer(deployerSubLevel.getLevel());
        if (container == null)
            return;

        SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        Vector3d grip = getDeployerGripPoint();
        Vector3d grab = handle.getGrabCenter();

        // Player shift-grab: world goal at the holder, constrained body = handle sub-level.
        // Player no-shift: entity moves to the handle — for Deployers, constrain the Deployer's
        // sub-level toward the handle grab (projected into world space).
        if (hitch) {
            Vector3d grabWorld = Sable.HELPER.projectOutOfSubLevel(level, grab, new Vector3d());
            constraintHandle = physicsSystem.getPipeline().addConstraint(
                    null,
                    deployerSubLevel,
                    new FreeConstraintConfiguration(grabWorld, grip, new Quaterniond())
            );
        } else {
            Vector3d gripWorld = Sable.HELPER.projectOutOfSubLevel(level, grip, new Vector3d());
            constraintHandle = physicsSystem.getPipeline().addConstraint(
                    null,
                    handleSubLevel,
                    new FreeConstraintConfiguration(gripWorld, grab, new Quaterniond())
            );
        }

        if (constraintHandle == null)
            return;

        double maxForce = 120.0;
        if (SimConfigService.INSTANCE.serverLoaded()) {
            SimPhysics physics = SimConfigService.INSTANCE.server().physics;
            maxForce = physics.handleMaxForce.getF();
        }

        for (ConstraintJointAxis axis : ConstraintJointAxis.LINEAR) {
            constraintHandle.setMotor(axis, 0.0, CONSTRAINT_STIFFNESS, CONSTRAINT_DAMPING, true, maxForce);
        }
        for (ConstraintJointAxis axis : ConstraintJointAxis.ANGULAR) {
            constraintHandle.setMotor(axis, 0.0, 0.0, 4.5, true, maxForce);
        }

        constraintHandle.setContactsEnabled(true);
        lastConstraintHitch = hitch;
        lastConstraintHandlePos = heldHandlePos;
    }

    private void removeConstraint() {
        if (constraintHandle != null) {
            if (constraintHandle.isValid())
                constraintHandle.remove();
            constraintHandle = null;
        }
        lastConstraintHandlePos = null;
    }
}
