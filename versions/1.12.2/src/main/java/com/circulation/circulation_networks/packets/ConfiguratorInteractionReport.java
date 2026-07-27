package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.utils.Packet;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

import static com.circulation.circulation_networks.CirculationFlowNetworks.proxy;

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
        proxy.displayConfiguratorInteraction(message);
        return null;
    }

    public boolean hasMachine() {
        return hasMachine;
    }

    public long getMachinePosition() {
        return machinePosition;
    }

    public String getMachineInput() {
        return machineInput;
    }

    public String getMachineOutput() {
        return machineOutput;
    }

    public boolean hasNetwork() {
        return hasNetwork;
    }

    public int getInputCount() {
        return inputPositions.length;
    }

    public long getInputPosition(int index) {
        return inputPositions[index];
    }

    public String getInputValue(int index) {
        return inputValues[index];
    }

    public int getOutputCount() {
        return outputPositions.length;
    }

    public long getOutputPosition(int index) {
        return outputPositions[index];
    }

    public String getOutputValue(int index) {
        return outputValues[index];
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
