package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.utils.Packet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

public final class ConfiguratorInteractionReport implements Packet<ConfiguratorInteractionReport> {

    public static final Type<ConfiguratorInteractionReport> TYPE = new Type<>(
        ResourceLocation.parse(CirculationFlowNetworks.MOD_ID + ":configurator_interaction_report")
    );
    private static final int RANKING_LIMIT = 10;
    private boolean hasMachine;
    private long machinePosition;
    private String machineInput = "";
    private String machineOutput = "";
    private boolean hasNetwork;
    private long[] inputPositions = new long[0];
    private String[] inputValues = new String[0];
    private long[] outputPositions = new long[0];
    private String[] outputValues = new String[0];

    public ConfiguratorInteractionReport() {
    }

    public ConfiguratorInteractionReport(boolean hasMachine, long machinePosition,
                                         String machineInput, String machineOutput,
                                         boolean hasNetwork,
                                         long[] inputPositions, String[] inputValues,
                                         long[] outputPositions, String[] outputValues) {
        this.hasMachine = hasMachine;
        this.machinePosition = machinePosition;
        this.machineInput = requireValue(machineInput);
        this.machineOutput = requireValue(machineOutput);
        this.hasNetwork = hasNetwork;
        this.inputPositions = copyPositions(inputPositions, inputValues, "input");
        this.inputValues = copyValues(inputValues);
        this.outputPositions = copyPositions(outputPositions, outputValues, "output");
        this.outputValues = copyValues(outputValues);
    }

    @Override
    public ConfiguratorInteractionReport decode(RegistryFriendlyByteBuf buf) {
        ConfiguratorInteractionReport message = new ConfiguratorInteractionReport();
        message.hasMachine = buf.readBoolean();
        message.machinePosition = buf.readLong();
        message.machineInput = buf.readUtf();
        message.machineOutput = buf.readUtf();
        message.hasNetwork = buf.readBoolean();
        message.inputPositions = readPositions(buf);
        message.inputValues = readValues(buf, message.inputPositions.length);
        message.outputPositions = readPositions(buf);
        message.outputValues = readValues(buf, message.outputPositions.length);
        return message;
    }

    @Override
    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(hasMachine);
        buf.writeLong(machinePosition);
        buf.writeUtf(requireValue(machineInput));
        buf.writeUtf(requireValue(machineOutput));
        buf.writeBoolean(hasNetwork);
        writeRanking(buf, inputPositions, inputValues, "input");
        writeRanking(buf, outputPositions, outputValues, "output");
    }

    @Override
    public void handle(ConfiguratorInteractionReport message, IPayloadContext context) {
        context.enqueueWork(() -> display(message));
    }

    private static void display(ConfiguratorInteractionReport message) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        if (message.hasMachine) {
            BlockPos pos = BlockPos.of(message.machinePosition);
            player.displayClientMessage(Component.translatable(
                "item.circulation_networks.circulation_configurator.interaction.machine.title",
                resolveName(player, level, pos), pos.getX(), pos.getY(), pos.getZ()
            ), false);
            player.displayClientMessage(Component.translatable(
                "item.circulation_networks.circulation_configurator.interaction.machine.input",
                message.machineInput
            ), false);
            player.displayClientMessage(Component.translatable(
                "item.circulation_networks.circulation_configurator.interaction.machine.output",
                message.machineOutput
            ), false);
        }
        if (message.hasNetwork) {
            displayRanking(player, level,
                "item.circulation_networks.circulation_configurator.interaction.network.input_top",
                message.inputPositions, message.inputValues);
            displayRanking(player, level,
                "item.circulation_networks.circulation_configurator.interaction.network.output_top",
                message.outputPositions, message.outputValues);
        }
    }

    private static void displayRanking(LocalPlayer player, Level level, String titleKey,
                                       long[] positions, String[] values) {
        player.displayClientMessage(Component.translatable(titleKey), false);
        if (positions.length == 0) {
            player.displayClientMessage(Component.translatable(
                "item.circulation_networks.circulation_configurator.interaction.network.empty"
            ), false);
            return;
        }
        for (int index = 0; index < positions.length; index++) {
            BlockPos pos = BlockPos.of(positions[index]);
            player.displayClientMessage(Component.translatable(
                "item.circulation_networks.circulation_configurator.interaction.network.entry",
                index + 1, resolveName(player, level, pos), pos.getX(), pos.getY(), pos.getZ(), values[index]
            ), false);
        }
    }

    private static Component resolveName(LocalPlayer player, Level level, BlockPos pos) {
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
            return Component.literal(pos.toShortString());
        }
        BlockState state = level.getBlockState(pos);
        try {
            BlockHitResult target = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            ItemStack picked = state.getBlock().getCloneItemStack(state, target, level, pos, player);
            if (!picked.isEmpty()) {
                return picked.getHoverName();
            }
        } catch (RuntimeException exception) {
            CirculationFlowNetworks.LOGGER.warn("Failed to resolve picked block name at {}", pos, exception);
        }
        return state.getBlock().getName();
    }

    private static long[] readPositions(RegistryFriendlyByteBuf buf) {
        int count = buf.readUnsignedByte();
        if (count > RANKING_LIMIT || count > buf.readableBytes() / Long.BYTES) {
            throw new IllegalArgumentException("Invalid configurator interaction ranking size: " + count);
        }
        long[] positions = new long[count];
        for (int index = 0; index < count; index++) {
            positions[index] = buf.readLong();
        }
        return positions;
    }

    private static String[] readValues(RegistryFriendlyByteBuf buf, int count) {
        String[] values = new String[count];
        for (int index = 0; index < count; index++) {
            values[index] = requireValue(buf.readUtf());
        }
        return values;
    }

    private static void writeRanking(RegistryFriendlyByteBuf buf, long[] positions,
                                     String[] values, String name) {
        long[] checkedPositions = copyPositions(positions, values, name);
        buf.writeByte(checkedPositions.length);
        for (long position : checkedPositions) {
            buf.writeLong(position);
        }
        for (String value : values) {
            buf.writeUtf(requireValue(value));
        }
    }

    private static long[] copyPositions(long[] positions, String[] values, String name) {
        Objects.requireNonNull(positions, name + " positions");
        Objects.requireNonNull(values, name + " values");
        if (positions.length != values.length || positions.length > RANKING_LIMIT) {
            throw new IllegalArgumentException("Invalid " + name + " interaction ranking size");
        }
        return Arrays.copyOf(positions, positions.length);
    }

    private static String[] copyValues(String[] values) {
        String[] copy = Arrays.copyOf(Objects.requireNonNull(values, "values"), values.length);
        for (String value : copy) {
            requireValue(value);
        }
        return copy;
    }

    private static String requireValue(String value) {
        return Objects.requireNonNull(value, "interaction value");
    }

    @NotNull
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
