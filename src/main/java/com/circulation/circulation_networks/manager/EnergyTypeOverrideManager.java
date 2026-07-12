package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.events.BlockEntityLifeCycleEvent;
import com.circulation.circulation_networks.packets.ConfigOverrideRendering;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import static com.circulation.circulation_networks.utils.SideCompat.isClientWorld;
//? if <1.20 {
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
//?} else {
/*import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
*///?}
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import java.io.File;
import java.io.IOException;

public final class EnergyTypeOverrideManager {

    private static volatile EnergyTypeOverrideManager INSTANCE;

    private final Int2ObjectMap<Long2ObjectMap<IEnergyHandler.EnergyType>> overrides = new Int2ObjectOpenHashMap<>();
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

    //? if <1.20 {
    private static MinecraftServer getServer() {
        return CirculationFlowNetworks.server;
    }

    private static int getPlayerDimensionId(EntityPlayerMP player) {
        return player.dimension;
    }
    //?} else if <1.21 {
    /*private static MinecraftServer getServer() {
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
    }

    private static int getPlayerDimensionId(ServerPlayer player) {
        return player.level().dimension().location().hashCode();
    }
    *///?} else {
    /*private static MinecraftServer getServer() {
        return net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
    }

    private static int getPlayerDimensionId(ServerPlayer player) {
        return player.level().dimension().location().hashCode();
    }
    *///?}

    //~ if >=1.20 'net.minecraft.world.World' -> 'net.minecraft.world.level.Level' {
    //~ if >=1.20 '.provider.getDimension()' -> '.dimension().location().hashCode()' {
    private static int getDimensionId(net.minecraft.world.World world) {
        return world.provider.getDimension();
    }

