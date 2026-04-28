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

    public CirculationShielderManager() {
        dimShielders.defaultReturnValue(ReferenceSets.emptySet());
    }

    private static String getDimensionId(Level world) {
        return WorldResolveCompat.getDimensionId(world);
    }

    public Object2ObjectMap<String, ReferenceSet<ICirculationShielderBlockEntity>> getDimShielders() {
        return dimShielders;
    }

    public ReferenceSet<ICirculationShielderBlockEntity> getShieldersForDim(String dimId) {
        return dimShielders.get(dimId);
    }

    public void register(ICirculationShielderBlockEntity shielder, String dimId) {
        if (shielder == null) return;

        ReferenceSet<ICirculationShielderBlockEntity> set = dimShielders.get(dimId);
        if (set == dimShielders.defaultReturnValue()) {
            dimShielders.put(dimId, set = new ReferenceOpenHashSet<>());
        }
        set.add(shielder);
    }

    public void unregister(ICirculationShielderBlockEntity shielder, String dimId) {
        if (shielder == null) return;

        var shielders = dimShielders.get(dimId);
        if (shielders == null) return;
        shielders.remove(shielder);
    }

    public boolean isBlockedByShielder(BlockPos tePos, Level world) {
        if (world == null || isClientWorld(world)) return false;
        String dimId = getDimensionId(world);

        var shielders = dimShielders.get(dimId);
        if (shielders == null || shielders.isEmpty()) return false;

        for (ICirculationShielderBlockEntity shielder : shielders) {
            if (!shielder.isActive()) continue;
            if (!shielder.checkScope(tePos)) continue;
            return true;
        }

        return false;
    }
}
