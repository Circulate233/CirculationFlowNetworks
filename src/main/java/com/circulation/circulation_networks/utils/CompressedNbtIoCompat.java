package com.circulation.circulation_networks.utils;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public final class CompressedNbtIoCompat {

    private CompressedNbtIoCompat() {
    }

    public static @Nullable CompoundTag tryReadCompressedNbt(File file, String context) {
        try {
            return readCompressedNbt(file);
        } catch (IOException e) {
            CirculationFlowNetworks.LOGGER.warn("Failed to read {} from {}", context, file.getAbsolutePath(), e);
            return null;
        }
    }

    public static boolean tryWriteCompressedNbt(CompoundTag nbt, File file, String context, Object lock) {
        try {
            writeCompressedNbt(nbt, file, lock);
            return true;
        } catch (IOException e) {
            CirculationFlowNetworks.LOGGER.warn("Failed to write {} to {}", context, file.getAbsolutePath(), e);
            file.delete();
            return false;
        }
    }

    public static CompoundTag readCompressedNbt(File file) throws IOException {
        return NbtIo.readCompressed(file.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
    }

    public static void writeCompressedNbt(CompoundTag nbt, File file, Object lock) throws IOException {
        synchronized (lock) {
            NbtIo.writeCompressed(nbt, file.toPath());
        }
    }
}
