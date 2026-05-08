package com.circulation.circulation_networks.registry;

import com.circulation.circulation_networks.api.node.IMachineNode;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.api.IMachineNodeBlockEntity;
import com.circulation.circulation_networks.CFNConfig;
//~ mc_imports
//? if <1.20
import com.github.bsideup.jabel.Desugar;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import org.jetbrains.annotations.Nullable;

import org.jetbrains.annotations.NotNull;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unused")
public final class RegistryEnergyHandler {

    //~ if >=1.20 '(TileEntity ' -> '(BlockEntity ' {
    //~ if >=1.20 ' TileEntity ' -> ' BlockEntity ' {
    //~ if >=1.20 '<TileEntity>' -> '<BlockEntity>' {

    private static Class<?>[] blackListClass;
    private static Class<?>[] supplyBlackListClass;
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

    public static boolean isBlack(TileEntity blockEntity) {
        if (blockEntity instanceof IMachineNodeBlockEntity) return true;
        return blackClassCache.computeIfAbsent(blockEntity.getClass(), clazz -> matchesClassRule(clazz, blackListClass, blackPrefixArray));
    }

    public static boolean isSupplyBlack(TileEntity blockEntity) {
        return supplyBlackClassCache.computeIfAbsent(blockEntity.getClass(), clazz -> matchesClassRule(clazz, supplyBlackListClass, supplyPrefixArray));
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

    public static boolean isEnergyTileEntity(TileEntity tile) {
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

    public static @Nullable IEnergyHandlerManager getEnergyManager(TileEntity tile) {
        if (locked) {
            for (IEnergyHandlerManager manager : managerArray) {
                if (manager.isAvailable(tile)) return manager;
            }
        } else {
            for (IEnergyHandlerManager manager : list) {
                if (manager.isAvailable(tile)) return manager;
            }
        }
        return null;
    }

    public static @Nullable IEnergyHandlerManager getEnergyManager(TileEntity tile, @Nullable IEnergyHandlerManager preferred) {
        if (preferred != null && preferred.isAvailable(tile)) {
            return preferred;
        }
        return getEnergyManager(tile);
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
        final ReferenceSet<Class<?>> blackSet = registeredBlackClasses;
        final ReferenceSet<Class<?>> supplySet = registeredSupplyBlackClasses;

        collectExactClasses(CFNConfig.classNames, blackSet, blackPrefixes);
        collectExactClasses(CFNConfig.supplyClassNames, supplySet, supplyPrefixes);
        //? if <1.20 {
        if (!blackPrefixes.isEmpty() || !supplyPrefixes.isEmpty()) {
            for (var aClass : TileEntity.REGISTRY) {
                var className = aClass.getName();
                if (!blackPrefixes.isEmpty() && !blackSet.contains(aClass)) {
                    for (String prefix : blackPrefixes) {
                        if (className.startsWith(prefix)) {
                            blackSet.add(aClass);
                            break;
                        }
                    }
                }
                if (!supplyPrefixes.isEmpty() && !supplySet.contains(aClass)) {
                    for (String prefix : supplyPrefixes) {
                        if (className.startsWith(prefix)) {
                            supplySet.add(aClass);
                            break;
                        }
                    }
                }
            }
        }
        //?}
        blackPrefixArray = blackPrefixes.isEmpty() ? null : blackPrefixes.toArray(new String[0]);
        supplyPrefixArray = supplyPrefixes.isEmpty() ? null : supplyPrefixes.toArray(new String[0]);
        blackListClass = blackSet.isEmpty() ? null : blackSet.toArray(new Class[0]);
        supplyBlackListClass = supplySet.isEmpty() ? null : supplySet.toArray(new Class[0]);
        blackClassCache.clear();
        supplyBlackClassCache.clear();

        registeredBlackClasses.clear();
        registeredSupplyBlackClasses.clear();

        registeredBlackClasses = null;
        registeredSupplyBlackClasses = null;
    }

    private static boolean matchesClassRule(Class<?> clazz, @Nullable Class<?>[] classRules, @Nullable String[] prefixRules) {
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

    //? if <1.20
    @Desugar
    public record Pair(double multiplying, String unit, int p) implements Comparable<Pair> {

        @Override
        public int compareTo(@NotNull RegistryEnergyHandler.Pair o) {
            return Integer.compare(p, o.p);
        }
    }

    //~}
    //~}
    //~}
}
