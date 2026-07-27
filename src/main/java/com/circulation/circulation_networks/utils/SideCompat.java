package com.circulation.circulation_networks.utils;

import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public final class SideCompat {

    private SideCompat() {
    }

    public static boolean isClientWorld(@NotNull Level world) {
        return world.isClientSide();
    }

    public static boolean isServerWorld(@NotNull Level world) {
        return !isClientWorld(world);
    }

}
