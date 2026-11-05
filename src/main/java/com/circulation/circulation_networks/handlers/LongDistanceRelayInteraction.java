package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.manager.NetworkManager;
import com.circulation.circulation_networks.network.nodes.LongDistanceRelayNode;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Locale;

/** Server-side two-click selection; player keys expire when the player leaves. */
public final class LongDistanceRelayInteraction {
    private static final Map<Object, LongDistanceRelayNode> selections = new WeakHashMap<>();

    private LongDistanceRelayInteraction() {
    }

    public static String select(Object player, LongDistanceRelayNode target) {
        LongDistanceRelayNode source = selections.remove(player);
        if (target == null || !target.isActive()) return key("unavailable");
        if (source == null || !source.isActive()) {
            selections.put(player, target);
            return key("start");
        }
        if (source == target) return key("cancelled");
        if (!source.getSerializedDimensionKey().equals(target.getSerializedDimensionKey())) {
            return key("different_dimension");
        }
        return key(NetworkManager.INSTANCE.connectLongDistanceRelays(source, target).name().toLowerCase(Locale.ROOT));
    }

    private static String key(String suffix) {
        return "message.circulation_networks.long_distance_relay." + suffix;
    }

    public static void reset() {
        selections.clear();
    }

    public static String clear(Object player, LongDistanceRelayNode target) {
        int removed = NetworkManager.INSTANCE.clearLongDistanceRelayLinks(target);
        if (removed < 0) return key("unavailable");
        selections.remove(player);
        selections.values().removeIf(node -> node == target);
        return key("cleared");
    }
}
