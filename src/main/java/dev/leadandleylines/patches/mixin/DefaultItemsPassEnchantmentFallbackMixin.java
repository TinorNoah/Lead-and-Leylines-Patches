package dev.leadandleylines.patches.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.leadandleylines.patches.DefaultItemsPass;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = EventHooks.class, remap = false)
abstract class DefaultItemsPassEnchantmentFallbackMixin {
    // On a client joining a server, that pass runs before Apothic Enchanting's enchantment data has synced.
    @WrapMethod(method = "getAllEnchantmentLevels", remap = false)
    private static ItemEnchantments leylines$baseEnchantmentsWhenListenerFails(
        ItemEnchantments enchantments,
        ItemStack stack,
        HolderLookup.RegistryLookup<Enchantment> lookup,
        Operation<ItemEnchantments> original
    ) {
        if (!DefaultItemsPass.active()) return original.call(enchantments, stack, lookup);
        try {
            return original.call(enchantments, stack, lookup);
        } catch (RuntimeException error) {
            DefaultItemsPass.warnBaseEnchantments(stack, error);
            return enchantments;
        }
    }
}
