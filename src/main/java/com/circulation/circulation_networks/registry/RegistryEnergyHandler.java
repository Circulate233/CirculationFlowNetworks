package com.circulation.circulation_networks.registry;

import com.circulation.circulation_networks.api.node.IMachineNode;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.api.IMachineNodeBlockEntity;
import com.circulation.circulation_networks.CFNConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import org.jetbrains.annotations.NotNull;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unused")
public final class RegistryEnergyHandler {


    private static Class<?>[] registeredBlackClassArray;
    private static Class<?>[] registeredSupplyBlackClassArray;
    private static Class<?>[] configuredBlackClassRules;
    private static Class<?>[] configuredSupplyBlackClassRules;
    private static Pair[] managerUnit;
    private static String[] blackPrefixArray;
    private static String[] supplyPrefixArray;
    private static List<IEnergyHandlerManager> list = new ObjectArrayList<>();
    private static IEnergyHandlerManager[] managerArray = new IEnergyHandlerManager[0];
    private static boolean locked;
    private static final Map<Class<?>, Boolean> blackClassCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> supplyBlackClassCache = new ConcurrentHashMap<>();

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
        if (locked) {
            throw new IllegalStateException("Energy handler registry is already locked");
        }
        list.add(manager);
        list.sort(Comparator.reverseOrder());
        referenceSet.add(new Pair(manager.getMultiplying(), manager.getUnit(), manager.getPriority()));
    }

    /**
     * Registers an exact runtime tile entity class to be excluded from automatic energy network integration.
     * Subclasses are not excluded.
     * Node-based tile entities (implementing {@link IMachineNode}) are automatically blacklisted
     * and do not need to be registered here.
     * Must be called before {@link #lock()}.
     *
     * @param clazz the tile entity class to blacklist from energy handling
     */
    public static void registerBlackClass(Class<?> clazz) {
        if (locked) {
            throw new IllegalStateException("Energy handler registry is already locked");
        }
        registeredBlackClasses.add(Objects.requireNonNull(clazz, "clazz"));
    }

    /**
     * Registers an exact runtime tile entity class to be excluded from energy supply operations.
     * Subclasses are not excluded.
     * Must be called before {@link #lock()}.
     *
     * @param clazz the tile entity class to blacklist from energy supply
     */
    public static void registerSupplyBlackClass(Class<?> clazz) {
        if (locked) {
            throw new IllegalStateException("Energy handler registry is already locked");
        }
        registeredSupplyBlackClasses.add(Objects.requireNonNull(clazz, "clazz"));
    }

    public static boolean isBlack(BlockEntity blockEntity) {
        if (blockEntity instanceof IMachineNodeBlockEntity) return true;
        return blackClassCache.computeIfAbsent(blockEntity.getClass(), (Class<?> clazz) ->
            matchesExactClass(clazz, registeredBlackClassArray)
                || matchesConfiguredClassRule(clazz, configuredBlackClassRules, blackPrefixArray));
    }

    public static boolean isSupplyBlack(BlockEntity blockEntity) {
        return supplyBlackClassCache.computeIfAbsent(blockEntity.getClass(), (Class<?> clazz) ->
            matchesExactClass(clazz, registeredSupplyBlackClassArray)
                || matchesConfiguredClassRule(clazz, configuredSupplyBlackClassRules, supplyPrefixArray));
    }

    public static boolean isEnergyItemStack(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (locked) {
            for (IEnergyHandlerManager manager : managerArray) {
                if (manager.isAvailable(stack)) return true;
            }
        } else {
            for (IEnergyHandlerManager manager : list) {
                if (manager.isAvailable(stack)) return true;
            }
        }
        return false;
    }

    public static boolean isEnergyTileEntity(BlockEntity tile) {
        if (locked) {
            for (IEnergyHandlerManager manager : managerArray) {
                if (manager.isAvailable(tile)) return true;
            }
        } else {
            for (IEnergyHandlerManager manager : list) {
                if (manager.isAvailable(tile)) return true;
            }
        }
        return false;
    }

    public static @Nullable IEnergyHandlerManager getEnergyManager(BlockEntity tile) {
        if (locked) {
            return selectEnergyManagerExcluding(tile, managerArray, null);
        }
        return selectEnergyManagerExcluding(tile, list.toArray(new IEnergyHandlerManager[0]), null);
    }

    public static @Nullable IEnergyHandlerManager getEnergyManagerExcluding(
        BlockEntity tile,
        IEnergyHandlerManager excluded) {
        if (locked) {
            return selectEnergyManagerExcluding(tile, managerArray, excluded);
        }
        return selectEnergyManagerExcluding(tile, list.toArray(new IEnergyHandlerManager[0]), excluded);
    }

    static @Nullable IEnergyHandlerManager selectEnergyManagerExcluding(
        BlockEntity tile,
        IEnergyHandlerManager[] managers,
        @Nullable IEnergyHandlerManager excluded) {
        for (IEnergyHandlerManager manager : managers) {
            if (manager != excluded && manager.isAvailable(tile)) {
                return manager;
            }
        }
        return null;
    }

    public static @Nullable IEnergyHandlerManager getEnergyManager(ItemStack stack) {
        if (locked) {
            for (IEnergyHandlerManager manager : managerArray) {
                if (manager.isAvailable(stack)) return manager;
            }
        } else {
            for (IEnergyHandlerManager manager : list) {
                if (manager.isAvailable(stack)) return manager;
            }
        }
        return null;
    }

    public static void lock() {
        if (locked) return;
        list.sort(Comparator.reverseOrder());
        managerArray = list.toArray(new IEnergyHandlerManager[0]);
        list = null;
        locked = true;
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
        final ReferenceSet<Class<?>> configuredBlackClasses = new ReferenceOpenHashSet<>();
        final ReferenceSet<Class<?>> configuredSupplyBlackClasses = new ReferenceOpenHashSet<>();

        collectConfiguredClasses(CFNConfig.classNames, configuredBlackClasses, blackPrefixes);
        collectConfiguredClasses(CFNConfig.supplyClassNames, configuredSupplyBlackClasses, supplyPrefixes);
        blackPrefixArray = blackPrefixes.isEmpty() ? null : blackPrefixes.toArray(new String[0]);
        supplyPrefixArray = supplyPrefixes.isEmpty() ? null : supplyPrefixes.toArray(new String[0]);
        registeredBlackClassArray = registeredBlackClasses.isEmpty()
            ? null : registeredBlackClasses.toArray(new Class<?>[0]);
        registeredSupplyBlackClassArray = registeredSupplyBlackClasses.isEmpty()
            ? null : registeredSupplyBlackClasses.toArray(new Class<?>[0]);
        configuredBlackClassRules = configuredBlackClasses.isEmpty()
            ? null : configuredBlackClasses.toArray(new Class<?>[0]);
        configuredSupplyBlackClassRules = configuredSupplyBlackClasses.isEmpty()
            ? null : configuredSupplyBlackClasses.toArray(new Class<?>[0]);
        blackClassCache.clear();
        supplyBlackClassCache.clear();

        registeredBlackClasses.clear();
        registeredSupplyBlackClasses.clear();

        registeredBlackClasses = null;
        registeredSupplyBlackClasses = null;
    }

    static boolean matchesExactClass(Class<?> clazz, @Nullable Class<?>[] exactClasses) {
        if (exactClasses == null) {
            return false;
        }
        for (Class<?> exactClass : exactClasses) {
            if (exactClass == clazz) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesConfiguredClassRule(Class<?> clazz,
                                              @Nullable Class<?>[] classRules,
                                              @Nullable String[] prefixRules) {
        if (classRules != null) {
            for (Class<?> classRule : classRules) {
                if (classRule == null) continue;
                if (classRule.isAssignableFrom(clazz)) return true;
            }
        }
        if (prefixRules != null) {
            String className = clazz.getName();
            for (String prefix : prefixRules) {
                if (prefix == null) continue;
                if (className.startsWith(prefix)) return true;
            }
        }
        return false;
    }

    private static void collectConfiguredClasses(String[] names,
                                                 ReferenceSet<Class<?>> classSet,
                                                 List<String> prefixes) {
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
