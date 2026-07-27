package com.circulation.circulation_networks.api.hub;

import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.manager.PocketNodeManager;
import com.circulation.circulation_networks.registry.PocketNodeItems;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class NodeSnapshotList {

    public static final NodeSnapshotList EMPTY = new NodeSnapshotList(Collections.emptyList());
    private static final Gson GSON = new GsonBuilder().create();
    public static final String EMPTY_JSON = EMPTY.toJson();

    private final List<NodeSnapshotEntry> entries;
    private String json;
    private byte[] bytes;

    public NodeSnapshotList(List<NodeSnapshotEntry> entries) {
        this.entries = entries.isEmpty()
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ObjectArrayList<>(entries));
    }

    public static NodeSnapshotList fromJson(String json) {
        NodeSnapshotList list = GSON.fromJson(json, NodeSnapshotList.class);
        if (list == null || list.entries == null || list.entries.isEmpty()) {
            return EMPTY;
        }
        return new NodeSnapshotList(list.entries);
    }

    public static NodeSnapshotList fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return EMPTY;
        }
        try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int size = readVarInt(data);
            if (size <= 0) {
                return EMPTY;
            }
            List<NodeSnapshotEntry> entries = new ObjectArrayList<>(size);
            int previousX = 0;
            int previousY = 0;
            int previousZ = 0;
            boolean first = true;
            for (int i = 0; i < size; i++) {
                String itemId = resolveItemId(readVarInt(data));
                int x;
                int y;
                int z;
                if (first) {
                    x = readZigZagInt(data);
                    y = readZigZagInt(data);
                    z = readZigZagInt(data);
                    first = false;
                } else {
                    x = previousX + readZigZagInt(data);
                    y = previousY + readZigZagInt(data);
                    z = previousZ + readZigZagInt(data);
                }
                String customName = readNullableString(data);
                entries.add(new NodeSnapshotEntry(itemId, x, y, z, customName));
                previousX = x;
                previousY = y;
                previousZ = z;
            }
            return entries.isEmpty() ? EMPTY : new NodeSnapshotList(entries);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to decode node snapshot", e);
        }
    }

    public static NodeSnapshotList fromGrid(@Nullable IGrid grid) {
        if (grid == null || grid.getNodes().isEmpty()) {
            return EMPTY;
        }

        List<NodeSnapshotEntry> entries = new ObjectArrayList<>(grid.getNodes().size());
        for (INode node : grid.getNodes()) {
            String itemId = resolveItemId(node);
            var pos = node.getPos();
            entries.add(new NodeSnapshotEntry(itemId, pos.getX(), pos.getY(), pos.getZ(), node.getCustomName()));
        }
        entries.sort(Comparator.comparingInt(NodeSnapshotEntry::x)
                               .thenComparingInt(NodeSnapshotEntry::y)
                               .thenComparingInt(NodeSnapshotEntry::z)
                               .thenComparing(NodeSnapshotEntry::itemId));
        return entries.isEmpty() ? EMPTY : new NodeSnapshotList(entries);
    }

    private static String resolveItemId(INode node) {
        ItemStack displayStack = ItemStack.EMPTY;
        if (PocketNodeManager.INSTANCE.isActivePocketNode(node.getWorld(), node.getPos(), node.getNodeType())) {
            displayStack = PocketNodeItems.createStack(node.getNodeType());
        }
        if (displayStack.isEmpty()) {
            displayStack = resolveVisualItemStack(node.getVisualId());
        }
        if (displayStack.isEmpty()) {
            return "";
        }
        Identifier registryName = BuiltInRegistries.ITEM.getKey(displayStack.getItem());
        return registryName.toString();
    }

    private static ItemStack resolveVisualItemStack(String visualId) {
        if (visualId == null || visualId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Identifier location = Identifier.parse(visualId);
        var itemHolder = BuiltInRegistries.ITEM.get(Identifier.parse(visualId)).orElse(null);
        Item item = itemHolder != null ? itemHolder.value() : null;
        if (item != null && item != Items.AIR) {
            return new ItemStack(item);
        }
        var blockHolder = BuiltInRegistries.BLOCK.get(Identifier.parse(visualId)).orElse(null);
        Block block = blockHolder != null ? blockHolder.value() : null;
        Item blockItem = Item.BY_BLOCK.getOrDefault(block, Items.AIR);
        return blockItem != Items.AIR ? new ItemStack(blockItem) : ItemStack.EMPTY;
    }

    private static int resolveItemIntId(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return 0;
        }
        var holder = BuiltInRegistries.ITEM.get(Identifier.parse(itemId)).orElse(null);
        return holder != null ? BuiltInRegistries.ITEM.getId(holder.value()) : 0;
    }

    private static String resolveItemId(int itemId) {
        return BuiltInRegistries.ITEM.getKey(BuiltInRegistries.ITEM.byId(itemId)).toString();
    }

    private static void writeNullableString(DataOutputStream data, @Nullable String value) throws IOException {
        if (value == null) {
            data.writeBoolean(false);
            return;
        }
        data.writeBoolean(true);
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(data, encoded.length);
        data.write(encoded);
    }

    @Nullable
    private static String readNullableString(DataInputStream data) throws IOException {
        if (!data.readBoolean()) {
            return null;
        }
        int length = readVarInt(data);
        byte[] encoded = new byte[length];
        data.readFully(encoded);
        return new String(encoded, StandardCharsets.UTF_8);
    }

    private static void writeZigZagInt(DataOutputStream data, int value) throws IOException {
        writeVarInt(data, (value << 1) ^ (value >> 31));
    }

    private static int readZigZagInt(DataInputStream data) throws IOException {
        int value = readVarInt(data);
        return (value >>> 1) ^ -(value & 1);
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

    public List<NodeSnapshotEntry> getEntries() {
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
                int previousX = 0;
                int previousY = 0;
                int previousZ = 0;
                boolean first = true;
                for (NodeSnapshotEntry entry : entries) {
                    writeVarInt(data, resolveItemIntId(entry.itemId()));
                    if (first) {
                        writeZigZagInt(data, entry.x());
                        writeZigZagInt(data, entry.y());
                        writeZigZagInt(data, entry.z());
                        first = false;
                    } else {
                        writeZigZagInt(data, entry.x() - previousX);
                        writeZigZagInt(data, entry.y() - previousY);
                        writeZigZagInt(data, entry.z() - previousZ);
                    }
                    writeNullableString(data, entry.customName());
                    previousX = entry.x();
                    previousY = entry.y();
                    previousZ = entry.z();
                }
                bytes = output.toByteArray();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to encode node snapshot", e);
            }
        }
        return bytes.clone();
    }
}
