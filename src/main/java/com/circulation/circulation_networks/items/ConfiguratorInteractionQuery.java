package com.circulation.circulation_networks.items;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Objects;
import java.util.function.LongPredicate;

/** Builds owned, fixed-capacity snapshots for configurator interaction chat output. */
final class ConfiguratorInteractionQuery {

    static final int RANKING_LIMIT = 10;

    private ConfiguratorInteractionQuery() {
    }

    static MachineSnapshot snapshotMachine(EnergyMachineManager.Interaction interaction) {
        Objects.requireNonNull(interaction, "interaction");
        EnergyAmount input = interaction.getInput();
        try {
            return new MachineSnapshot(input, interaction.getOutput());
        } catch (RuntimeException | Error failure) {
            recycle(input);
            throw failure;
        }
    }

    static GridSnapshot snapshotGrid(IGrid grid, LongPredicate positionFilter) {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(positionFilter, "positionFilter");
        ObjectArrayList<RankedInteraction> inputs = new ObjectArrayList<>(RANKING_LIMIT);
        ObjectArrayList<RankedInteraction> outputs = new ObjectArrayList<>(RANKING_LIMIT);
        try {
            for (Long2ObjectMap.Entry<EnergyMachineManager.Interaction> entry
                : grid.getMachineInteractions().long2ObjectEntrySet()) {
                if (!positionFilter.test(entry.getLongKey())) {
                    continue;
                }
                retain(inputs, new RankedInteraction(entry.getLongKey(), entry.getValue().getInput()));
                retain(outputs, new RankedInteraction(entry.getLongKey(), entry.getValue().getOutput()));
            }
            return new GridSnapshot(inputs, outputs);
        } catch (RuntimeException | Error failure) {
            recycleAll(inputs);
            recycleAll(outputs);
            throw failure;
        }
    }

    static void retain(List<RankedInteraction> ranking, RankedInteraction candidate) {
        Objects.requireNonNull(ranking, "ranking");
        Objects.requireNonNull(candidate, "candidate");
        if (ranking.size() > RANKING_LIMIT) {
            candidate.close();
            throw new IllegalStateException("Configurator interaction ranking exceeds fixed capacity");
        }
        try {
            if (!candidate.amount().isPositive()) {
                candidate.close();
                return;
            }

            int insertion = 0;
            while (insertion < ranking.size() && compare(ranking.get(insertion), candidate) <= 0) {
                insertion++;
            }
            if (insertion >= RANKING_LIMIT) {
                candidate.close();
                return;
            }
            if (ranking.size() < RANKING_LIMIT) {
                ranking.add(insertion, candidate);
                return;
            }

            RankedInteraction evicted = ranking.get(RANKING_LIMIT - 1);
            for (int index = RANKING_LIMIT - 1; index > insertion; index--) {
                ranking.set(index, ranking.get(index - 1));
            }
            ranking.set(insertion, candidate);
            evicted.close();
        } catch (RuntimeException | Error failure) {
            if (!candidate.closed) {
                candidate.close();
            }
            throw failure;
        }
    }

    private static int compare(RankedInteraction left, RankedInteraction right) {
        int amountOrder = right.amount().compareTo(left.amount());
        if (amountOrder != 0) {
            return amountOrder;
        }
        int xOrder = Integer.compare(left.x, right.x);
        if (xOrder != 0) {
            return xOrder;
        }
        int yOrder = Integer.compare(left.y, right.y);
        return yOrder != 0 ? yOrder : Integer.compare(left.z, right.z);
    }

    static BlockPos unpack(long packedPosition) {
        return BlockPos.of(packedPosition);
    }

    private static void recycleAll(List<RankedInteraction> ranking) {
        for (RankedInteraction entry : ranking) {
            entry.close();
        }
        ranking.clear();
    }

    private static void recycle(EnergyAmount amount) {
        if (amount != EnergyAmounts.ZERO) {
            amount.recycle();
        }
    }

    static final class MachineSnapshot implements AutoCloseable {
        private final EnergyAmount input;
        private final EnergyAmount output;
        private boolean closed;

        private MachineSnapshot(EnergyAmount input, EnergyAmount output) {
            this.input = Objects.requireNonNull(input, "input");
            this.output = Objects.requireNonNull(output, "output");
        }

        EnergyAmount input() {
            requireOpen();
            return input;
        }

        EnergyAmount output() {
            requireOpen();
            return output;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            recycle(input);
            recycle(output);
        }

        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException("Machine interaction snapshot is closed");
            }
        }
    }

    static final class GridSnapshot implements AutoCloseable {
        private final ObjectArrayList<RankedInteraction> inputs;
        private final ObjectArrayList<RankedInteraction> outputs;
        private boolean closed;

        private GridSnapshot(ObjectArrayList<RankedInteraction> inputs,
                             ObjectArrayList<RankedInteraction> outputs) {
            this.inputs = inputs;
            this.outputs = outputs;
        }

        List<RankedInteraction> inputs() {
            requireOpen();
            return inputs;
        }

        List<RankedInteraction> outputs() {
            requireOpen();
            return outputs;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            recycleAll(inputs);
            recycleAll(outputs);
        }

        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException("Grid interaction snapshot is closed");
            }
        }
    }

    static final class RankedInteraction implements AutoCloseable {
        private final long packedPosition;
        private final EnergyAmount amount;
        private final int x;
        private final int y;
        private final int z;
        private boolean closed;

        RankedInteraction(long packedPosition, EnergyAmount amount) {
            this.packedPosition = packedPosition;
            this.amount = Objects.requireNonNull(amount, "amount");
            BlockPos position = unpack(packedPosition);
            x = position.getX();
            y = position.getY();
            z = position.getZ();
        }

        long packedPosition() {
            requireOpen();
            return packedPosition;
        }

        EnergyAmount amount() {
            requireOpen();
            return amount;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            recycle(amount);
        }

        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException("Ranked interaction snapshot is closed");
            }
        }
    }
}
