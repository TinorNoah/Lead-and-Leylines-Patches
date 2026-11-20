package dev.leadandleylines.patches;

import com.bawnorton.mixinsquared.canceller.MixinCancellerRegistrar;
import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class LeylinesMixinPlugin implements IMixinConfigPlugin {
    private static final String BACKPACK_AMMO_HUD_MIXIN =
        "dev.leadandleylines.patches.mixin.TaczBackpackAmmoHudMixin";
    private static final String SINGULO_JEI_MIXIN =
        "dev.leadandleylines.patches.mixin.SinguloJeiNullRecipeResultMixin";
    private static final String GUN_HUD = "com/tacz/guns/client/gui/overlay/GunHudOverlay";
    // Military Backpack 2.1.0 injects at RETURN of this pre-1.2.0 TaCZ method, which 1.2.0 removed.
    private static final String INVENTORY_AMMO_NAME = "handleInventoryAmmo";
    private static final String INVENTORY_AMMO_DESC =
        "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Inventory;)V";
    private static final String CACHE_COUNT_DESC =
        "(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/item/ItemStack;"
            + "Lcom/tacz/guns/resource/pojo/data/gun/GunData;Lcom/tacz/guns/api/item/IGun;)V";

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        LoadingModList mods = LoadingModList.get();
        return switch (mixinClassName) {
            case BACKPACK_AMMO_HUD_MIXIN ->
                mods.getModFileById("tacz") != null && mods.getModFileById("militarybackpack") != null;
            case SINGULO_JEI_MIXIN -> mods.getModFileById("singulo") != null;
            default -> true;
        };
    }

    // Runs before any mixin is merged or injected, so Military Backpack's injector finds the restored method.
    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (!mixinClassName.equals(BACKPACK_AMMO_HUD_MIXIN)) return;
        if (findMethod(targetClass, INVENTORY_AMMO_NAME, INVENTORY_AMMO_DESC) != null) return;

        MethodNode stub = new MethodNode(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, INVENTORY_AMMO_NAME, INVENTORY_AMMO_DESC, null, null
        );
        stub.instructions.add(new InsnNode(Opcodes.RETURN));
        stub.maxLocals = 2;
        targetClass.methods.add(stub);

        // handleCacheCount refreshes the cached count only every 50 ms, so add backpack ammo right after that write.
        MethodNode cacheCount = findMethod(targetClass, "handleCacheCount", CACHE_COUNT_DESC);
        if (cacheCount == null) return;
        for (AbstractInsnNode insn : cacheCount.instructions) {
            if (insn instanceof FieldInsnNode field
                && field.getOpcode() == Opcodes.PUTSTATIC
                && field.owner.equals(GUN_HUD)
                && field.name.equals("cacheInventoryAmmoCount")) {
                InsnList call = new InsnList();
                call.add(new VarInsnNode(Opcodes.ALOAD, 1));
                call.add(new VarInsnNode(Opcodes.ALOAD, 0));
                call.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    "net/minecraft/world/entity/player/Player",
                    "getInventory",
                    "()Lnet/minecraft/world/entity/player/Inventory;",
                    false
                ));
                call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GUN_HUD, INVENTORY_AMMO_NAME, INVENTORY_AMMO_DESC, false));
                cacheCount.instructions.insert(field, call);
                cacheCount.maxStack = Math.max(cacheCount.maxStack, 2);
                return;
            }
        }
    }

    private static MethodNode findMethod(ClassNode owner, String name, String desc) {
        for (MethodNode method : owner.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) return method;
        }
        return null;
    }

    @Override
    public void onLoad(String mixinPackage) {
        MixinCancellerRegistrar.register(new AppliedIndustrializationJadeCanceller());
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
