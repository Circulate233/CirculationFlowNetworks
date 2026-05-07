package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.events.BlockEntityLifeCycleEvent;
import com.circulation.circulation_networks.packets.ConfigOverrideRendering;
import com.circulation.circulation_networks.utils.BlockPosCompat;
import com.circulation.circulation_networks.utils.NbtCompat;
import com.circulation.circulation_networks.utils.WorldResolveCompat;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

import static com.circulation.circulation_networks.utils.WorldResolveCompat.isClientWorld;

public final class EnergyTypeOverrideManager {

    private static volatile EnergyTypeOverrideManager INSTANCE;

    private final Object2ObjectMap<String, Long2ObjectMap<IEnergyHandler.EnergyType>> overrides = new Object2ObjectOpenHashMap<>();
    private boolean m;

    private EnergyTypeOverrideManager() {
    }

    @Nullable
    public static EnergyTypeOverrideManager get() {
        if (INSTANCE != null) return INSTANCE;
        if (!NetworkManager.isServerAvailable()) return null;
        INSTANCE = new EnergyTypeOverrideManager();
        INSTANCE.loadFromFile();
        return INSTANCE;
    }

    public static void onServerStop() {
        if (INSTANCE != null) {
            INSTANCE.overrides.clear();
        }
        INSTANCE = null;
    }

    public static boolean save() {
        if (INSTANCE != null) {
            return INSTANCE.saveToFile();
        }
        return true;
    }

    private static MinecraftServer getServer() {
        return WorldResolveCompat.getCurrentServer();
    }

    private static String getPlayerDimensionId(ServerPlayer player) {
        return WorldResolveCompat.getPlayerDimensionId(player);
    }

    private static String getDimensionId(net.minecraft.world.level.Level world) {
        return WorldResolveCompat.getDimensionId(world);
    }

    public void setOverride(String dim, BlockPos pos, IEnergyHandler.EnergyType type) {
        overrides.computeIfAbsent(dim, _ -> new Long2ObjectOpenHashMap<>()).put(BlockPosCompat.toLong(pos), type);
        markDirty();
    }

    public void clearOverride(String dim, BlockPos pos) {
        var dimMap = overrides.get(dim);
        if (dimMap != null) {
            dimMap.remove(BlockPosCompat.toLong(pos));
            if (dimMap.isEmpty()) overrides.remove(dim);
        }
        markDirty();
    }

    @Nullable
    public IEnergyHandler.EnergyType getOverride(String dim, BlockPos pos) {
        var dimMap = overrides.get(dim);
        if (dimMap == null) return null;
        return dimMap.get(BlockPosCompat.toLong(pos));
    }

    public void markDirty() {
        m = true;
        DatPersistenceScheduler.INSTANCE.markDirty(DatPersistenceScheduler.Target.ENERGY_TYPE_OVERRIDE);
    }

    @Nullable
    public Long2ObjectMap<IEnergyHandler.EnergyType> getOverridesForDim(String dim) {
        return overrides.get(dim);
    }

    public void onBlockEntityInvalidate(BlockEntityLifeCycleEvent.Invalidate event) {
        if (isClientWorld(event.getWorld())) return;
        String dim = getDimensionId(event.getWorld());
        BlockPos pos = event.getPos();
        if (getOverride(dim, pos) != null) {
            MinecraftServer server = getServer();
            if (server != null) {
                long packedPos = BlockPosCompat.toLong(pos);
                for (ServerPlayer player : WorldResolveCompat.getServerPlayers(server)) {
                    if (dim.equals(getPlayerDimensionId(player))) {
                        ConfigOverrideRendering.sendRemove(player, packedPos);
                    }
                }
            }
        }
        clearOverride(dim, pos);
    }

    private void loadFromFile() {
        File saveFile = new File(NetworkManager.getSaveFile(), "EnergyTypeOverride.dat");
        if (!saveFile.exists()) {
            return;
        }

        try {
            CompoundTag nbt = NetworkManager.readCompressedNbt(saveFile);
            if (nbt == null) return;

            overrides.clear();
            ListTag dims = NbtCompat.getListOrEmpty(nbt, "overrides");
            for (int i = 0; i < dims.size(); i++) {
                CompoundTag dimTag = NbtCompat.getCompoundOrEmpty(dims, i);
                String dim = NbtCompat.getStringOr(dimTag, "dim", "");
                if (dim.isEmpty()) continue;
                ListTag entries = NbtCompat.getListOrEmpty(dimTag, "entries");
                Long2ObjectMap<IEnergyHandler.EnergyType> dimMap = new Long2ObjectOpenHashMap<>();
                for (int j = 0; j < entries.size(); j++) {
                    CompoundTag entry = NbtCompat.getCompoundOrEmpty(entries, j);
                    long pos = NbtCompat.getLongOr(entry, "pos", 0L);
                    int type = NbtCompat.getIntOr(entry, "type", -1);
                    var values = IEnergyHandler.EnergyType.values();
                    if (type >= 0 && type < values.length) {
                        dimMap.put(pos, values[type]);
                    }
                }
                if (!dimMap.isEmpty()) overrides.put(dim, dimMap);
            }
        } catch (IOException ignored) {
        }
    }

    private boolean saveToFile() {
        if (overrides.isEmpty() && !m) {
            return true;
        }

        File saveFile = new File(NetworkManager.getSaveFile(), "EnergyTypeOverride.dat");
        CompoundTag nbt = new CompoundTag();

        ListTag dims = new ListTag();
        for (var dimEntry : overrides.object2ObjectEntrySet()) {
            CompoundTag dimTag = new CompoundTag();
            NbtCompat.putString(dimTag, "dim", dimEntry.getKey());
            ListTag entries = new ListTag();
            for (var posEntry : dimEntry.getValue().long2ObjectEntrySet()) {
                CompoundTag entry = new CompoundTag();
                entry.putLong("pos", posEntry.getLongKey());
                entry.putInt("type", posEntry.getValue().ordinal());
                entries.add(entry);
            }
            dimTag.put("entries", entries);
            dims.add(dimTag);
        }
        nbt.put("overrides", dims);

        try {
            NetworkManager.writeCompressedNbt(nbt, saveFile);
            m = false;
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