    //~ if >=1.20 '.toLong()' -> '.asLong()' {
    public void setOverride(int dim, BlockPos pos, IEnergyHandler.EnergyType type) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        long packedPos = pos.toLong();
        IEnergyHandler.EnergyType oldType = overrides.computeIfAbsent(dim, k -> new Long2ObjectOpenHashMap<>()).put(packedPos, type);
        MachineBindingIndex.INSTANCE.onEnergyTypeOverrideChanged(dim, packedPos, oldType, type);
        markDirty();
    }

    public void clearOverride(int dim, BlockPos pos) {
        var dimMap = overrides.get(dim);
        IEnergyHandler.EnergyType oldType = null;
        long packedPos = pos.toLong();
        if (dimMap != null) {
            oldType = dimMap.remove(packedPos);
            if (dimMap.isEmpty()) overrides.remove(dim);
        }
        MachineBindingIndex.INSTANCE.onEnergyTypeOverrideChanged(dim, packedPos, oldType, null);
        markDirty();
    }

    @Nullable
    public IEnergyHandler.EnergyType getOverride(int dim, BlockPos pos) {
        var dimMap = overrides.get(dim);
        if (dimMap == null) return null;
        return dimMap.get(pos.toLong());
    }

    public void markDirty() {
        m = true;
        DatPersistenceScheduler.INSTANCE.markDirty(DatPersistenceScheduler.Target.ENERGY_TYPE_OVERRIDE);
    }
    //~}

    @Nullable
    public Long2ObjectMap<IEnergyHandler.EnergyType> getOverridesForDim(int dim) {
        return overrides.get(dim);
    }

    public boolean isEmpty() {
        return overrides.isEmpty();
    }

    public boolean hasOverridesForDim(int dim) {
        var dimMap = overrides.get(dim);
        return dimMap != null && !dimMap.isEmpty();
    }

    public void onBlockEntityInvalidate(BlockEntityLifeCycleEvent.Invalidate event) {
        if (isClientWorld(event.getWorld())) return;
        int dim = getDimensionId(event.getWorld());
        BlockPos pos = event.getPos();
        if (getOverride(dim, pos) != null) {
            MinecraftServer server = getServer();
            if (server != null) {
                //~ if >=1.20 '.toLong()' -> '.asLong()' {
                long packedPos = pos.toLong();
                //~}
                //~ if >=1.20 'EntityPlayerMP' -> 'ServerPlayer' {
                for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                    if (getPlayerDimensionId(player) == dim) {
                        ConfigOverrideRendering.sendRemove(player, packedPos);
                    }
                }
                //~}
            }
        }
        clearOverride(dim, pos);
    }
    //~}
    //~}

    //? if <1.20 {
    private void loadFromFile() {
        File saveFile = new File(NetworkManager.getSaveFile(), "EnergyTypeOverride.dat");
        if (!saveFile.exists()) {
            MachineBindingIndex.INSTANCE.onEnergyTypeOverridesLoaded(this);
            return;
        }

        try {
            NBTTagCompound nbt = CompressedStreamTools.read(saveFile);
            if (nbt == null) {
                CirculationFlowNetworks.LOGGER.warn("Energy type override file {} contains no data", saveFile.getAbsolutePath());
                MachineBindingIndex.INSTANCE.onEnergyTypeOverridesLoaded(this);
                return;
            }

            overrides.clear();
            NBTTagList dims = nbt.getTagList("overrides", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < dims.tagCount(); i++) {
                NBTTagCompound dimTag = dims.getCompoundTagAt(i);
                int dim = dimTag.getInteger("dim");
                NBTTagList entries = dimTag.getTagList("entries", Constants.NBT.TAG_COMPOUND);
                Long2ObjectMap<IEnergyHandler.EnergyType> dimMap = new Long2ObjectOpenHashMap<>();
                for (int j = 0; j < entries.tagCount(); j++) {
                    NBTTagCompound entry = entries.getCompoundTagAt(j);
                    long pos = entry.getLong("pos");
                    int type = entry.getInteger("type");
                    var values = IEnergyHandler.EnergyType.values();
                    if (type >= 0 && type < values.length) {
                        dimMap.put(pos, values[type]);
                    }
                }
                if (!dimMap.isEmpty()) overrides.put(dim, dimMap);
            }
        } catch (IOException exception) {
            CirculationFlowNetworks.LOGGER.error("Failed to load energy type overrides from {}", saveFile.getAbsolutePath(), exception);
        }
        MachineBindingIndex.INSTANCE.onEnergyTypeOverridesLoaded(this);
    }

    private boolean saveToFile() {
        if (overrides.isEmpty() && !m) {
            return true;
        }

        File saveFile = new File(NetworkManager.getSaveFile(), "EnergyTypeOverride.dat");
        NBTTagCompound nbt = new NBTTagCompound();

        NBTTagList dims = new NBTTagList();
        for (var dimEntry : overrides.int2ObjectEntrySet()) {
            NBTTagCompound dimTag = new NBTTagCompound();
            dimTag.setInteger("dim", dimEntry.getIntKey());
            NBTTagList entries = new NBTTagList();
            for (var posEntry : dimEntry.getValue().long2ObjectEntrySet()) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setLong("pos", posEntry.getLongKey());
                entry.setInteger("type", posEntry.getValue().ordinal());
                entries.appendTag(entry);
            }
            dimTag.setTag("entries", entries);
            dims.appendTag(dimTag);
        }
        nbt.setTag("overrides", dims);

        if (NetworkManager.tryWriteCompressedNbt(nbt, saveFile, "energy type override save")) {
            m = false;
            return true;
        }
        return false;
    }
    //?} else {
    /*private void loadFromFile() {
        File saveFile = new File(NetworkManager.getSaveFile(), "EnergyTypeOverride.dat");
        if (!saveFile.exists()) {
            MachineBindingIndex.INSTANCE.onEnergyTypeOverridesLoaded(this);
            return;
        }

        try {
            CompoundTag nbt = NetworkManager.readCompressedNbt(saveFile);
            if (nbt == null) {
                CirculationFlowNetworks.LOGGER.warn("Energy type override file {} contains no data", saveFile.getAbsolutePath());
                MachineBindingIndex.INSTANCE.onEnergyTypeOverridesLoaded(this);
                return;
            }

            overrides.clear();
            ListTag dims = nbt.getList("overrides", Tag.TAG_COMPOUND);
            for (int i = 0; i < dims.size(); i++) {
                CompoundTag dimTag = dims.getCompound(i);
                int dim = dimTag.getInt("dim");
                ListTag entries = dimTag.getList("entries", Tag.TAG_COMPOUND);
                Long2ObjectMap<IEnergyHandler.EnergyType> dimMap = new Long2ObjectOpenHashMap<>();
                for (int j = 0; j < entries.size(); j++) {
                    CompoundTag entry = entries.getCompound(j);
                    long pos = entry.getLong("pos");
                    int type = entry.getInt("type");
                    var values = IEnergyHandler.EnergyType.values();
                    if (type >= 0 && type < values.length) {
                        dimMap.put(pos, values[type]);
                    }
                }
                if (!dimMap.isEmpty()) overrides.put(dim, dimMap);
            }
        } catch (IOException exception) {
            CirculationFlowNetworks.LOGGER.error("Failed to load energy type overrides from {}", saveFile.getAbsolutePath(), exception);
        }
        MachineBindingIndex.INSTANCE.onEnergyTypeOverridesLoaded(this);
    }

    private boolean saveToFile() {
        if (overrides.isEmpty() && !m) {
            return true;
        }

        File saveFile = new File(NetworkManager.getSaveFile(), "EnergyTypeOverride.dat");
        CompoundTag nbt = new CompoundTag();

        ListTag dims = new ListTag();
        for (var dimEntry : overrides.int2ObjectEntrySet()) {
            CompoundTag dimTag = new CompoundTag();
            dimTag.putInt("dim", dimEntry.getIntKey());
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
        } catch (IOException exception) {
            CirculationFlowNetworks.LOGGER.error("Failed to save energy type overrides to {}", saveFile.getAbsolutePath(), exception);
            return false;
        }
    }
    *///?}
}
