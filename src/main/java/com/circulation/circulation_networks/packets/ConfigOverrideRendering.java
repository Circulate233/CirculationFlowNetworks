package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.handlers.ConfigOverrideRenderingHandler;
import com.circulation.circulation_networks.manager.EnergyTypeOverrideManager;
import com.circulation.circulation_networks.utils.Packet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class ConfigOverrideRendering implements Packet<ConfigOverrideRendering> {

    public static final int SET = 0;
    public static final int ADD = 1;
    public static final int REMOVE = 2;
    public static final Type<ConfigOverrideRendering> TYPE = new Type<>(
        Identifier.parse(CirculationFlowNetworks.MOD_ID + ":config_override_rendering")
    );
    private static final long[] EMPTY_LONGS = new long[0];
    private static final int[] EMPTY_INTS = new int[0];
    private static final IEnergyHandler.EnergyType[] ENERGY_TYPES = IEnergyHandler.EnergyType.values();
    private int mode;
    private long pos;
    private int energyTypeOrdinal;
    private long[] positions = EMPTY_LONGS;
    private int[] types = EMPTY_INTS;

    public ConfigOverrideRendering(String dim) {
        this.mode = SET;
        var manager = EnergyTypeOverrideManager.get();
        if (manager != null) {
            Long2ObjectMap<IEnergyHandler.EnergyType> dimOverrides = manager.getOverridesForDim(dim);
            if (dimOverrides != null && !dimOverrides.isEmpty()) {
                positions = new long[dimOverrides.size()];
                types = new int[dimOverrides.size()];
                int index = 0;
                for (var entry : dimOverrides.long2ObjectEntrySet()) {
                    positions[index] = entry.getLongKey();
                    types[index] = entry.getValue().ordinal();
                    index++;
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

    public static void sendFullSync(ServerPlayer player) {
        CirculationFlowNetworks.sendToPlayer(new ConfigOverrideRendering(player.level().dimension().identifier().toString()), player);
    }

    public static void sendAdd(ServerPlayer player, long pos, IEnergyHandler.EnergyType type) {
        CirculationFlowNetworks.sendToPlayer(new ConfigOverrideRendering(pos, type), player);
    }

    public static void sendRemove(ServerPlayer player, long pos) {
        CirculationFlowNetworks.sendToPlayer(new ConfigOverrideRendering(pos), player);
    }

    public static void sendClear(ServerPlayer player) {
        CirculationFlowNetworks.sendToPlayer(new ConfigOverrideRendering(), player);
    }

    public @NonNull ConfigOverrideRendering decode(@NonNull RegistryFriendlyByteBuf buf) {
        ConfigOverrideRendering message = new ConfigOverrideRendering();
        message.mode = buf.readByte();
        switch (message.mode) {
            case SET -> {
                int count = buf.readInt();
                message.positions = new long[count];
                message.types = new int[count];
                for (int i = 0; i < count; i++) {
                    message.positions[i] = buf.readLong();
                    message.types[i] = buf.readByte();
                }
            }
            case ADD -> {
                message.pos = buf.readLong();
                message.energyTypeOrdinal = buf.readByte();
            }
            case REMOVE -> message.pos = buf.readLong();
            default -> {
            }
        }
        return message;
    }

    public void encode(@NonNull RegistryFriendlyByteBuf buf) {
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
            default -> {
            }
        }
    }

    public void handle(@NonNull ConfigOverrideRendering message, @NonNull IPayloadContext context) {
        context.enqueueWork(() -> {
            var handler = ConfigOverrideRenderingHandler.INSTANCE;
            switch (message.mode) {
                case SET -> {
                    handler.clear();
                    for (int i = 0; i < message.positions.length; i++) {
                        if (message.types[i] >= 0 && message.types[i] < ENERGY_TYPES.length) {
                            handler.addOverride(message.positions[i], ENERGY_TYPES[message.types[i]]);
                        }
                    }
                }
                case ADD -> {
                    if (message.energyTypeOrdinal >= 0 && message.energyTypeOrdinal < ENERGY_TYPES.length) {
                        handler.addOverride(message.pos, ENERGY_TYPES[message.energyTypeOrdinal]);
                    }
                }
                case REMOVE -> handler.removeOverride(message.pos);
                default -> {
                }
            }
        });
    }

    @NotNull
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
