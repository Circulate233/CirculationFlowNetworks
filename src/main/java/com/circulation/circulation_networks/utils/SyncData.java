package com.circulation.circulation_networks.utils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Objects;

public class SyncData {
    private final Object source;
    private final Field field;
    private final int channel;
    private final SyncType syncType;
    private final SyncUpdateCallback updateCallback;
    private long numericVersion = 0L;
    private String stringVersion = null;
    private byte[] bytesVersion = null;
    private byte[] bytesSourceVersion = null;

    public SyncData(Object container, Field field, GuiSync annotation, SyncUpdateCallback updateCallback) {
        this.source = container;
        this.field = field;
        this.channel = annotation.value();
        this.syncType = determineSyncType(field);
        this.updateCallback = updateCallback;
    }

    private static SyncType determineSyncType(Field field) {
        Class<?> type = field.getType();
        if (type == String.class) {
            return SyncType.STRING;
        } else if (type == byte[].class) {
            return SyncType.BYTES;
        } else if (type == Integer.TYPE || type == Integer.class) {
            return SyncType.INT;
        } else if (type == Long.TYPE || type == Long.class) {
            return SyncType.LONG;
        } else if (type == Byte.TYPE || type == Byte.class) {
            return SyncType.BYTE;
        } else if (type == Short.TYPE || type == Short.class) {
            return SyncType.SHORT;
        } else if (type == Boolean.TYPE || type == Boolean.class) {
            return SyncType.BOOLEAN;
        } else if (type.isEnum()) {
            return SyncType.ENUM;
        }
        return SyncType.INT;
    }

    private static byte[] copyBytes(byte[] value) {
        return value == null ? value : Arrays.copyOf(value, value.length);
    }

    public int getChannel() {
        return channel;
    }

    public void init() {
        numericVersion = 0L;
        stringVersion = null;
        bytesVersion = null;
        bytesSourceVersion = null;
    }

    public void tick(SyncSender sender) {
        try {
            Object val = this.field.get(this.source);
            boolean needsSync;
            if (syncType == SyncType.STRING) {
                needsSync = !Objects.equals(val, stringVersion);
            } else if (syncType == SyncType.BYTES) {
                byte[] bytes = (byte[]) val;
                if (bytes == bytesSourceVersion) {
                    needsSync = false;
                } else {
                    needsSync = !Arrays.equals(bytes, bytesVersion);
                }
            } else {
                long numeric = extractNumericValue(val);
                needsSync = numeric != numericVersion;
            }

            if (needsSync) {
                try {
                    this.send(sender, val);
                    updateCachedVersion(val);
                } catch (Exception ignored) {
                }
            }
        } catch (IllegalArgumentException | IllegalAccessException ignored) {
        }
    }

    private long extractNumericValue(Object val) {
        if (val == null) return 0L;
        return switch (syncType) {
            case INT -> (Integer) val;
            case LONG -> (Long) val;
            case BYTE -> (Byte) val;
            case SHORT -> (Short) val;
            case BOOLEAN -> ((Boolean) val) ? 1L : 0L;
            case ENUM -> ((Enum<?>) val).ordinal();
            default -> 0L;
        };
    }

    private void updateCachedVersion(Object val) {
        switch (syncType) {
            case STRING -> stringVersion = (String) val;
            case BYTES -> {
                bytesSourceVersion = (byte[]) val;
                bytesVersion = copyBytes((byte[]) val);
            }
            case INT, LONG, BYTE, SHORT, BOOLEAN, ENUM -> numericVersion = extractNumericValue(val);
        }
    }

    private void send(SyncSender sender, Object val) {
        switch (syncType) {
            case STRING -> sender.sendString(channel, (String) val);
            case BYTES -> sender.sendBytes(channel, (byte[]) val);
            case ENUM -> sender.sendInt(channel, ((Enum<?>) val).ordinal());
            case BOOLEAN -> sender.sendInt(channel, ((Boolean) val) ? 1 : 0);
            case INT -> sender.sendInt(channel, (Integer) val);
            case LONG -> sender.sendLong(channel, (Long) val);
            case BYTE -> sender.sendByte(channel, (Byte) val);
            case SHORT -> sender.sendShort(channel, (Short) val);
        }
    }

    public void update(Object val) {
        try {
            final Object oldValue = this.field.get(this.source);
            switch (syncType) {
                case STRING -> this.field.set(this.source, val);
                case BYTES -> this.field.set(this.source, copyBytes((byte[]) val));
                case INT -> this.field.set(this.source, ((Number) val).intValue());
                case LONG -> this.field.set(this.source, ((Number) val).longValue());
                case BYTE -> this.field.set(this.source, ((Number) val).byteValue());
                case SHORT -> this.field.set(this.source, ((Number) val).shortValue());
                case BOOLEAN -> this.field.set(this.source, ((Number) val).longValue() == 1L);
                case ENUM -> updateEnum(((Number) val).longValue());
            }
            if (updateCallback != null) {
                updateCallback.onUpdate(this.field.getName(), oldValue, this.field.get(this.source));
            }
        } catch (IllegalArgumentException | IllegalAccessException e) {
            // Ignore invalid sync payloads.
        }
    }

    @SuppressWarnings("unchecked")
    private <E extends Enum<E>> void updateEnum(long ordinal) throws IllegalAccessException {
        Class<E> enumType = (Class<E>) this.field.getType();
        E[] constants = enumType.getEnumConstants();
        int idx = (int) ordinal;
        if (idx >= 0 && idx < constants.length) {
            this.field.set(this.source, constants[idx]);
        }
    }

    enum SyncType {
        STRING, BYTES, INT, LONG, BYTE, SHORT, BOOLEAN, ENUM
    }

    @FunctionalInterface
    public interface SyncUpdateCallback {
        void onUpdate(String fieldName, Object oldValue, Object newValue);
    }
}
