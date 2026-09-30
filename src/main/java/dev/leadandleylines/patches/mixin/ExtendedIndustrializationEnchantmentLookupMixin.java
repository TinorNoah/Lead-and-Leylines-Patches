package dev.leadandleylines.patches.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
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
abstract class ExtendedIndustrializationEnchantmentLookupMixin {
    @ModifyVariable(
        method = "getAllEnchantments",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        remap = false
    )
    private HolderLookup.RegistryLookup<Enchantment> leylines$useServerLookupWhenMissing(
        HolderLookup.RegistryLookup<Enchantment> lookup
    ) {
        if (lookup != null) return lookup;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
    }
}
