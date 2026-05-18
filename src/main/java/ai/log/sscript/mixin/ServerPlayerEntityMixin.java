package ai.log.sscript.mixin;

import ai.log.sscript.SScript;
import ai.log.sscript.event.MixinManager;
import ai.log.sscript.event.EventManager;
import com.mojang.datafixers.util.Either;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fires 'player_death' event when a player dies.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void sscript$onDeath(net.minecraft.entity.damage.DamageSource source, CallbackInfo ci) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        EventManager manager = EventManager.getInstance();
        if (manager != null) {
            var target = SScript.buildPlayerPayload(self);
            manager.fire("player_death", self.getEntityWorld().getServer(), target, self.getBlockPos().toShortString());
            manager.fire("player_dead", self.getEntityWorld().getServer(), target, self.getBlockPos().toShortString());
        }
    }

    @Inject(method = "copyFrom", at = @At("TAIL"))
    private void sscript$onRespawn(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        EventManager manager = EventManager.getInstance();
        if (manager != null) {
            manager.fire("player_respawn", self.getEntityWorld().getServer(), SScript.buildPlayerPayload(self), alive);
        }
    }

    @Inject(method = "trySleep", at = @At("HEAD"), cancellable = true)
    private void sscript$onTrySleepStart(BlockPos pos,
            CallbackInfoReturnable<Either<PlayerEntity.SleepFailureReason, Unit>> cir) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        MixinManager mixinManager = MixinManager.getInstance();
        if (mixinManager != null && mixinManager.fireCancelable("player_sleep_attempt", self.getEntityWorld().getServer(),
            SScript.buildPlayerPayload(self), pos.toShortString())) {
            cir.setReturnValue(Either.left(new PlayerEntity.SleepFailureReason(net.minecraft.text.Text.empty())));
            cir.cancel();
        }
    }

    @Inject(method = "trySleep", at = @At("RETURN"))
    private void sscript$onTrySleepReturn(BlockPos pos,
            CallbackInfoReturnable<Either<PlayerEntity.SleepFailureReason, Unit>> cir) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        EventManager manager = EventManager.getInstance();
        if (manager != null && cir.getReturnValue() != null && cir.getReturnValue().right().isPresent()) {
            manager.fire("player_sleep", self.getEntityWorld().getServer(),
                    SScript.buildPlayerPayload(self), pos.toShortString());
        }
    }
}
