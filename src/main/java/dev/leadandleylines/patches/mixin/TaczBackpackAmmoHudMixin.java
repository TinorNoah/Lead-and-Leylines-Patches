package dev.leadandleylines.patches.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

// Empty on purpose: LeylinesMixinPlugin.preApply rewrites GunHudOverlay when this mixin applies.
@Pseudo
@Mixin(targets = "com.tacz.guns.client.gui.overlay.GunHudOverlay", remap = false)
abstract class TaczBackpackAmmoHudMixin {}
