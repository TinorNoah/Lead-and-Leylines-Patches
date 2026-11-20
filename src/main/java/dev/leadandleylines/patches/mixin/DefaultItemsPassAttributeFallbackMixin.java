package dev.leadandleylines.patches.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.leadandleylines.patches.DefaultItemsPass;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = CommonHooks.class, remap = false)
abstract class DefaultItemsPassAttributeFallbackMixin {
    // That pass runs during the initial datapack reload, before a server, world, or SERVER config exists.
    @WrapMethod(method = "computeModifiedAttributes", remap = false)
    private static ItemAttributeModifiers leylines$baseModifiersWhenListenerFails(
        ItemStack stack,
        ItemAttributeModifiers defaultModifiers,
        Operation<ItemAttributeModifiers> original
    ) {
        if (!DefaultItemsPass.active()) return original.call(stack, defaultModifiers);
        try {
            return original.call(stack, defaultModifiers);
        } catch (RuntimeException error) {
            DefaultItemsPass.warnBaseModifiers(stack, error);
            return defaultModifiers;
        }
    }
}
