package com.circulation.circulation_networks.registry;

import com.circulation.circulation_networks.CFNConfig;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.api.IMachineNodeBlockEntity;
import com.circulation.circulation_networks.api.node.IMachineNode;
import com.circulation.circulation_networks.utils.ObjectPool;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

@SuppressWarnings("unused")
public final class RegistryEnergyHandler {


    private static Class<?>[] blackListClass;
    private static Class<?>[] supplyBlackListClass;
    private static Pair[] managerUnit;
    private static String[] blackPrefixArray;
    private static String[] supplyPrefixArray;
    private static List<IEnergyHandlerManager> list = new ObjectArrayList<>();

    private static ReferenceSet<Class<?>> registeredBlackClasses = new ReferenceOpenHashSet<>();
    private static ReferenceSet<Class<?>> registeredSupplyBlackClasses = new ReferenceOpenHashSet<>();
    private static ReferenceSet<Pair> referenceSet = new ReferenceOpenHashSet<>();

    public static Pair getPair(int o) {
        return managerUnit[Math.floorMod(o, managerUnit.length)];
    }

    /**
     * Registers an energy handler manager. Must be called before {@link #lock()}.
     */
    public static void registerEnergyHandler(IEnergyHandlerManager manager) {
        list.add(manager);
        IEnergyHandler.POOL.put(manager.getEnergyHandlerClass(), new ObjectPool<>(manager::newBlockEntityInstance, IEnergyHandler::clear, 4096));
        referenceSet.add(new Pair(manager.getMultiplying(), manager.getUnit(), manager.getPriority()));
    }

    /**
     * Registers a tile entity class to be excluded from automatic energy network integration.
     * Node-based tile entities (implementing {@link IMachineNode}) are automatically blacklisted
     * and do not need to be registered here.
     * Must be called before {@link #lock()}.
     *
     * @param clazz the tile entity class to blacklist from energy handling
     */
    public static void registerBlackClass(Class<?> clazz) {
        registeredBlackClasses.add(clazz);
    }

    /**
     * Registers a tile entity class to be excluded from energy supply operations.
     * Must be called before {@link #lock()}.
     *
     * @param clazz the tile entity class to blacklist from energy supply
     */
    public static void registerSupplyBlackClass(Class<?> clazz) {
        registeredSupplyBlackClasses.add(clazz);
    }

    public static boolean isBlack(BlockEntity blockEntity) {
        if (blockEntity instanceof IMachineNodeBlockEntity) return true;
        if (blackListClass != null) {
            for (Class<?> listClass : blackListClass) {
                if (listClass.isInstance(blockEntity)) return true;
            }
        }
        if (blackPrefixArray != null) {
            String className = blockEntity.getClass().getName();
            for (String prefix : blackPrefixArray) {
                if (className.startsWith(prefix)) return true;
            }
        }
        return false;
    }

    public static boolean isSupplyBlack(BlockEntity blockEntity) {
        if (supplyBlackListClass != null) {
            for (Class<?> listClass : supplyBlackListClass) {
                if (listClass.isInstance(blockEntity)) return true;
            }
        }
        if (supplyPrefixArray != null) {
            String className = blockEntity.getClass().getName();
            for (String prefix : supplyPrefixArray) {
                if (className.startsWith(prefix)) return true;
            }
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

    public static boolean isEnergyTileEntity(BlockEntity tile) {
        for (IEnergyHandlerManager manager : list) {
            if (manager.isAvailable(tile)) return true;
        }
        return false;
    }

    public static @Nullable IEnergyHandlerManager getEnergyManager(BlockEntity tile) {
        for (IEnergyHandlerManager manager : list) {
            if (manager.isAvailable(tile)) return manager;
        }
        return null;
    }

    public static @Nullable IEnergyHandlerManager getEnergyManager(BlockEntity tile, @Nullable IEnergyHandlerManager preferred) {
        if (preferred != null && preferred.isAvailable(tile)) {
            return preferred;
        }
        return getEnergyManager(tile);
    }

    public static @Nullable IEnergyHandlerManager getEnergyManager(ItemStack stack) {
        for (IEnergyHandlerManager manager : list) {
            if (manager.isAvailable(stack)) return manager;
        }
        return null;
    }

    public static void lock() {
        list.sort(Comparator.reverseOrder());
        list = ImmutableList.copyOf(list);
        var rl = new ObjectArrayList<>(referenceSet);
        referenceSet.clear();
        referenceSet = null;
        rl.sort(Comparator.reverseOrder());
        String defaultUnit = CFNConfig.defaultEnergyUnitDisplay;
        if (defaultUnit != null && !defaultUnit.isEmpty() && rl.size() > 1) {
            for (int i = 0; i < rl.size(); i++) {
                if (defaultUnit.equals(rl.get(i).unit())) {
                    if (i > 0) {
                        Pair matched = rl.remove(i);
                        rl.add(0, matched);
                    }
                    break;
                }
            }
        }
        managerUnit = rl.isEmpty() ? new Pair[]{new Pair(1, "FE", 0)} : rl.toArray(new Pair[0]);

        final List<String> blackPrefixes = new ObjectArrayList<>();
        final List<String> supplyPrefixes = new ObjectArrayList<>();
        final ReferenceSet<Class<?>> blackSet = registeredBlackClasses;
        final ReferenceSet<Class<?>> supplySet = registeredSupplyBlackClasses;

        collectExactClasses(CFNConfig.classNames, blackSet, blackPrefixes);
        collectExactClasses(CFNConfig.supplyClassNames, supplySet, supplyPrefixes);
        blackPrefixArray = blackPrefixes.isEmpty() ? null : blackPrefixes.toArray(new String[0]);
        supplyPrefixArray = supplyPrefixes.isEmpty() ? null : supplyPrefixes.toArray(new String[0]);

        blackListClass = blackSet.isEmpty() ? null : blackSet.toArray(new Class[0]);
        supplyBlackListClass = supplySet.isEmpty() ? null : supplySet.toArray(new Class[0]);

        registeredBlackClasses.clear();
        registeredSupplyBlackClasses.clear();

        registeredBlackClasses = null;
        registeredSupplyBlackClasses = null;
    }

    private static void collectExactClasses(String[] names, ReferenceSet<Class<?>> classSet, List<String> prefixes) {
        if (names == null) return;
        for (String className : names) {
            if (className == null || className.trim().isEmpty()) continue;
            className = className.trim();
            try {
                classSet.add(Class.forName(className));
            } catch (ClassNotFoundException e) {
                prefixes.add(className);
            }
        }
    }

    public record Pair(double multiplying, String unit, int p) implements Comparable<Pair> {

        @Override
        public int compareTo(@NotNull RegistryEnergyHandler.Pair o) {
            return Integer.compare(p, o.p);
        }
    }

}
