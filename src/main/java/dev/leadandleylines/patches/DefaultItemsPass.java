package dev.leadandleylines.patches;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/** Tracks Epic Fight's {@code addDefaultItems} pass, where BK's Armaments reads every item's attribute modifiers. */
public final class DefaultItemsPass {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private DefaultItemsPass() {}

    public static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void exit() {
        DEPTH.set(DEPTH.get() - 1);
    }

    public static boolean active() {
        return DEPTH.get() > 0;
    }

    public static void warnBaseModifiers(ItemStack stack, RuntimeException error) {
        LOGGER.warn(
            "Attribute event failed for {} during Epic Fight default item assignment; using its base modifiers: {}",
            BuiltInRegistries.ITEM.getKey(stack.getItem()),
            error.toString()
        );
    }
}
