package com.circulation.circulation_networks.utils;

import com.circulation.circulation_networks.api.node.INode;

public final class UnionFindUtils {

    public static void makeSet(INode node) {
        node.setParent(node);
        node.setRank(0);
    }

    public static INode find(INode node) {
        if (node == null || !node.isActive()) return null;
        if (node.getParent() != node) {
            node.setParent(find(node.getParent()));
        }
        return node.getParent();
    }

    public static void union(INode a, INode b) {
        if (a == null || b == null || !a.isActive() || !b.isActive()) return;
        INode rootA = find(a);
        INode rootB = find(b);
        if (rootA == rootB) return;

        if (rootA.getRank() < rootB.getRank()) {
            rootA.setParent(rootB);
        } else {
            rootB.setParent(rootA);
            if (rootA.getRank() == rootB.getRank()) rootA.setRank(rootA.getRank() + 1);
        }
    }

}