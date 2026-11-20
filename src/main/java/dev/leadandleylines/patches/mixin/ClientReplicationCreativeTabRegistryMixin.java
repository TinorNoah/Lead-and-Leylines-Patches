package dev.leadandleylines.patches.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.RegistryAccess;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.buuz135.replication.Replication", remap = false)
abstract class ClientReplicationCreativeTabRegistryMixin {
    // Replication's creative tab listener; a configuration-phase tab rebuild runs before the client has a level.
    @WrapOperation(
        method = "lambda$initModules$16",
        at = @At(
            value = "INVOKE",
            target = "Lcom/buuz135/replication/ReplicationRegistry;registryAccess()Lnet/minecraft/core/RegistryAccess;"
        ),
        remap = false
    )
    private static RegistryAccess leylines$useTabBuildRegistriesWhenNoLevel(
        Operation<RegistryAccess> original,
        @Local(argsOnly = true) BuildCreativeModeTabContentsEvent event
    ) {
        try {
            return original.call();
        } catch (RuntimeException error) {
            if (event.getParameters().holders() instanceof RegistryAccess registries) return registries;
            throw error;
        }
    }
}
