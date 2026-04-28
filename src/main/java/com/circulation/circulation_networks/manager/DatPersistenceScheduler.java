package com.circulation.circulation_networks.manager;

import java.util.EnumMap;

public final class DatPersistenceScheduler {

    public static final DatPersistenceScheduler INSTANCE = new DatPersistenceScheduler();
    private static final long SAVE_DELAY_TICKS = 20L;
    private static final long FORCE_SAVE_TICKS = 200L;
    private static final long RETRY_DELAY_TICKS = 20L;
    private final EnumMap<Target, TargetState> targets = new EnumMap<>(Target.class);
    private long currentTick;

    private DatPersistenceScheduler() {
        reset();
    }

    public void markDirty(Target target) {
        TargetState state = targets.get(target);
        if (state == null) {
            return;
        }
        if (!state.pending) {
            state.pending = true;
            state.firstDirtyTick = currentTick;
        }
        state.lastDirtyTick = currentTick;
    }

    public void onServerTick() {
        for (Target target : Target.values()) {
            tryPersistTarget(target);
        }
        currentTick++;
    }

    public void reset() {
        currentTick = 0L;
        targets.clear();
        for (Target target : Target.values()) {
            targets.put(target, new TargetState());
        }
    }

    private void tryPersistTarget(Target target) {
        TargetState state = targets.get(target);
        if (state == null || !state.pending) {
            return;
        }
        if (currentTick - state.lastAttemptTick < RETRY_DELAY_TICKS) {
            return;
        }
        if (currentTick - state.lastDirtyTick < SAVE_DELAY_TICKS
            && currentTick - state.firstDirtyTick < FORCE_SAVE_TICKS) {
            return;
        }

        state.lastAttemptTick = currentTick;
        if (target.save()) {
            state.reset();
        }
    }

    public enum Target {
        NETWORK_GRID {
            @Override
            boolean save() {
                return NetworkManager.INSTANCE.saveGrid();
            }
        },
        POCKET_NODE {
            @Override
            boolean save() {
                return PocketNodeManager.INSTANCE.save();
            }
        },
        HUB_CHANNEL {
            @Override
            boolean save() {
                return HubChannelManager.INSTANCE.save();
            }
        },
        ENERGY_TYPE_OVERRIDE {
            @Override
            boolean save() {
                return EnergyTypeOverrideManager.save();
            }
        };

        abstract boolean save();
    }

    private static final class TargetState {
        private boolean pending;
        private long firstDirtyTick;
        private long lastDirtyTick;
        private long lastAttemptTick = -RETRY_DELAY_TICKS;

        private void reset() {
            pending = false;
            firstDirtyTick = 0L;
            lastDirtyTick = 0L;
            lastAttemptTick = -RETRY_DELAY_TICKS;
        }
    }
}
