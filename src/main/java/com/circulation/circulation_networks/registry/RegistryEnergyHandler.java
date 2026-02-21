package com.circulation.circulation_networks.registry;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.api.IMachineNodeTileEntity;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;

public final class RegistryEnergyHandler {

    public static List<String> blackList = new ObjectArrayList<>();
    public static List<Class<?>> blackListClass = new ObjectArrayList<>();
    private static List<IEnergyHandlerManager> list = new ObjectArrayList<>();

    /**
     * 只允许在postinit阶段前进行注册
     */
    public static void registryEnergyHandler(IEnergyHandlerManager manager) {
        list.add(manager);
        IEnergyHandler.POOL.put(manager.getEnergyHandlerClass(), new ArrayDeque<>());
    }

    public static boolean isBlack(TileEntity tileEntity) {
        if (tileEntity instanceof IMachineNodeTileEntity) return true;
        for (Class<?> listClass : blackListClass) {
            if (listClass.isInstance(tileEntity)) return true;
        }
        var className = tileEntity.getClass().getName();
        for (String s : blackList) {
            if (className.startsWith(s)) return true;
        }
        return false;
    }

    public static boolean isEnergyItemStack(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (IEnergyHandlerManager manager : list) {
            if (manager.isAvailable(stack)) return true;
        }
        return false;
    }

    public static boolean isEnergyTileEntity(TileEntity tile) {
        for (IEnergyHandlerManager manager : list) {
            if (manager.isAvailable(tile)) return true;
        }
        return false;
    }

    public static IEnergyHandlerManager getEnergyManager(TileEntity tile) {
        for (IEnergyHandlerManager manager : list) {
            if (manager.isAvailable(tile)) return manager;
        }
        return null;
    }

    public static IEnergyHandlerManager getEnergyManager(ItemStack stack) {
        for (IEnergyHandlerManager manager : list) {
            if (manager.isAvailable(stack)) return manager;
        }
        return null;
    }

    public static void lock() {
        list.sort(Comparator.reverseOrder());
        list = ImmutableList.copyOf(list);
        blackList = ImmutableList.copyOf(blackList);
        blackListClass = ImmutableList.copyOf(blackListClass);
    }

}