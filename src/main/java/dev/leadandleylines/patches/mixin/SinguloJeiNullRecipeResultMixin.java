package dev.leadandleylines.patches.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "io.github.genichimaruo.singulo.compat.jei.SinguloJeiPlugin", remap = false)
abstract class SinguloJeiNullRecipeResultMixin {
    @WrapOperation(
        method = "registerRecipes(Lmezz/jei/api/registration/IRecipeRegistration;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/Recipe;getResultItem(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"
        ),
        remap = false
    )
    private ItemStack leylines$emptyIfNullResult(
        Recipe<?> recipe, HolderLookup.Provider registries, Operation<ItemStack> original
    ) {
        ItemStack result = original.call(recipe, registries);
        return result == null ? ItemStack.EMPTY : result;
    }
}
