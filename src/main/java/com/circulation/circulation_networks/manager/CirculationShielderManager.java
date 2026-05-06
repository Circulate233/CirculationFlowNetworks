package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.ICirculationShielderBlockEntity;
import com.circulation.circulation_networks.utils.WorldResolveCompat;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import static com.circulation.circulation_networks.utils.WorldResolveCompat.isClientWorld;

@SuppressWarnings("unused")
public final class CirculationShielderManager {

    public static final CirculationShielderManager INSTANCE = new CirculationShielderManager();

    private final Object2ObjectMap<String, ReferenceSet<ICirculationShielderBlockEntity>> dimShielders = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, ICirculationShielderBlockEntity[]> dimShielderArrays = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, ICirculationShielderBlockEntity[]> activeDimShielderArrays = new Object2ObjectOpenHashMap<>();
    private static final ICirculationShielderBlockEntity[] EMPTY = new ICirculationShielderBlockEntity[0];

    public CirculationShielderManager() {
        dimShielders.defaultReturnValue(ReferenceSets.emptySet());
        dimShielderArrays.defaultReturnValue(EMPTY);
        activeDimShielderArrays.defaultReturnValue(EMPTY);
    }

    private static String getDimensionId(Level world) {
        return WorldResolveCompat.getDimensionId(world);
    }

    public Object2ObjectMap<String, ICirculationShielderBlockEntity[]> getDimShielders() {
        return dimShielderArrays;
    }

    public ICirculationShielderBlockEntity[] getShieldersForDim(String dimId) {
        return dimShielderArrays.get(dimId);
    }

    public ICirculationShielderBlockEntity[] getActiveShieldersForDim(String dimId) {
        return activeDimShielderArrays.get(dimId);
    }

    public void register(ICirculationShielderBlockEntity shielder, String dimId) {
        if (shielder == null) return;

        ReferenceSet<ICirculationShielderBlockEntity> set = dimShielders.get(dimId);
        if (set == dimShielders.defaultReturnValue()) {
            dimShielders.put(dimId, set = new ReferenceOpenHashSet<>());
        }
        set.add(shielder);
        refreshDimCache(dimId, set);
    }

    public void unregister(ICirculationShielderBlockEntity shielder, String dimId) {
        if (shielder == null) return;

        var shielders = dimShielders.get(dimId);
        if (shielders == null) return;
        shielders.remove(shielder);
        if (shielders.isEmpty()) dimShielders.remove(dimId);
        refreshDimCache(dimId, shielders);
    }

    public void refreshActiveState(ICirculationShielderBlockEntity shielder, String dimId) {
        if (shielder == null) return;
        var shielders = dimShielders.get(dimId);
        if (shielders == null || shielders == dimShielders.defaultReturnValue()) return;
        refreshDimCache(dimId, shielders);
    }

    private void refreshDimCache(String dimId, ReferenceSet<ICirculationShielderBlockEntity> shielders) {
        if (shielders.isEmpty()) {
            dimShielderArrays.remove(dimId);
            activeDimShielderArrays.remove(dimId);
            return;
        }
        dimShielderArrays.put(dimId, shielders.toArray(EMPTY));
        int activeCount = 0;
        for (ICirculationShielderBlockEntity shielder : shielders) {
            if (shielder.isActive()) {
                activeCount++;
            }
        }
        if (activeCount == 0) {
            activeDimShielderArrays.remove(dimId);
            return;
        }
        ICirculationShielderBlockEntity[] active = new ICirculationShielderBlockEntity[activeCount];
        int index = 0;
        for (ICirculationShielderBlockEntity shielder : shielders) {
            if (shielder.isActive()) {
                active[index++] = shielder;
            }
        }
        activeDimShielderArrays.put(dimId, active);
    }

    public boolean isBlockedByShielder(BlockPos tePos, ICirculationShielderBlockEntity[] shielders) {
        for (ICirculationShielderBlockEntity shielder : shielders) {
            if (!shielder.checkScope(tePos)) continue;
            return true;
        }
        return false;
    }
}
