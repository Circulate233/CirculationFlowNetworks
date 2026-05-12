package com.circulation.circulation_networks.mixins;

import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.Collections;
import java.util.List;

@SuppressWarnings("unused")
public class LateMixinLoader implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.circulation_networks.de.json");
    }

    @Override
    public boolean shouldMixinConfigQueue(String mixinConfig) {
        return "mixins.circulation_networks.de.json".equals(mixinConfig);
    }

}
