package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.ICirculationShielderBlockEntity;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import static com.circulation.circulation_networks.utils.WorldSideCompat.isClientWorld;
//~ mc_imports
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class CirculationShielderManager {

    public static final CirculationShielderManager INSTANCE = new CirculationShielderManager();

    private final Int2ObjectMap<ReferenceSet<ICirculationShielderBlockEntity>> dimShielders = new Int2ObjectOpenHashMap<>();

    public CirculationShielderManager() {
        dimShielders.defaultReturnValue(ReferenceSets.emptySet());
    }

    //~ if >=1.20 '(World ' -> '(Level ' {
    //~ if >=1.20 '.provider.getDimension()' -> '.dimension().location().hashCode()' {
    private static int getDimensionId(World world) {
        return world.provider.getDimension();
    }

    public Int2ObjectMap<ReferenceSet<ICirculationShielderBlockEntity>> getDimShielders() {
        return dimShielders;
    }

    public ReferenceSet<ICirculationShielderBlockEntity> getShieldersForDim(int dimId) {
        return dimShielders.get(dimId);
    }

    public void register(ICirculationShielderBlockEntity shielder, int dimId) {
        if (shielder == null) return;

        ReferenceSet<ICirculationShielderBlockEntity> set = dimShielders.get(dimId);
        if (set == dimShielders.defaultReturnValue()) {
            dimShielders.put(dimId, set = new ReferenceOpenHashSet<>());
        }
        set.add(shielder);
    }

    public void unregister(ICirculationShielderBlockEntity shielder, int dimId) {
        if (shielder == null) return;

        var shielders = dimShielders.get(dimId);
        if (shielders == null) return;
        shielders.remove(shielder);
    }

    //~ if >=1.20 ' World ' -> ' Level ' {
    public boolean isBlockedByShielder(BlockPos tePos, World world) {
        //~}
        if (world == null || isClientWorld(world)) return false;
        int dimId = getDimensionId(world);

        var shielders = dimShielders.get(dimId);
        if (shielders == null || shielders.isEmpty()) return false;

        for (ICirculationShielderBlockEntity shielder : shielders) {
            if (!shielder.isActive()) continue;
            if (!shielder.checkScope(tePos)) continue;
            return true;
        }

        return false;
    }
    //~}
    //~}
}
