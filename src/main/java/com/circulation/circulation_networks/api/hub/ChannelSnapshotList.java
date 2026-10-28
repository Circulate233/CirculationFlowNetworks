package com.circulation.circulation_networks.api.hub;

import it.unimi.dsi.fastutil.objects.ObjectLists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public final class ChannelSnapshotList {

    public static final ChannelSnapshotList EMPTY = new ChannelSnapshotList(ObjectLists.emptyList());
    private static final Gson GSON = new GsonBuilder().create();

    private final List<ChannelSnapshotEntry> entries;
    private String json;
    private byte[] bytes;

    public ChannelSnapshotList(List<ChannelSnapshotEntry> entries) {
        this.entries = entries.isEmpty()
            ? ObjectLists.emptyList()
            : ObjectLists.unmodifiable(new ObjectArrayList<>(entries));
    }

    public static ChannelSnapshotList fromJson(String json) {
        ChannelSnapshotList list = GSON.fromJson(json, ChannelSnapshotList.class);
        if (list == null || list.entries == null || list.entries.isEmpty()) {
            return EMPTY;
        }
        return new ChannelSnapshotList(list.entries);
    }

    public static ChannelSnapshotList fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return EMPTY;
        }

        try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int size = readVarInt(data);
            if (size <= 0) {
                return EMPTY;
            }

            List<ChannelSnapshotEntry> entries = new ObjectArrayList<>(size);
            for (int i = 0; i < size; i++) {
                UUID id = new UUID(data.readLong(), data.readLong());
                PermissionMode permissionMode = PermissionMode.fromId(data.readUnsignedByte());
                HubPermissionLevel permission = HubPermissionLevel.fromId(data.readUnsignedByte());
                boolean connected = data.readBoolean();
                String name = readString(data);
                entries.add(new ChannelSnapshotEntry(id, name, permissionMode, permission, connected));
            }
            return entries.isEmpty() ? EMPTY : new ChannelSnapshotList(entries);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to decode channel snapshot", e);
        }
    }

    public List<ChannelSnapshotEntry> getEntries() {
        return entries;
    }

    public String toJson() {
        if (json == null) {
            json = GSON.toJson(this);
        }
        return json;
    }

    public byte[] toBytes() {
        if (bytes == null) {
            try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                 DataOutputStream data = new DataOutputStream(output)) {
                writeVarInt(data, entries.size());
                for (ChannelSnapshotEntry entry : entries) {
                    UUID id = entry.id();
                    data.writeLong(id.getMostSignificantBits());
                    data.writeLong(id.getLeastSignificantBits());
                    data.writeByte(entry.permissionMode().getId());
                    data.writeByte(entry.permission().getId());
                    data.writeBoolean(entry.connected());
                    writeString(data, entry.name());
                }
                bytes = output.toByteArray();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to encode channel snapshot", e);
            }
        }
        return bytes.clone();
    }

    private static void writeString(DataOutputStream data, String value) throws IOException {
        byte[] encoded = value != null ? value.getBytes(StandardCharsets.UTF_8) : new byte[0];
        writeVarInt(data, encoded.length);
        data.write(encoded);
    }

    private static String readString(DataInputStream data) throws IOException {
        int length = readVarInt(data);
        if (length == 0) {
            return "";
        }
        byte[] encoded = new byte[length];
        data.readFully(encoded);
        return new String(encoded, StandardCharsets.UTF_8);
    }

    private static void writeVarInt(DataOutputStream data, int value) throws IOException {
        int current = value;
        while ((current & ~0x7F) != 0) {
            data.writeByte((current & 0x7F) | 0x80);
            current >>>= 7;
        }
        data.writeByte(current);
    }

    private static int readVarInt(DataInputStream data) throws IOException {
        int value = 0;
        int position = 0;
        while (true) {
            int currentByte = data.readUnsignedByte();
            value |= (currentByte & 0x7F) << position;
            if ((currentByte & 0x80) == 0) {
                return value;
            }
            position += 7;
            if (position >= 32) {
                throw new IOException("VarInt is too big");
            }
        }
    }
}