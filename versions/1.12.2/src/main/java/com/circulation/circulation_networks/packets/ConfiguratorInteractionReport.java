package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.utils.Packet;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

public final class ConfiguratorInteractionReport implements Packet<ConfiguratorInteractionReport> {

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
    public void fromBytes(ByteBuf buf) {
        hasMachine = buf.readBoolean();
        machinePosition = buf.readLong();
        machineInput = ByteBufUtils.readUTF8String(buf);
        machineOutput = ByteBufUtils.readUTF8String(buf);
        hasNetwork = buf.readBoolean();
        inputPositions = readPositions(buf);
        inputValues = readValues(buf, inputPositions.length);
        outputPositions = readPositions(buf);
        outputValues = readValues(buf, outputPositions.length);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(hasMachine);
        buf.writeLong(machinePosition);
        ByteBufUtils.writeUTF8String(buf, requireValue(machineInput));
        ByteBufUtils.writeUTF8String(buf, requireValue(machineOutput));
        buf.writeBoolean(hasNetwork);
        writeRanking(buf, inputPositions, inputValues, "input");
        writeRanking(buf, outputPositions, outputValues, "output");
    }

    @Override
    public @Nullable IMessage onMessage(ConfiguratorInteractionReport message, MessageContext context) {
        Minecraft.getMinecraft().addScheduledTask(() -> display(message));
        return null;
    }

    private static void display(ConfiguratorInteractionReport message) {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.player;
        World world = minecraft.world;
        if (player == null || world == null) {
            return;
        }
        if (message.hasMachine) {
            BlockPos pos = BlockPos.fromLong(message.machinePosition);
            player.sendMessage(new TextComponentTranslation(
                "item.circulation_networks.circulation_configurator.interaction.machine.title",
                resolveName(player, world, pos), pos.getX(), pos.getY(), pos.getZ()
            ));
            player.sendMessage(new TextComponentTranslation(
                "item.circulation_networks.circulation_configurator.interaction.machine.input",
                message.machineInput
            ));
            player.sendMessage(new TextComponentTranslation(
                "item.circulation_networks.circulation_configurator.interaction.machine.output",
                message.machineOutput
            ));
        }
        if (message.hasNetwork) {
            displayRanking(player, world,
                "item.circulation_networks.circulation_configurator.interaction.network.input_top",
                message.inputPositions, message.inputValues);
            displayRanking(player, world,
                "item.circulation_networks.circulation_configurator.interaction.network.output_top",
                message.outputPositions, message.outputValues);
        }
    }

    private static void displayRanking(EntityPlayerSP player, World world, String titleKey,
                                       long[] positions, String[] values) {
        player.sendMessage(new TextComponentTranslation(titleKey));
        if (positions.length == 0) {
            player.sendMessage(new TextComponentTranslation(
                "item.circulation_networks.circulation_configurator.interaction.network.empty"
            ));
            return;
        }
        for (int index = 0; index < positions.length; index++) {
            BlockPos pos = BlockPos.fromLong(positions[index]);
            player.sendMessage(new TextComponentTranslation(
                "item.circulation_networks.circulation_configurator.interaction.network.entry",
                index + 1, resolveName(player, world, pos), pos.getX(), pos.getY(), pos.getZ(), values[index]
            ));
        }
    }

    private static ITextComponent resolveName(EntityPlayerSP player, World world, BlockPos pos) {
        if (world.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4) == null) {
            return new TextComponentString(pos.toString());
        }
        IBlockState state = world.getBlockState(pos);
        try {
            Vec3d center = new Vec3d(pos).add(0.5D, 0.5D, 0.5D);
            RayTraceResult target = new RayTraceResult(center, EnumFacing.UP, pos);
            ItemStack picked = state.getBlock().getPickBlock(state, target, world, pos, player);
            if (!picked.isEmpty()) {
                return new TextComponentString(picked.getDisplayName());
            }
        } catch (RuntimeException exception) {
            CirculationFlowNetworks.LOGGER.warn("Failed to resolve picked block name at {}", pos, exception);
        }
        return new TextComponentString(state.getBlock().getLocalizedName());
    }

    private static long[] readPositions(ByteBuf buf) {
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

    private static String[] readValues(ByteBuf buf, int count) {
        String[] values = new String[count];
        for (int index = 0; index < count; index++) {
            values[index] = requireValue(ByteBufUtils.readUTF8String(buf));
        }
        return values;
    }

    private static void writeRanking(ByteBuf buf, long[] positions, String[] values, String name) {
        long[] checkedPositions = copyPositions(positions, values, name);
        buf.writeByte(checkedPositions.length);
        for (long position : checkedPositions) {
            buf.writeLong(position);
        }
        for (String value : values) {
            ByteBufUtils.writeUTF8String(buf, requireValue(value));
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
}
