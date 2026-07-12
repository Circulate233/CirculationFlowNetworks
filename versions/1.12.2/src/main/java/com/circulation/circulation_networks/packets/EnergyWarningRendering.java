package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.handlers.EnergyWarningRenderingHandler;
import com.circulation.circulation_networks.utils.Packet;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.nio.charset.StandardCharsets;

public final class EnergyWarningRendering implements Packet<EnergyWarningRendering> {

    private static final int MAX_DIMENSION_KEY_BYTES = 32_767;
    private String dimensionKey;
    private long sessionGeneration;
    private long revision;
    private LongList positions;

    public EnergyWarningRendering() {
    }

    public EnergyWarningRendering(String dimensionKey, long sessionGeneration, long revision, LongCollection positions) {
        this.dimensionKey = requireDimensionKey(dimensionKey);
        this.sessionGeneration = sessionGeneration;
        this.revision = revision;
        this.positions = new LongArrayList(Objects.requireNonNull(positions, "positions"));
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dimensionKey = readDimensionKey(buf);
        sessionGeneration = buf.readLong();
        revision = buf.readLong();
        positions = readPositions(buf, "positions");
    }

    private static String readDimensionKey(ByteBuf buf) {
        int length = buf.readInt();
        if (length <= 0 || length > MAX_DIMENSION_KEY_BYTES || length > buf.readableBytes()) {
            throw new IllegalArgumentException("Invalid dimension key byte length: " + length);
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return requireDimensionKey(new String(bytes, StandardCharsets.UTF_8));
    }

    private static String requireDimensionKey(String dimensionKey) {
        String key = Objects.requireNonNull(dimensionKey, "dimensionKey");
        int length = key.getBytes(StandardCharsets.UTF_8).length;
        if (key.isEmpty() || length > MAX_DIMENSION_KEY_BYTES) {
            throw new IllegalArgumentException("Invalid dimension key byte length: " + length);
        }
        return key;
    }

    private static LongList readPositions(ByteBuf buf, String fieldName) {
        int count = buf.readInt();
        if (count < 0) {
            throw new IllegalArgumentException("Negative " + fieldName + " count: " + count);
        }
        int readableLongs = buf.readableBytes() / Long.BYTES;
        if (count > readableLongs) {
            throw new IllegalArgumentException(fieldName + " count " + count
                + " exceeds remaining payload capacity " + readableLongs);
        }
        LongList positions = new LongArrayList(count);
        for (int i = 0; i < count; i++) {
            positions.add(buf.readLong());
        }
        return positions;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        byte[] dimensionBytes = requireDimensionKey(dimensionKey).getBytes(StandardCharsets.UTF_8);
        buf.writeInt(dimensionBytes.length);
        buf.writeBytes(dimensionBytes);
        buf.writeLong(sessionGeneration);
        buf.writeLong(revision);
        buf.writeInt(positions.size());
        for (long posLong : positions) {
            buf.writeLong(posLong);
        }
    }

    @Override
    public @Nullable IMessage onMessage(EnergyWarningRendering message, MessageContext ctx) {
        Minecraft.getMinecraft().addScheduledTask(() -> EnergyWarningRenderingHandler.INSTANCE.applySnapshot(
            message.dimensionKey,
            message.sessionGeneration,
            message.revision,
            message.positions
        ));
        return null;
    }
}
