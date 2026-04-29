package com.circulation.circulation_networks.utils;

//~ mc_imports
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

public final class SideCompat {

    private SideCompat() {
    }

    //~ if >=1.20 'World ' -> 'Level ' {
    //~ if >=1.20 '.isRemote' -> '.isClientSide' {
    public static boolean isClientWorld(@NotNull World world) {
        return world.isRemote;
    }

    public static boolean isServerWorld(@NotNull World world) {
        return !isClientWorld(world);
    }
    //~}
    //~}

}
