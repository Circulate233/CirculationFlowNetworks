package com.circulation.circulation_networks.utils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

public final class ResourceIdCompat {

    private ResourceIdCompat() {
    }

    public static String getBlockId(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return id != null ? id.toString() : "";
    }
}
