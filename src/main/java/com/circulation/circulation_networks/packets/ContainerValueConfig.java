package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.container.CFNBaseContainer;
import com.circulation.circulation_networks.utils.Packet;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class ContainerValueConfig implements Packet<ContainerValueConfig> {

    public static final Type<ContainerValueConfig> TYPE = new Type<>(
        Identifier.parse(CirculationFlowNetworks.MOD_ID + ":container_value_config")
    );
    private static final int COMPRESSION_THRESHOLD = 256;
    private static final byte TYPE_STRING = 0;
    private static final byte TYPE_BYTES = 1;
    private final short name;
    private final byte payloadType;
    private final String stringValue;
    private final byte[] bytesValue;

    public ContainerValueConfig() {
        this((short) 0, TYPE_STRING, null, null);
    }

    public ContainerValueConfig(short name, String value) {
        this(name, TYPE_STRING, value, null);
    }

    public ContainerValueConfig(short name, byte[] value) {
        this(name, TYPE_BYTES, null, value != null ? value.clone() : new byte[0]);
    }

    private ContainerValueConfig(short name, byte payloadType, String stringValue, byte[] bytesValue) {
        this.name = name;
        this.payloadType = payloadType;
        this.stringValue = stringValue;
        this.bytesValue = bytesValue;
    }

    private static byte[] deflate(byte[] input) {
        byte[] buffer = new byte[Math.clamp(input.length, 256, 4096)];
        try (Deflater deflater = new Deflater(Deflater.BEST_SPEED);
             ByteArrayOutputStream output = new ByteArrayOutputStream(input.length)) {
            deflater.setInput(input);
            deflater.finish();
            while (!deflater.finished()) {
                int written = deflater.deflate(buffer);
                if (written <= 0) {
                    break;
                }
                output.write(buffer, 0, written);
            }
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deflate container sync payload", e);
        }
    }

    private static byte[] inflate(byte[] input) {
        byte[] buffer = new byte[Math.clamp(input.length * 2L, 256, 4096)];
        try (Inflater inflater = new Inflater();
             ByteArrayOutputStream output = new ByteArrayOutputStream(input.length * 2)) {
            inflater.setInput(input);
            while (!inflater.finished()) {
                int written = inflater.inflate(buffer);
                if (written > 0) {
                    output.write(buffer, 0, written);
                    continue;
                }
                if (inflater.needsDictionary() || inflater.needsInput()) {
                    break;
                }
            }
            return output.toByteArray();
        } catch (DataFormatException e) {
            throw new IllegalStateException("Failed to inflate container sync payload", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read container sync payload", e);
        }
    }

    public ContainerValueConfig decode(@NonNull RegistryFriendlyByteBuf buf) {
        short decodedName = buf.readShort();
        byte type = buf.readByte();
        boolean compressed = buf.readBoolean();
        int length = buf.readInt();
        byte[] payload = new byte[length];
        buf.readBytes(payload);
        byte[] decoded = compressed ? inflate(payload) : payload;
        if (type == TYPE_BYTES) {
            return new ContainerValueConfig(decodedName, type, null, decoded);
        }
        return new ContainerValueConfig(decodedName, type, new String(decoded, StandardCharsets.UTF_8), null);
    }

    public void encode(@NonNull RegistryFriendlyByteBuf buf) {
        buf.writeShort(name);
        buf.writeByte(payloadType);
        byte[] raw = payloadType == TYPE_BYTES
            ? (bytesValue != null ? bytesValue : new byte[0])
            : (stringValue != null ? stringValue.getBytes(StandardCharsets.UTF_8) : new byte[0]);
        byte[] payload = raw;
        boolean compressed = raw.length >= COMPRESSION_THRESHOLD;
        if (compressed) {
            byte[] compressedPayload = deflate(raw);
            if (compressedPayload.length < raw.length) {
                payload = compressedPayload;
            } else {
                compressed = false;
            }
        }

        buf.writeBoolean(compressed);
        buf.writeInt(payload.length);
        buf.writeBytes(payload);
    }

    public void handle(@NonNull ContainerValueConfig message, @NonNull IPayloadContext context) {
        if (context.player() instanceof ServerPlayer) {
            return;
        }

        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null || !(player.containerMenu instanceof CFNBaseContainer container)) {
                return;
            }
            if (message.payloadType == TYPE_BYTES) {
                container.bytesSync(message.name, message.bytesValue);
            } else {
                container.stringSync(message.name, message.stringValue);
            }
        });
    }

    @NotNull
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
