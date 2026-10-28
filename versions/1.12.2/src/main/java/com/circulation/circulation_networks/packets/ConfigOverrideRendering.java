package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.handlers.ConfigOverrideRenderingHandler;
import com.circulation.circulation_networks.manager.EnergyTypeOverrideManager;
import com.circulation.circulation_networks.utils.Packet;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import static com.circulation.circulation_networks.CirculationFlowNetworks.NET_CHANNEL;

public final class ConfigOverrideRendering implements Packet<ConfigOverrideRendering> {

    public static final int SET = 0;
    public static final int ADD = 1;
    public static final int REMOVE = 2;
    private static final long[] EL = new long[0];
    private static final int[] EI = new int[0];
    private static final IEnergyHandler.EnergyType[] ENERGY_TYPES = IEnergyHandler.EnergyType.values();

    private int mode;
    private long pos;
    private int energyTypeOrdinal;
    private long[] positions = EL;
    private int[] types = EI;

    public ConfigOverrideRendering(int dim) {
        this.mode = SET;
        var manager = EnergyTypeOverrideManager.get();
        if (manager != null) {
            Long2ObjectMap<IEnergyHandler.EnergyType> dimOverrides = manager.getOverridesForDim(dim);
            if (dimOverrides != null && !dimOverrides.isEmpty()) {
                positions = new long[dimOverrides.size()];
                types = new int[dimOverrides.size()];
                int i = 0;
                for (var entry : dimOverrides.long2ObjectEntrySet()) {
                    positions[i] = entry.getLongKey();
                    types[i] = entry.getValue().ordinal();
                    i++;
                }
            }
        }
    }

    public ConfigOverrideRendering(long pos, IEnergyHandler.EnergyType type) {
        this.mode = ADD;
        this.pos = pos;
        this.energyTypeOrdinal = type.ordinal();
    }

    public ConfigOverrideRendering(long pos) {
        this.mode = REMOVE;
        this.pos = pos;
    }

    public ConfigOverrideRendering() {

    }

    public static void sendFullSync(EntityPlayerMP player) {
        NET_CHANNEL.sendTo(new ConfigOverrideRendering(player.dimension), player);
    }

    public static void sendAdd(EntityPlayerMP player, long pos, IEnergyHandler.EnergyType type) {
        NET_CHANNEL.sendTo(new ConfigOverrideRendering(pos, type), player);
    }

    public static void sendRemove(EntityPlayerMP player, long pos) {
        NET_CHANNEL.sendTo(new ConfigOverrideRendering(pos), player);
    }

    public static void sendClear(EntityPlayerMP player) {
        NET_CHANNEL.sendTo(new ConfigOverrideRendering(), player);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(mode);
        switch (mode) {
            case SET -> {
                buf.writeInt(positions.length);
                for (int i = 0; i < positions.length; i++) {
                    buf.writeLong(positions[i]);
                    buf.writeByte(types[i]);
                }
            }
            case ADD -> {
                buf.writeLong(pos);
                buf.writeByte(energyTypeOrdinal);
            }
            case REMOVE -> buf.writeLong(pos);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        mode = buf.readByte();
        switch (mode) {
            case SET -> {
                int count = buf.readInt();
                positions = new long[count];
                types = new int[count];
                for (int i = 0; i < count; i++) {
                    positions[i] = buf.readLong();
                    types[i] = buf.readByte();
                }
            }
            case ADD -> {
                pos = buf.readLong();
                energyTypeOrdinal = buf.readByte();
            }
            case REMOVE -> pos = buf.readLong();
        }
    }

    @Override
    public IMessage onMessage(ConfigOverrideRendering message, MessageContext ctx) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            var handler = ConfigOverrideRenderingHandler.INSTANCE;
            switch (message.mode) {
                case SET -> {
                    handler.clear();
                    if (message.positions != null) {
                        for (int i = 0; i < message.positions.length; i++) {
                            if (message.types[i] >= 0 && message.types[i] < ENERGY_TYPES.length) {
                                handler.addOverride(message.positions[i], ENERGY_TYPES[message.types[i]]);
                            }
                        }
                    }
                }
                case ADD -> {
                    if (message.energyTypeOrdinal >= 0 && message.energyTypeOrdinal < ENERGY_TYPES.length) {
                        handler.addOverride(message.pos, ENERGY_TYPES[message.energyTypeOrdinal]);
                    }
                }
                case REMOVE -> handler.removeOverride(message.pos);
            }
        });
        return null;
    }
}
