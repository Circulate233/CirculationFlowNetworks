package com.circulation.circulation_networks.network.nodes;

import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.api.node.NodeContext;
import com.circulation.circulation_networks.registry.NodeTypes;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.UUID;
import com.circulation.circulation_networks.utils.NbtCompat;


public final class LongDistanceRelayNode extends Node {
    public static final int CONNECTION_CAPACITY = 3;
    private UUID relayId = UUID.randomUUID();
    private final Long2ObjectMap<UUID> peers = new Long2ObjectOpenHashMap<>();
    private boolean persistentState;

    public LongDistanceRelayNode(NodeContext context) {
        super(NodeTypes.LONG_DISTANCE_RELAY_NODE, context, 0);
    }
    public LongDistanceRelayNode(CompoundTag tag) {
        super(NodeTypes.LONG_DISTANCE_RELAY_NODE, tag);
        readLinkData(tag);
    }

    public UUID getRelayId() {
        return relayId;
    }

    public boolean hasPersistentState() {
        return persistentState;
    }

    public LongSet getPeerPositions() {
        return LongSets.unmodifiable(peers.keySet());
    }

    public boolean hasFreeConnection() {
        return peers.size() < CONNECTION_CAPACITY;
    }

    @Override
    public double getLinkScope() {
        return 0;
    }

    @Override
    public double getLinkScopeSq() {
        return 0;
    }
    @Override
    public boolean canLinkTo(INode other) {
        return other instanceof LongDistanceRelayNode relay && other != this
            && getDimensionId().equals(relay.getDimensionId())
            && relay.relayId.equals(peers.get(relay.getPos().asLong()))
            && relayId.equals(relay.peers.get(getPos().asLong()));
    }

    public void rememberPeer(LongDistanceRelayNode peer) {
        peers.put(peer.getPos().asLong(), peer.relayId);
        persistentState = true;
    }

    public void forgetPeer(LongDistanceRelayNode peer) {
        peers.remove(peer.getPos().asLong(), peer.relayId);
    }

    public void clearPeers() {
        peers.clear();
        persistentState = true;
    }

    public void readLinkData(CompoundTag tag) {
        persistentState = true;
        String id = NbtCompat.getStringOr(tag, "relayId", "");
        if (!id.isEmpty()) {
            try {
                relayId = UUID.fromString(id);
            } catch (IllegalArgumentException ignored) {
                // An invalid identity must not reconnect to an unrelated block.
                relayId = UUID.randomUUID();
            }
        }
        peers.clear();
        ListTag list = NbtCompat.getListOrEmpty(tag, "relayPeers");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = NbtCompat.getCompoundOrEmpty(list, i);
            try {
                peers.put(NbtCompat.getLongOr(entry, "pos", 0L), UUID.fromString(NbtCompat.getStringOr(entry, "id", "")));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed saved peers independently.
            }
        }
    }

    public CompoundTag writeLinkData(CompoundTag tag) {
        tag.putString("relayId", relayId.toString());
        ListTag list = new ListTag();
        for (var peer : peers.long2ObjectEntrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", peer.getLongKey());
            entry.putString("id", peer.getValue().toString());
            list.add(entry);
        }
        tag.put("relayPeers", list);
        return tag;
    }

    @Override
    public CompoundTag serialize() {
        return writeLinkData(super.serialize());
    }
}
