package com.circulation.circulation_networks.utils;

//~ mc_imports
import net.minecraft.world.World;
//? if <1.20 {
import net.minecraftforge.fml.common.FMLCommonHandler;
//?} else if <1.21 {
/*import net.minecraftforge.fml.loading.FMLEnvironment;
*///?} else {
/*import net.neoforged.fml.loading.FMLEnvironment;
*///?}
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

    //? if <1.20 {
    public static boolean isServer() {
        return FMLCommonHandler.instance().getSide().isServer();
    }
    //?} else {
    /*public static boolean isServer() {
        return FMLEnvironment.dist.isDedicatedServer();
    }
    *///?}

}
