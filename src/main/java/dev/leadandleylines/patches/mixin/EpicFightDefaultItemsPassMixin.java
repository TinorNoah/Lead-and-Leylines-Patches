package dev.leadandleylines.patches.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.leadandleylines.patches.DefaultItemsPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "yesman.epicfight.world.capabilities.provider.CommonItemCapabilityProvider", remap = false)
abstract class EpicFightDefaultItemsPassMixin {
    @WrapMethod(method = "addDefaultItems", remap = false)
    private void leylines$markDefaultItemsPass(Operation<Void> original) {
        DefaultItemsPass.enter();
        try {
            original.call();
        } finally {
            DefaultItemsPass.exit();
        }
    }
}
