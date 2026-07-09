package com.simibubi.create.content.kinetics.deployer;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DeployerBlockEntity.class)
public abstract class DeployerBlockEntityMixin extends KineticBlockEntity
        implements DeployerHoldAccess, BlockEntitySubLevelActor {

    @Unique
    private DeployerHoldController deployerhold$controller;

    public DeployerBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Unique
    private DeployerHoldController deployerhold$controller() {
        if (deployerhold$controller == null)
            deployerhold$controller = new DeployerHoldController((DeployerBlockEntity) (Object) this);
        return deployerhold$controller;
    }

    @Override
    public DeployerHoldController deployerhold$getHoldController() {
        return deployerhold$controller();
    }

    @Inject(method = "changeMode", at = @At("HEAD"), cancellable = true, remap = false)
    private void deployerhold$cycleThreeModes(CallbackInfo ci) {
        DeployerBlockEntity self = (DeployerBlockEntity) (Object) this;
        self.mode = DeployerHoldModes.next(self.mode);
        deployerhold$controller().onModeChanged();
        self.setChanged();
        self.sendData();
        ci.cancel();
    }

    @Inject(method = "start", at = @At("HEAD"), cancellable = true, remap = false)
    private void deployerhold$startOnlyWithHandle(CallbackInfo ci) {
        DeployerBlockEntity self = (DeployerBlockEntity) (Object) this;
        if (!DeployerHoldModes.isHold(self.mode))
            return;

        if (deployerhold$controller().getHeldHandlePos() == null
                && !DeployerHoldController.hasHandleTarget(self)) {
            self.timer = self.getTimerSpeed() * 10;
            ci.cancel();
        }
    }

    @Inject(method = "activate", at = @At("HEAD"), cancellable = true, remap = false)
    private void deployerhold$onActivate(CallbackInfo ci) {
        DeployerBlockEntity self = (DeployerBlockEntity) (Object) this;
        if (!DeployerHoldModes.isHold(self.mode))
            return;

        deployerhold$controller().onArmFullyExtended();
        ci.cancel();
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/kinetics/deployer/DeployerBlockEntity;activate()V",
                    shift = At.Shift.AFTER,
                    remap = false
            ),
            remap = false
    )
    private void deployerhold$keepExtendedWhileHolding(CallbackInfo ci) {
        DeployerBlockEntity self = (DeployerBlockEntity) (Object) this;
        if (!DeployerHoldModes.isHold(self.mode))
            return;
        if (!deployerhold$controller().isHolding())
            return;

        self.state = DeployerBlockEntity.State.EXPANDING;
        self.timer = 0;
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;tick()V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void deployerhold$tickWhileHolding(CallbackInfo ci) {
        DeployerBlockEntity self = (DeployerBlockEntity) (Object) this;
        if (!DeployerHoldModes.isHold(self.mode))
            return;
        if (!deployerhold$controller().isHolding())
            return;

        if (self.getSpeed() == 0 || self.redstoneLocked) {
            deployerhold$controller().release();
            self.state = DeployerBlockEntity.State.WAITING;
            self.timer = 500;
            self.sendData();
            ci.cancel();
            return;
        }

        deployerhold$controller().sustainHold();
        ci.cancel();
    }

    @OnlyIn(Dist.CLIENT)
    @Inject(method = "getHandPose", at = @At("HEAD"), cancellable = true, remap = false)
    private void deployerhold$holdHandPose(CallbackInfoReturnable<PartialModel> cir) {
        DeployerBlockEntity self = (DeployerBlockEntity) (Object) this;
        if (DeployerHoldModes.isHold(self.mode))
            cir.setReturnValue(AllPartialModels.DEPLOYER_HAND_HOLDING);
    }

    @Inject(method = "addToGoggleTooltip", at = @At("HEAD"), cancellable = true, remap = false)
    private void deployerhold$holdGoggleTooltip(List<?> tooltip, boolean isPlayerSneaking, CallbackInfoReturnable<Boolean> cir) {
        DeployerBlockEntity self = (DeployerBlockEntity) (Object) this;
        if (!DeployerHoldModes.isHold(self.mode))
            return;

        @SuppressWarnings("unchecked")
        List<Component> components = (List<Component>) tooltip;

        CreateLang.translate("tooltip.deployer.header").forGoggles(components);
        components.add(Component.literal("    ").append(
                Component.translatable("deployerhold.tooltip.deployer.holding").withStyle(ChatFormatting.YELLOW)
        ));

        if (deployerhold$controller().isHolding()) {
            components.add(Component.literal("    ").append(
                    Component.translatable("deployerhold.tooltip.deployer.holding_handle").withStyle(ChatFormatting.GREEN)
            ));
        }

        if (!self.heldItem.isEmpty()) {
            CreateLang.translate(
                    "tooltip.deployer.contains",
                    Component.translatable(self.heldItem.getDescriptionId()).getString(),
                    self.heldItem.getCount()
            ).style(ChatFormatting.GREEN).forGoggles(components);
        }

        cir.setReturnValue(true);
    }

    @Inject(method = "write", at = @At("TAIL"), remap = false)
    private void deployerhold$writeHold(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        deployerhold$controller().write(compound, registries);
    }

    @Inject(method = "read", at = @At("TAIL"), remap = false)
    private void deployerhold$readHold(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        deployerhold$controller().read(compound, registries);
    }

    @Inject(method = "invalidate", at = @At("HEAD"))
    private void deployerhold$invalidate(CallbackInfo ci) {
        if (deployerhold$controller != null)
            deployerhold$controller.clear();
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        DeployerBlockEntity self = (DeployerBlockEntity) (Object) this;
        if (!DeployerHoldModes.isHold(self.mode))
            return;
        deployerhold$controller().physicsTick(subLevel);
    }
}
