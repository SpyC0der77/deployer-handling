package com.carterstach.deployerhold.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * In-game tutorial for Grip: Pull / Grip: Hitch on Create Deployers.
 * Physics / Sable constraints are illustrated with section motion, not live constraints.
 */
public final class DeployerHoldScenes {
    private DeployerHoldScenes() {}

    public static void grip(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("deployer_grip", "Gripping Simulated Handles");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(3, 1, 3, 3, 1, 5), Direction.DOWN);
        scene.idle(10);

        BlockPos deployerPos = util.grid().at(3, 1, 2);
        BlockPos handlePos = util.grid().at(1, 1, 2);
        Selection deployerSelection = util.select().position(deployerPos);
        Vec3 frontVec = util.vector().blockSurface(deployerPos, Direction.WEST).add(-.125, 0, 0);

        scene.world().showSection(deployerSelection, Direction.DOWN);
        scene.idle(10);

        BlockState handleState = BuiltInRegistries.BLOCK
                .get(ResourceLocation.fromNamespaceAndPath("simulated", "iron_handle"))
                .defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.EAST);
        scene.world().setBlock(handlePos, handleState, false);

        ElementLink<WorldSectionElement> handlePlatform =
                scene.world().showIndependentSection(util.select().position(handlePos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(70)
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().centerOf(handlePos))
                .text("Deployer Hold adds grip modes that latch onto Simulated handles across Sable sub-levels");
        scene.idle(80);

        scene.overlay().showControls(frontVec, Pointing.LEFT, 40).rightClick()
                .withItem(AllItems.WRENCH.asStack());
        scene.idle(7);
        scene.world().modifyBlockEntityNBT(deployerSelection, DeployerBlockEntity.class,
                nbt -> nbt.putString("Mode", "HOLD_PULL"));
        scene.idle(20);

        scene.overlay().showText(70)
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(deployerPos))
                .text("Wrench the Deployer's front face to cycle into Grip: Pull, then Grip: Hitch");
        scene.idle(80);

        scene.world().setKineticSpeed(util.select().position(2, 0, 5), 16);
        scene.world().setKineticSpeed(util.select().layer(1), -32);

        scene.overlay().showText(60)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .pointAt(frontVec)
                .text("When powered, the arm extends, latches the handle, then retracts while the grip stays active");
        scene.world().moveDeployer(deployerPos, 1, 25);
        scene.idle(26);
        scene.effects().indicateSuccess(handlePos);
        scene.world().moveDeployer(deployerPos, -1, 25);
        scene.idle(40);

        scene.overlay().showOutlineWithText(util.select().position(handlePos), 70)
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .pointAt(util.vector().centerOf(handlePos))
                .text("Grip: Pull — the handle's sub-level (rider) is dragged toward the Deployer (anchor)");
        scene.world().moveSection(handlePlatform, util.vector().of(1.25, 0, 0), 30);
        scene.idle(45);
        scene.world().moveSection(handlePlatform, util.vector().of(-1.25, 0, 0), 20);
        scene.idle(35);

        scene.overlay().showControls(frontVec, Pointing.LEFT, 35).rightClick()
                .withItem(AllItems.WRENCH.asStack());
        scene.idle(7);
        scene.world().modifyBlockEntityNBT(deployerSelection, DeployerBlockEntity.class,
                nbt -> nbt.putString("Mode", "HOLD_HITCH"));
        scene.idle(20);

        ElementLink<WorldSectionElement> deployerPlatform =
                scene.world().makeSectionIndependent(deployerSelection);

        scene.overlay().showOutlineWithText(deployerSelection, 70)
                .attachKeyFrame()
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .pointAt(util.vector().topOf(deployerPos))
                .text("Grip: Hitch — the Deployer's sub-level rides toward the handle instead");
        scene.world().moveSection(deployerPlatform, util.vector().of(-1.25, 0, 0), 30);
        scene.idle(45);
        scene.world().moveSection(deployerPlatform, util.vector().of(1.25, 0, 0), 20);
        scene.idle(35);

        scene.overlay().showControls(util.vector().topOf(deployerPos), Pointing.DOWN, 40)
                .rightClick()
                .withItem(new ItemStack(net.minecraft.world.item.Items.REDSTONE_TORCH));
        scene.idle(7);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(deployerPos))
                .text("Redstone-lock the Deployer to release the latch. Engineer's Goggles show mode and latch status");
        scene.idle(90);

        scene.markAsFinished();
    }
}
