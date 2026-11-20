package dev.leadandleylines.patches;

import com.bawnorton.mixinsquared.api.MixinCanceller;
import java.util.List;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;

/** Drops Applied Industrialization's MI Jade fluids mixin, whose {@code addFluids} target MI no longer has. */
public final class AppliedIndustrializationJadeCanceller implements MixinCanceller {
    private static final String FLUIDS_MIXIN = "aeind.mixin.MachineComponentFluidsProviderMixin";
    private static final String MOD_ID = "aeind";
    private static final String VERSION = "1.4.4";

    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        if (!mixinClassName.equals(FLUIDS_MIXIN)) return false;
        ModFileInfo file = LoadingModList.get().getModFileById(MOD_ID);
        return file != null && file.getMods().stream()
            .anyMatch(mod -> mod.getModId().equals(MOD_ID) && mod.getVersion().toString().equals(VERSION));
    }
}
