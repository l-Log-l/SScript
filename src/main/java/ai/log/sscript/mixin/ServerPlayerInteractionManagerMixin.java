package ai.log.sscript.mixin;

import ai.log.sscript.SScript;
import ai.log.sscript.event.MixinManager;
import ai.log.sscript.event.EventManager;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashMap;
import java.util.Map;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionManagerMixin {

    @Shadow
    protected ServerPlayerEntity player;

    @Unique
    private String sscript$breakBlockId;

    @Unique
    private BlockPos sscript$interactClickedPos;

    @Unique
    private BlockPos sscript$interactPlacePos;

    @Unique
    private String sscript$clickedBeforeState;

    @Unique
    private String sscript$placeBeforeState;

    @Inject(method = "tryBreakBlock", at = @At("HEAD"), cancellable = true)
    private void sscript$captureBreakState(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = player.getEntityWorld().getBlockState(pos);
        sscript$breakBlockId = Registries.BLOCK.getId(state.getBlock()).toString();

        MixinManager mixinManager = MixinManager.getInstance();
        if (mixinManager != null) {
            Map<String, Object> block = sscript$buildBlockPayload(player.getEntityWorld(), pos);
            if (mixinManager.fireCancelable("block_break", player.getEntityWorld().getServer(),
                    SScript.buildPlayerPayload(player), block)) {
                cir.setReturnValue(false);
                cir.cancel();
            }
        }
    }

    @Inject(method = "tryBreakBlock", at = @At("RETURN"))
    private void sscript$onBlockBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }

        EventManager manager = EventManager.getInstance();
        if (manager == null) {
            return;
        }

        Map<String, Object> block = new LinkedHashMap<>();
        block.put("id", sscript$breakBlockId != null ? sscript$breakBlockId : "minecraft:air");
        block.put("x", (double) pos.getX());
        block.put("y", (double) pos.getY());
        block.put("z", (double) pos.getZ());
        block.put("pos", pos.toShortString());
        block.put("dimension", player.getEntityWorld().getRegistryKey().getValue().toString());

        manager.fire("block_break", player.getEntityWorld().getServer(), SScript.buildPlayerPayload(player), block);
    }

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void sscript$capturePlaceBefore(ServerPlayerEntity player, World world, ItemStack stack,
            Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        sscript$interactClickedPos = hitResult.getBlockPos().toImmutable();
        sscript$interactPlacePos = hitResult.getBlockPos().offset(hitResult.getSide()).toImmutable();
        sscript$clickedBeforeState = world.getBlockState(sscript$interactClickedPos).toString();
        sscript$placeBeforeState = world.getBlockState(sscript$interactPlacePos).toString();

        MixinManager mixinManager = MixinManager.getInstance();
        if (mixinManager != null) {
            Map<String, Object> clickedBlock = sscript$buildBlockPayload(world, sscript$interactClickedPos);
            Map<String, Object> placeBlock = sscript$buildBlockPayload(world, sscript$interactPlacePos);
            Map<String, Object> item = sscript$buildItemPayload(stack);
            item.put("hand", hand.name());

            if (mixinManager.fireCancelable("block_interact", player.getEntityWorld().getServer(),
                    SScript.buildPlayerPayload(player), clickedBlock, item)) {
                cir.setReturnValue(ActionResult.FAIL);
                cir.cancel();
                return;
            }

            if (mixinManager.fireCancelable("block_place", player.getEntityWorld().getServer(),
                    SScript.buildPlayerPayload(player), placeBlock, item)) {
                cir.setReturnValue(ActionResult.FAIL);
                cir.cancel();
            }
        }
    }

    @Inject(method = "interactBlock", at = @At("RETURN"))
    private void sscript$onBlockPlace(ServerPlayerEntity player, World world, ItemStack stack,
            Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        ActionResult result = cir.getReturnValue();
        if (result == null || !result.isAccepted()) {
            return;
        }

        EventManager manager = EventManager.getInstance();
        if (manager == null) {
            return;
        }

        // Raw interaction event for any successful right-click block action.
        manager.fire("block_interact", player.getEntityWorld().getServer(),
                SScript.buildPlayerPayload(player), sscript$buildBlockPayload(world, sscript$interactClickedPos));

        BlockPos clickedPos = sscript$interactClickedPos != null
                ? sscript$interactClickedPos
                : hitResult.getBlockPos();
        BlockPos placePos = sscript$interactPlacePos != null
                ? sscript$interactPlacePos
                : hitResult.getBlockPos().offset(hitResult.getSide());

        String clickedAfterState = world.getBlockState(clickedPos).toString();
        String placeAfterState = world.getBlockState(placePos).toString();

        boolean clickedChanged = !clickedAfterState.equals(sscript$clickedBeforeState);
        boolean placeChanged = !placeAfterState.equals(sscript$placeBeforeState);

        BlockPos actualPlacedPos = null;
        BlockState actualPlacedState = null;

        if (placeChanged && !world.getBlockState(placePos).isAir()) {
            actualPlacedPos = placePos;
            actualPlacedState = world.getBlockState(placePos);
        } else if (clickedChanged && !world.getBlockState(clickedPos).isAir()) {
            actualPlacedPos = clickedPos;
            actualPlacedState = world.getBlockState(clickedPos);
        }

        if (actualPlacedPos == null || actualPlacedState == null) {
            return;
        }

        Map<String, Object> block = sscript$buildBlockPayload(world, actualPlacedPos);
        block.put("id", Registries.BLOCK.getId(actualPlacedState.getBlock()).toString());

        manager.fire("block_place", player.getEntityWorld().getServer(),
                SScript.buildPlayerPayload(player), block);
    }

    @Unique
    private Map<String, Object> sscript$buildBlockPayload(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("id", Registries.BLOCK.getId(state.getBlock()).toString());
        block.put("x", (double) pos.getX());
        block.put("y", (double) pos.getY());
        block.put("z", (double) pos.getZ());
        block.put("pos", pos.toShortString());
        block.put("dimension", world.getRegistryKey().getValue().toString());
        return block;
    }

    @Unique
    private Map<String, Object> sscript$buildItemPayload(ItemStack stack) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", stack.isEmpty() ? "minecraft:air" : Registries.ITEM.getId(stack.getItem()).toString());
        item.put("count", (double) stack.getCount());
        item.put("name", stack.getName().getString());
        return item;
    }
}
