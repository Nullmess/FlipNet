package com.flipnet.bridge;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.CRC32;

// Encode et decode le protocole de gestion de fichiers Android <-> Flipper.
final class FlipperFileProtocol {
    static final String VERSION = "FNFS2";
    static final String PREFIX = VERSION + "|";
    static final int CHUNK_SIZE = 512;

    private FlipperFileProtocol() {
    }

    static byte[] request(
            long id,
            String command,
            String path,
            String argument,
            long value,
            byte[] data) {
        String payload = VERSION + "|REQ|" + unsigned(id) + "|" + command + "|"
                + hex(path == null ? new byte[0] : path.getBytes(StandardCharsets.UTF_8)) + "|"
                + hex(argument == null ? new byte[0] : argument.getBytes(StandardCharsets.UTF_8)) + "|"
                + value + "|" + hex(data == null ? new byte[0] : data);
        CRC32 crc = new CRC32();
        crc.update(payload.getBytes(StandardCharsets.US_ASCII));
        return (payload + "|" + String.format(Locale.ROOT, "%08X", crc.getValue()) + "\n")
                .getBytes(StandardCharsets.US_ASCII);
    }

    static String extractFrame(String rawLine) {
        if (rawLine == null) return null;
        String line = rawLine.trim();
        int start = line.indexOf(PREFIX);
        if (start < 0) return null;
        line = line.substring(start).trim();
        int newline = line.indexOf('\n');
        if (newline >= 0) line = line.substring(0, newline).trim();
        return line;
    }

    static boolean isRequest(String frame) {
        return frame != null && frame.startsWith(VERSION + "|REQ|");
    }

    static boolean isResponse(String frame) {
        return frame != null && frame.startsWith(VERSION + "|RES|");
    }

    static Long responseId(String frame) {
        if (!isResponse(frame)) return null;
        String[] fields = frame.split("\\|", 5);
        if (fields.length < 4) return null;
        try {
            return Long.parseUnsignedLong(fields[2]);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    static Response parseResponse(String rawLine) {
        String line = extractFrame(rawLine);
        if (!isResponse(line)) {
            throw new IllegalArgumentException("Not a file response");
        }
        int separator = line.lastIndexOf('|');
        if (separator <= 0 || separator + 9 != line.length()) {
            throw new IllegalArgumentException("Invalid file frame checksum");
        }
        String payload = line.substring(0, separator);
        String checksum = line.substring(separator + 1);
        CRC32 crc = new CRC32();
        crc.update(payload.getBytes(StandardCharsets.US_ASCII));
        String expected = String.format(Locale.ROOT, "%08X", crc.getValue());
        if (!expected.equalsIgnoreCase(checksum)) {
            throw new IllegalArgumentException("File frame checksum mismatch");
        }
        String[] fields = payload.split("\\|", -1);
        if (fields.length != 8 || !VERSION.equals(fields[0]) || !"RES".equals(fields[1])) {
            throw new IllegalArgumentException("Malformed file response");
        }
        long id = Long.parseUnsignedLong(fields[2]);
        String status = fields[3];
        if (!"OK".equals(status) && !"ERR".equals(status)) {
            throw new IllegalArgumentException("Unknown file response status");
        }
        String command = fields[4];
        long value1 = Long.parseUnsignedLong(fields[5]);
        long value2 = Long.parseUnsignedLong(fields[6]);
        byte[] data = unhex(fields[7]);
        return new Response(id, status, command, value1, value2, data);
    }

    static String utf8(byte[] data) {
        return new String(data == null ? new byte[0] : data, StandardCharsets.UTF_8);
    }

    private static String unsigned(long value) {
        return Long.toUnsignedString(value & 0xFFFFFFFFL);
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.ROOT, "%02X", value & 0xFF));
        }
        return result.toString();
    }

    private static byte[] unhex(String value) {
        if ((value.length() & 1) != 0) {
            throw new IllegalArgumentException("Invalid hex payload");
        }
        byte[] result = new byte[value.length() / 2];
        for (int index = 0; index < result.length; index++) {
            int high = Character.digit(value.charAt(index * 2), 16);
            int low = Character.digit(value.charAt(index * 2 + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("Invalid hex payload");
            result[index] = (byte) ((high << 4) | low);
        }
        return result;
    }

    static final class Response {
        final long id;
        final String status;
        final String command;
        final long value1;
        final long value2;
        final byte[] data;

        Response(long id, String status, String command, long value1, long value2, byte[] data) {
            this.id = id;
            this.status = status;
            this.command = command;
            this.value1 = value1;
            this.value2 = value2;
            this.data = data;
        }

        boolean ok() {
            return "OK".equals(status);
        }
    }
}
