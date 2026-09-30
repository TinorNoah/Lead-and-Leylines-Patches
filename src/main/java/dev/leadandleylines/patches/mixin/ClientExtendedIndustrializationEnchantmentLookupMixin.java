package dev.leadandleylines.patches.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(
    targets = {
        "net.swedz.extended_industrialization.item.ElectricToolItem",
        "net.swedz.extended_industrialization.item.SteamChainsawItem"
    },
    remap = false
)
abstract class ClientExtendedIndustrializationEnchantmentLookupMixin {
    @ModifyVariable(
        method = "getAllEnchantments",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        remap = false
    )
    private HolderLookup.RegistryLookup<Enchantment> leylines$useClientLookupWhenMissing(
        HolderLookup.RegistryLookup<Enchantment> lookup
    ) {
        if (lookup != null) return lookup;
        Minecraft client = Minecraft.getInstance();
        return client.level == null
            ? null
            : client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
    }
}
