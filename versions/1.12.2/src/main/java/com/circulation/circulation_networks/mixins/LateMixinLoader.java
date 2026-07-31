package com.circulation.circulation_networks.mixins;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraftforge.fml.common.Loader;
import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

@SuppressWarnings("unused")
public class LateMixinLoader implements ILateMixinLoader {

    private static final Map<String, BooleanSupplier> MIXIN_CONFIGS = new Object2ObjectLinkedOpenHashMap<>();

    static {
        addModdedMixinCFG("mixins.circulation_networks.de.json", "draconicevolution");
        addMixinCFG("mixins.circulation_networks.ae.json", () -> modLoaded("appliedenergistics2") && !modLoaded("ae2"));
    }

    private static boolean modLoaded(final String modID) {
        return Loader.isModLoaded(modID);
    }

    private static void addModdedMixinCFG(final String mixinConfig, final String modID) {
        MIXIN_CONFIGS.put(mixinConfig, () -> modLoaded(modID));
    }

    private static void addModdedMixinCFG(final String mixinConfig, final String modID, final String... modIDs) {
        MIXIN_CONFIGS.put(mixinConfig, () -> modLoaded(modID) && Arrays.stream(modIDs).allMatch(Loader::isModLoaded));
    }

    private static void addMixinCFG(final String mixinConfig) {
        MIXIN_CONFIGS.put(mixinConfig, () -> true);
    }

    private static void addMixinCFG(final String mixinConfig, final BooleanSupplier conditions) {
        MIXIN_CONFIGS.put(mixinConfig, conditions);
    }

    @Override
    public List<String> getMixinConfigs() {
        return new ArrayList<>(MIXIN_CONFIGS.keySet());
    }

    @Override
    public boolean shouldMixinConfigQueue(final String mixinConfig) {
        BooleanSupplier supplier = MIXIN_CONFIGS.get(mixinConfig);
        if (supplier == null) {
            return false;
        }
        return supplier.getAsBoolean();
    }

}
