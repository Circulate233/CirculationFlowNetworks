package com.circulation.circulation_networks.network.nodes;

import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.api.node.NodeContext;
import com.circulation.circulation_networks.registry.NodeTypes;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
//~ mc_imports
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import java.util.UUID;

/** Placed relay with zero automatic reach and explicit, persistent peer links. */
public final class LongDistanceRelayNode extends Node {
    public static final int CONNECTION_CAPACITY = 3;
    private UUID relayId = UUID.randomUUID();
    private final Long2ObjectMap<UUID> peers = new Long2ObjectOpenHashMap<>();
    private boolean persistentState;

    public LongDistanceRelayNode(NodeContext context) {
        super(NodeTypes.LONG_DISTANCE_RELAY_NODE, context, 0);
    }

    //~ if >=1.20 'NBTTagCompound' -> 'CompoundTag' {
    public LongDistanceRelayNode(NBTTagCompound tag) {
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

    //~ if >=1.20 '.toLong()' -> '.asLong()' {
    @Override
    public boolean canLinkTo(INode other) {
        return other instanceof LongDistanceRelayNode relay && other != this
            && getSerializedDimensionKey().equals(relay.getSerializedDimensionKey())
            && relay.relayId.equals(peers.get(relay.getPos().toLong()))
            && relayId.equals(relay.peers.get(getPos().toLong()));
    }

    public void rememberPeer(LongDistanceRelayNode peer) {
        peers.put(peer.getPos().toLong(), peer.relayId);
        persistentState = true;
    }

    public void forgetPeer(LongDistanceRelayNode peer) {
        peers.remove(peer.getPos().toLong(), peer.relayId);
    }

    public void clearPeers() {
        peers.clear();
        persistentState = true;
    }
    //~}

    public void readLinkData(NBTTagCompound tag) {
        persistentState = true;
        String id = tag.getString("relayId");
        if (!id.isEmpty()) {
            try {
                relayId = UUID.fromString(id);
            } catch (IllegalArgumentException ignored) {
                // An invalid identity must not reconnect to an unrelated block.
                relayId = UUID.randomUUID();
            }
        }
        peers.clear();
        //? if <1.20 {
        NBTTagList list = tag.getTagList("relayPeers", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
        //?} else {
        /*ListTag list = tag.getList("relayPeers", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
        *///?}
            try {
                peers.put(entry.getLong("pos"), UUID.fromString(entry.getString("id")));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed saved peers independently.
            }
        }
    }

    public NBTTagCompound writeLinkData(NBTTagCompound tag) {
        //? if <1.20 {
        tag.setString("relayId", relayId.toString());
        NBTTagList list = new NBTTagList();
        for (var peer : peers.long2ObjectEntrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setLong("pos", peer.getLongKey());
            entry.setString("id", peer.getValue().toString());
            list.appendTag(entry);
        }
        tag.setTag("relayPeers", list);
        //?} else {
        /*tag.putString("relayId", relayId.toString());
        ListTag list = new ListTag();
        for (var peer : peers.long2ObjectEntrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", peer.getLongKey());
            entry.putString("id", peer.getValue().toString());
            list.add(entry);
        }
        tag.put("relayPeers", list);
        *///?}
        return tag;
    }

    @Override
    public NBTTagCompound serialize() {
        return writeLinkData(super.serialize());
    }
    //~}
}
