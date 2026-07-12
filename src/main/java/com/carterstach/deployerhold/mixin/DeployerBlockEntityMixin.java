package com.carterstach.deployerhold.mixin;

import com.carterstach.deployerhold.DeployerFields;
import com.carterstach.deployerhold.DeployerHoldAccess;
import com.carterstach.deployerhold.DeployerHoldController;
import com.carterstach.deployerhold.DeployerHoldModes;
import com.carterstach.deployerhold.LatchStatus;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = DeployerBlockEntity.class, remap = false)
public abstract class DeployerBlockEntityMixin extends KineticBlockEntity
        implements DeployerHoldAccess, BlockEntitySubLevelActor {

    @Shadow(remap = false)
    protected int timer;

    @Shadow(remap = false)
    protected float reach;

    @Shadow(remap = false)
    protected boolean redstoneLocked;

    @Shadow(remap = false)
    protected ItemStack heldItem;

    @Unique
    private DeployerHoldController deployerhold$controller;

    public DeployerBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Unique
    private DeployerBlockEntity deployerhold$self() {
        return (DeployerBlockEntity) (Object) this;
    }

    @Unique
    private DeployerHoldController deployerhold$controller() {
        if (deployerhold$controller == null)
            deployerhold$controller = new DeployerHoldController(deployerhold$self());
        return deployerhold$controller;
    }

    @Override
    public DeployerHoldController deployerhold$getHoldController() {
        return deployerhold$controller();
    }

    @Override
    public Object deployerhold$getMode() {
        return DeployerFields.getMode(deployerhold$self());
    }

    @Override
    public void deployerhold$setMode(Object mode) {
        DeployerFields.setMode(deployerhold$self(), mode);
    }

    @Override
    public Object deployerhold$getState() {
        return DeployerFields.getState(deployerhold$self());
    }

    @Override
    public void deployerhold$setState(Object state) {
        DeployerFields.setState(deployerhold$self(), state);
    }

    @Override
    public int deployerhold$getTimer() {
        return timer;
    }

    @Override
    public void deployerhold$setTimer(int timer) {
        this.timer = timer;
    }

    @Override
    public float deployerhold$getReach() {
        return reach;
    }

    @Override
    public void deployerhold$setReach(float reach) {
        this.reach = reach;
    }

    @Override
    public boolean deployerhold$isRedstoneLocked() {
        return redstoneLocked;
    }

    @Override
    public ItemStack deployerhold$getHeldItem() {
        return heldItem;
    }

    @Override
    public int deployerhold$getTimerSpeed() {
        return DeployerFields.getTimerSpeed(deployerhold$self());
    }

    @Inject(method = "changeMode", at = @At("HEAD"), cancellable = true, remap = false)
    private void deployerhold$cycleModes(CallbackInfo ci) {
        DeployerBlockEntity self = deployerhold$self();
        DeployerFields.setMode(self, DeployerHoldModes.next(DeployerFields.getMode(self)));
        deployerhold$controller().onModeChanged();
        setChanged();
        self.sendData();
        ci.cancel();
    }

    @Inject(method = "activate", at = @At("HEAD"), cancellable = true, remap = false)
    private void deployerhold$onActivate(CallbackInfo ci) {
        if (!DeployerHoldModes.isGrip(DeployerFields.getMode(deployerhold$self())))
            return;

        deployerhold$controller().onArmFullyExtended();
        ci.cancel();
    }

    /**
     * While latched: sync the constraint, and park retracted by cancelling the rest of
     * Create's tick so it cannot call {@code start()} again.
     * Redstone ungrips and restores a normal WAITING arm so the next unpowered tick can extend.
     */
    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;tick()V",
                    shift = At.Shift.AFTER,
                    remap = false
            ),
            cancellable = true,
            remap = false
    )
    private void deployerhold$tickWhileHolding(CallbackInfo ci) {
        DeployerBlockEntity self = deployerhold$self();
        if (!DeployerHoldModes.isGrip(DeployerFields.getMode(self)))
            return;
        if (!deployerhold$controller().isHolding())
            return;

        if (redstoneLocked) {
            deployerhold$resetArmAfterRelease(self);
            ci.cancel();
            return;
        }

        deployerhold$controller().tickHold();
        // Still latched — keep Create from starting another expand cycle.
        if (!deployerhold$controller().isHolding())
            return;

        Object state = DeployerFields.getState(self);
        String name = state instanceof Enum<?> e ? e.name() : String.valueOf(state);
        // Let expand→activate and retract finish so pull tracks the tip drawing back.
        if ("EXPANDING".equals(name) || "RETRACTING".equals(name))
            return;

        DeployerFields.setState(self, DeployerFields.stateNamed(self, "WAITING"));
        int speed = DeployerFields.getTimerSpeed(self);
        timer = Math.max(speed, 1) * 10;
        ci.cancel();
    }

    @Unique
    private void deployerhold$resetArmAfterRelease(DeployerBlockEntity self) {
        deployerhold$controller().release();
        DeployerFields.setState(self, DeployerFields.stateNamed(self, "WAITING"));
        // Ready to extend as soon as redstone unlocks and Create's tick runs again.
        timer = 0;
        self.setChanged();
        self.sendData();
    }

    @OnlyIn(Dist.CLIENT)
    @Inject(method = "getHandPose", at = @At("HEAD"), cancellable = true, remap = false)
    private void deployerhold$holdHandPose(CallbackInfoReturnable<PartialModel> cir) {
        if (DeployerHoldModes.isGrip(DeployerFields.getMode(deployerhold$self())))
            cir.setReturnValue(AllPartialModels.DEPLOYER_HAND_HOLDING);
    }

    @Inject(method = "addToGoggleTooltip", at = @At("HEAD"), cancellable = true, remap = false)
    private void deployerhold$holdGoggleTooltip(List<?> tooltip, boolean isPlayerSneaking, CallbackInfoReturnable<Boolean> cir) {
        String modeKey = DeployerHoldModes.tooltipKey(DeployerFields.getMode(deployerhold$self()));
        if (modeKey == null)
            return;

        @SuppressWarnings("unchecked")
        List<Component> components = (List<Component>) tooltip;

        CreateLang.translate("tooltip.deployer.header").forGoggles(components);
        components.add(Component.literal("    ").append(
                Component.translatable(modeKey).withStyle(ChatFormatting.YELLOW)
        ));

        LatchStatus latch = deployerhold$controller().latchStatus();
        ChatFormatting latchColor = switch (latch) {
            case LATCHED -> ChatFormatting.GREEN;
            case READY -> ChatFormatting.AQUA;
            case TARGET_LOCKED -> ChatFormatting.RED;
            case OPEN -> ChatFormatting.GRAY;
        };
        components.add(Component.literal("    ").append(
                Component.translatable(latch.langKey()).withStyle(latchColor)
        ));

        if (!heldItem.isEmpty()) {
            CreateLang.translate(
                    "tooltip.deployer.contains",
                    Component.translatable(heldItem.getDescriptionId()).getString(),
                    heldItem.getCount()
            ).style(ChatFormatting.GREEN).forGoggles(components);
        }

        cir.setReturnValue(true);
    }

    @Inject(method = "write", at = @At("RETURN"), remap = false)
    private void deployerhold$writeHold(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        deployerhold$controller().write(compound, registries);
    }

    @Inject(method = "writeSafe", at = @At("RETURN"), remap = false)
    private void deployerhold$writeHoldSafe(CompoundTag compound, HolderLookup.Provider registries, CallbackInfo ci) {
        // Schematics / some plot paths use writeSafe (Mode only) — still persist the latch.
        deployerhold$controller().write(compound, registries);
    }

    @Inject(method = "read", at = @At("HEAD"), remap = false)
    private void deployerhold$migrateLegacyHoldMode(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        // Older builds saved Mode as "HOLD"; map that to Grip: Pull.
        if ("HOLD".equals(compound.getString("Mode")))
            compound.putString("Mode", "HOLD_PULL");
    }

    /**
     * Must use RETURN (every exit), not TAIL. Create's disk load path
     * ({@code clientPacket == false}) returns early after {@code super.read},
     * and TAIL only hooks the final client-packet return — so latch NBT was
     * written on save but never applied on world load (goggles stayed Open).
     */
    @Inject(method = "read", at = @At("RETURN"), remap = false)
    private void deployerhold$readHold(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        deployerhold$controller().read(compound, registries);
    }

    @Inject(method = "invalidate", at = @At("HEAD"))
    private void deployerhold$invalidate(CallbackInfo ci) {
        // Only detach the live joint — do not clear holding/handle pos, or chunk
        // unload would wipe the latch from the next save and break leave/rejoin.
        if (deployerhold$controller != null)
            deployerhold$controller.detachConstraint();
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (!DeployerHoldModes.isGrip(DeployerFields.getMode(deployerhold$self())))
            return;
        deployerhold$controller().physicsTick(subLevel);
    }
}
