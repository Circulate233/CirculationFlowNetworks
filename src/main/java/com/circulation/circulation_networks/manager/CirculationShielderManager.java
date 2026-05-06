package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.ICirculationShielderBlockEntity;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
//~ mc_imports
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class CirculationShielderManager {

    public static final CirculationShielderManager INSTANCE = new CirculationShielderManager();

    private final Int2ObjectMap<ReferenceSet<ICirculationShielderBlockEntity>> dimShielders = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<ICirculationShielderBlockEntity[]> idimShielders = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<ICirculationShielderBlockEntity[]> activeDimShielders = new Int2ObjectOpenHashMap<>();
    private static final ICirculationShielderBlockEntity[] EMPTY = new ICirculationShielderBlockEntity[0];

    public CirculationShielderManager() {
        dimShielders.defaultReturnValue(ReferenceSets.emptySet());
        idimShielders.defaultReturnValue(EMPTY);
        activeDimShielders.defaultReturnValue(EMPTY);
    }

    //~ if >=1.20 '(World ' -> '(Level ' {
    //~ if >=1.20 '.provider.getDimension()' -> '.dimension().location().hashCode()' {
    private static int getDimensionId(World world) {
        return world.provider.getDimension();
    }

    public Int2ObjectMap<ICirculationShielderBlockEntity[]> getDimShielders() {
        return idimShielders;
    }

    public ICirculationShielderBlockEntity[] getShieldersForDim(int dimId) {
        return idimShielders.get(dimId);
    }

    public ICirculationShielderBlockEntity[] getActiveShieldersForDim(int dimId) {
        return activeDimShielders.get(dimId);
    }

    public void register(ICirculationShielderBlockEntity shielder, int dimId) {
        if (shielder == null) return;

        ReferenceSet<ICirculationShielderBlockEntity> set = dimShielders.get(dimId);
        if (set == dimShielders.defaultReturnValue()) {
            dimShielders.put(dimId, set = new ReferenceOpenHashSet<>());
        }
        set.add(shielder);
        refreshDimCache(dimId, set);
    }

    public void unregister(ICirculationShielderBlockEntity shielder, int dimId) {
        if (shielder == null) return;

        var shielders = dimShielders.get(dimId);
        if (shielders == null) return;
        shielders.remove(shielder);
        if (shielders.isEmpty()) dimShielders.remove(dimId);
        refreshDimCache(dimId, shielders);
    }

    public void refreshActiveState(ICirculationShielderBlockEntity shielder, int dimId) {
        if (shielder == null) return;
        var shielders = dimShielders.get(dimId);
        if (shielders == null || shielders == dimShielders.defaultReturnValue()) return;
        refreshDimCache(dimId, shielders);
    }

    private void refreshDimCache(int dimId, ReferenceSet<ICirculationShielderBlockEntity> shielders) {
        if (shielders.isEmpty()) {
            idimShielders.remove(dimId);
            activeDimShielders.remove(dimId);
            return;
        }
        idimShielders.put(dimId, shielders.toArray(EMPTY));
        int activeCount = 0;
        for (ICirculationShielderBlockEntity shielder : shielders) {
            if (shielder.isActive()) {
                activeCount++;
            }
        }
        if (activeCount == 0) {
            activeDimShielders.remove(dimId);
            return;
        }
        ICirculationShielderBlockEntity[] active = new ICirculationShielderBlockEntity[activeCount];
        int index = 0;
        for (ICirculationShielderBlockEntity shielder : shielders) {
            if (shielder.isActive()) {
                active[index++] = shielder;
            }
        }
        activeDimShielders.put(dimId, active);
    }

    public boolean isBlockedByShielder(BlockPos tePos, ICirculationShielderBlockEntity[] shielders) {
        for (ICirculationShielderBlockEntity shielder : shielders) {
            if (!shielder.checkScope(tePos)) continue;
            return true;
        }
        return false;
    }
    //~}
    //~}
}
