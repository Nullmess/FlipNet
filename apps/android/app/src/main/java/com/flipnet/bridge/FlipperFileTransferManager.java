package com.flipnet.bridge;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

// Orchestre les commandes et transferts de fichiers avec le Flipper.
final class FlipperFileTransferManager {
    static final String ACTION_COMMAND = "com.flipnet.bridge.FILES_COMMAND";
    static final String ACTION_EVENT = "com.flipnet.bridge.FILES_EVENT";

    static final String EXTRA_COMMAND = "command";
    static final String EXTRA_PATH = "path";
    static final String EXTRA_ARGUMENT = "argument";
    static final String EXTRA_URI = "uri";
    static final String EXTRA_KIND = "kind";
    static final String EXTRA_DATA = "data";
    static final String EXTRA_MESSAGE = "message";
    static final String EXTRA_CURRENT = "current";
    static final String EXTRA_TOTAL = "total";

    static final String COMMAND_LIST = "LIST";
    static final String COMMAND_SPACE = "SPACE";
    static final String COMMAND_MKDIR = "MKDIR";
    static final String COMMAND_DELETE = "DELETE";
    static final String COMMAND_RENAME = "RENAME";
    static final String COMMAND_DOWNLOAD = "DOWNLOAD";
    static final String COMMAND_UPLOAD = "UPLOAD";
    static final String COMMAND_CANCEL = "CANCEL";

    static final String EVENT_LIST = "LIST";
    static final String EVENT_SPACE = "SPACE";
    static final String EVENT_DONE = "DONE";
    static final String EVENT_PROGRESS = "PROGRESS";
    static final String EVENT_ERROR = "ERROR";
    static final String EVENT_CANCELLED = "CANCELLED";

    private static final String TAG = "FlipNetFiles";
    private static final long MAX_FILE_SIZE = 0xFFFFFFFFL;
    private static final long REQUEST_TIMEOUT_MS = 20000L;
    private static final int TRANSFER_RETRY_MAX = 3;

    interface Sender {
        boolean send(byte[] frame);
    }

    private final Context context;
    private final Sender sender;
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private final Map<Long, Pending> pending = new HashMap<>();
    private final Map<Long, Runnable> timeouts = new HashMap<>();
    private long nextId = System.currentTimeMillis() & 0xFFFFFFFFL;
    private Pending activeTransfer;

    FlipperFileTransferManager(Context context, Sender sender) {
        this.context = context.getApplicationContext();
        this.sender = sender;
    }

    synchronized void handleCommand(Intent intent) {
        if (intent == null) return;
        String command = intent.getStringExtra(EXTRA_COMMAND);
        String path = safe(intent.getStringExtra(EXTRA_PATH));
        String argument = safe(intent.getStringExtra(EXTRA_ARGUMENT));
        String uriText = safe(intent.getStringExtra(EXTRA_URI));
        if (command == null) return;
        switch (command) {
            case COMMAND_LIST:
                requestList(path);
                break;
            case COMMAND_SPACE:
                sendSimple("SPACE", "", "", 0L, EVENT_SPACE, "");
                break;
            case COMMAND_MKDIR:
                sendSimple("MKDIR", path, "", 0L, EVENT_DONE, "Folder created");
                break;
            case COMMAND_DELETE:
                sendSimple("DELETE", path, "", 0L, EVENT_DONE, "Deleted");
                break;
            case COMMAND_RENAME:
                sendSimple("RENAME", path, argument, 0L, EVENT_DONE, "Renamed");
                break;
            case COMMAND_DOWNLOAD:
                startDownload(path, uriText);
                break;
            case COMMAND_UPLOAD:
                startUpload(path, uriText);
                break;
            case COMMAND_CANCEL:
                cancelTransfer("Transfer cancelled");
                break;
            default:
                broadcast(EVENT_ERROR, path, "", "Unknown file command", 0L, 0L);
                break;
        }
    }

    synchronized boolean handleResponseLine(String rawLine) {
        String frame = FlipperFileProtocol.extractFrame(rawLine);
        if (frame == null) return false;

        // Certains transports/firmwares peuvent renvoyer l'entree envoyee. Un echo REQ
        // n'est pas une erreur et ne doit jamais polluer l'interface Files.
        if (FlipperFileProtocol.isRequest(frame)) {
            Log.d(TAG, "Ignoring echoed file request");
            return true;
        }
        if (!FlipperFileProtocol.isResponse(frame)) {
            Log.d(TAG, "Ignoring unknown file frame");
            return true;
        }

        final FlipperFileProtocol.Response response;
        try {
            response = FlipperFileProtocol.parseResponse(frame);
        } catch (RuntimeException error) {
            Long responseId = FlipperFileProtocol.responseId(frame);
            Log.w(TAG, "Corrupted file response", error);
            // Ne transforme pas un fragment/echo parasite en avalanche de Toasts. La
            // requete reste active et le timeout donnera un diagnostic exploitable.
            if (responseId != null && !pending.containsKey(responseId)) {
                Log.d(TAG, "Malformed response does not match a pending file request");
            }
            return true;
        }

        Pending operation = takePending(response.id);
        if (operation == null) {
            Log.d(TAG, "Ignoring stale file response id=" + response.id);
            return true;
        }
        if (!response.ok()) {
            fail(operation, friendlyRemoteError(FlipperFileProtocol.utf8(response.data)));
            return true;
        }
        try {
            handleSuccess(operation, response);
        } catch (IOException | RuntimeException error) {
            Log.e(TAG, "File transfer failed", error);
            fail(operation, error.getMessage() == null ? "File transfer failed" : error.getMessage());
        }
        return true;
    }

    synchronized void onDisconnected() {
        clearPending();
        Pending transfer = activeTransfer;
        if (transfer == null) return;
        closeStreams(transfer);
        transfer.waitingReconnect = true;
        broadcast(EVENT_PROGRESS, transfer.path, transfer.kind,
                "Connection lost. Transfer will resume automatically.",
                transfer.offset, transfer.total);
    }

    synchronized void onConnected() {
        Pending transfer = activeTransfer;
        if (transfer == null || !transfer.waitingReconnect) return;
        transfer.waitingReconnect = false;
        try {
            if (COMMAND_UPLOAD.equals(transfer.kind)) {
                InputStream input = context.getContentResolver().openInputStream(transfer.uri);
                if (input == null) throw new IOException("Cannot reopen Android source");
                transfer.input = input;
                transfer.offset = 0L;
                broadcast(EVENT_PROGRESS, transfer.path, "Upload",
                        "Reconnected. Restarting current file safely…", 0L, transfer.total);
                sendRequest(transfer, "WRITE_BEGIN", transfer.path, "", transfer.total, null);
                return;
            }
            if (COMMAND_DOWNLOAD.equals(transfer.kind)) {
                OutputStream output = context.getContentResolver().openOutputStream(transfer.uri, "w");
                if (output == null) throw new IOException("Cannot reopen Android destination");
                transfer.output = output;
                transfer.offset = 0L;
                broadcast(EVENT_PROGRESS, transfer.path, "Download",
                        "Reconnected. Restarting current file safely…", 0L, transfer.total);
                sendRequest(transfer, "READ", transfer.path, "", 0L, null);
            }
        } catch (IOException | SecurityException error) {
            fail(transfer, message(error, "Cannot resume file transfer"));
        }
    }

    synchronized boolean isTransferActive() {
        return activeTransfer != null;
    }

    synchronized void close() {
        cancelTransfer("Transfer stopped");
        clearPending();
    }

    private void requestList(String path) {
        removePendingKind(EVENT_LIST);
        Pending operation = new Pending(EVENT_LIST, path);
        operation.buffer = new StringBuilder();
        sendRequest(operation, "LIST", path, "", 0L, null);
    }

    private void sendSimple(
            String command,
            String path,
            String argument,
            long value,
            String event,
            String message) {
        if (EVENT_SPACE.equals(event)) removePendingKind(EVENT_SPACE);
        Pending operation = new Pending(event, path);
        operation.message = message;
        sendRequest(operation, command, path, argument, value, null);
    }

    private void startDownload(String path, String uriText) {
        if (!ensureTransferSlot(path)) return;
        Uri uri = parseUri(uriText);
        if (uri == null) {
            broadcast(EVENT_ERROR, path, "", "Invalid Android destination", 0L, 0L);
            return;
        }
        try {
            OutputStream output = context.getContentResolver().openOutputStream(uri, "w");
            if (output == null) throw new IOException("Cannot open Android destination");
            Pending operation = new Pending(COMMAND_DOWNLOAD, path);
            operation.output = output;
            operation.uri = uri;
            operation.offset = 0L;
            activeTransfer = operation;
            broadcast(EVENT_PROGRESS, path, "Download", "Starting download", 0L, 0L);
            sendRequest(operation, "READ", path, "", 0L, null);
        } catch (IOException | SecurityException error) {
            broadcast(EVENT_ERROR, path, "", message(error, "Cannot open Android destination"), 0L, 0L);
        }
    }

    private void startUpload(String path, String uriText) {
        if (!ensureTransferSlot(path)) return;
        Uri uri = parseUri(uriText);
        if (uri == null) {
            broadcast(EVENT_ERROR, path, "", "Invalid Android source", 0L, 0L);
            return;
        }
        try {
            long size = contentSize(context.getContentResolver(), uri);
            if (size < 0L) throw new IOException("Android provider did not report the file size");
            if (size > MAX_FILE_SIZE) throw new IOException("File is larger than 4 GiB");
            InputStream input = context.getContentResolver().openInputStream(uri);
            if (input == null) throw new IOException("Cannot open Android source");
            Pending operation = new Pending(COMMAND_UPLOAD, path);
            operation.input = input;
            operation.uri = uri;
            operation.total = size;
            operation.offset = 0L;
            activeTransfer = operation;
            broadcast(EVENT_PROGRESS, path, "Upload", "Starting upload", 0L, size);
            sendRequest(operation, "WRITE_BEGIN", path, "", size, null);
        } catch (IOException | SecurityException error) {
            broadcast(EVENT_ERROR, path, "", message(error, "Cannot open Android source"), 0L, 0L);
        }
    }

    private boolean ensureTransferSlot(String path) {
        if (activeTransfer == null) return true;
        broadcast(EVENT_ERROR, path, "", "Another file transfer is already running",
                activeTransfer.offset, activeTransfer.total);
        return false;
    }

    private void handleSuccess(Pending operation, FlipperFileProtocol.Response response)
            throws IOException {
        if (EVENT_LIST.equals(operation.kind)) {
            operation.buffer.append(FlipperFileProtocol.utf8(response.data));
            if (response.value2 == 0L) {
                sendRequest(operation, "LIST", operation.path, "", response.value1, null);
            } else {
                broadcast(EVENT_LIST, operation.path, operation.buffer.toString(), "", 0L, 0L);
            }
            return;
        }
        if (EVENT_SPACE.equals(operation.kind)) {
            broadcast(EVENT_SPACE, "", FlipperFileProtocol.utf8(response.data), "", 0L, 0L);
            return;
        }
        if (COMMAND_DOWNLOAD.equals(operation.kind)) {
            handleDownloadChunk(operation, response);
            return;
        }
        if (COMMAND_UPLOAD.equals(operation.kind)) {
            handleUploadAck(operation, response);
            return;
        }
        broadcast(EVENT_DONE, operation.path, operation.kind,
                operation.message == null ? "Done" : operation.message, 0L, 0L);
    }

    private void handleDownloadChunk(Pending operation, FlipperFileProtocol.Response response)
            throws IOException {
        if (!"READ".equals(response.command)) throw new IOException("Unexpected download response");
        if (response.data.length > 0) operation.output.write(response.data);
        operation.offset = response.value1;
        operation.total = response.value2;
        broadcast(EVENT_PROGRESS, operation.path, "Download", "Downloading",
                operation.offset, operation.total);
        if (operation.offset >= operation.total) {
            operation.output.flush();
            completeTransfer(operation, "Download complete");
            return;
        }
        sendRequest(operation, "READ", operation.path, "", operation.offset, null);
    }

    private void handleUploadAck(Pending operation, FlipperFileProtocol.Response response)
            throws IOException {
        switch (response.command) {
            case "WRITE_BEGIN":
                sendNextUploadChunk(operation);
                break;
            case "WRITE":
                operation.offset = response.value1;
                broadcast(EVENT_PROGRESS, operation.path, "Upload", "Uploading",
                        operation.offset, operation.total);
                sendNextUploadChunk(operation);
                break;
            case "WRITE_END":
                completeTransfer(operation, "Upload complete");
                break;
            default:
                throw new IOException("Unexpected upload response");
        }
    }

    private void sendNextUploadChunk(Pending operation) throws IOException {
        byte[] buffer = new byte[FlipperFileProtocol.CHUNK_SIZE];
        int read;
        do {
            read = operation.input.read(buffer);
        } while (read == 0);
        if (read < 0) {
            if (operation.offset != operation.total) {
                throw new IOException("Android source size changed during upload");
            }
            sendRequest(operation, "WRITE_END", operation.path, "", operation.total, null);
            return;
        }
        byte[] chunk;
        if (read == buffer.length) {
            chunk = buffer;
        } else {
            chunk = new byte[read];
            System.arraycopy(buffer, 0, chunk, 0, read);
        }
        sendRequest(operation, "WRITE", operation.path, "", operation.offset, chunk);
    }

    private void completeTransfer(Pending operation, String message) {
        removePending(operation);
        closeStreams(operation);
        if (activeTransfer == operation) activeTransfer = null;
        broadcast(EVENT_DONE, operation.path, operation.kind, message,
                operation.offset, operation.total);
    }

    private void fail(Pending operation, String message) {
        removePending(operation);
        closeStreams(operation);
        if (activeTransfer == operation) activeTransfer = null;
        broadcast(EVENT_ERROR, operation.path, operation.kind,
                message == null || message.isEmpty() ? "File operation failed" : message,
                operation.offset, operation.total);
    }

    private void cancelTransfer(String message) {
        Pending transfer = activeTransfer;
        if (transfer == null) return;
        removePending(transfer);
        closeStreams(transfer);
        activeTransfer = null;
        broadcast(EVENT_CANCELLED, transfer.path, transfer.kind, message,
                transfer.offset, transfer.total);
    }

    private void sendRequest(
            Pending operation,
            String command,
            String path,
            String argument,
            long value,
            byte[] data) {
        operation.lastCommand = command;
        operation.lastArgument = argument;
        operation.lastValue = value;
        operation.lastData = data == null ? null : data.clone();
        operation.retryCount = 0;
        transmitLastRequest(operation);
    }

    private void transmitLastRequest(Pending operation) {
        long id = nextRequestId();
        pending.put(id, operation);
        scheduleTimeout(id, operation);
        byte[] frame = FlipperFileProtocol.request(id, operation.lastCommand, operation.path,
                operation.lastArgument, operation.lastValue, operation.lastData);
        if (!sender.send(frame)) {
            takePending(id);
            if (activeTransfer == operation) {
                closeStreams(operation);
                operation.waitingReconnect = true;
                broadcast(EVENT_PROGRESS, operation.path, operation.kind,
                        "Connection unavailable. Waiting to reconnect…",
                        operation.offset, operation.total);
            } else {
                fail(operation, "Flipper is not connected");
            }
        }
    }

    private void scheduleTimeout(long id, Pending operation) {
        Runnable timeout = () -> {
            synchronized (FlipperFileTransferManager.this) {
                Pending current = pending.get(id);
                if (current != operation) return;
                pending.remove(id);
                timeouts.remove(id);
                if (activeTransfer == operation
                        && retryableTransferCommand(operation.lastCommand)
                        && operation.retryCount < TRANSFER_RETRY_MAX) {
                    operation.retryCount++;
                    broadcast(EVENT_PROGRESS, operation.path, operation.kind,
                            "Flipper is busy. Retrying transfer… ("
                                    + operation.retryCount + "/" + TRANSFER_RETRY_MAX + ")",
                            operation.offset, operation.total);
                    transmitLastRequest(operation);
                    return;
                }
                String detail = "File transfer timed out. The connection stayed up but the Flipper did not acknowledge the block.";
                fail(operation, detail);
            }
        };
        timeouts.put(id, timeout);
        timeoutHandler.postDelayed(timeout, REQUEST_TIMEOUT_MS);
    }

    private static boolean retryableTransferCommand(String command) {
        return "WRITE_BEGIN".equals(command) || "WRITE".equals(command)
                || "WRITE_END".equals(command) || "READ".equals(command);
    }

    private Pending takePending(long id) {
        Runnable timeout = timeouts.remove(id);
        if (timeout != null) timeoutHandler.removeCallbacks(timeout);
        return pending.remove(id);
    }

    private void removePending(Pending operation) {
        Iterator<Map.Entry<Long, Pending>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Pending> entry = iterator.next();
            if (entry.getValue() == operation) {
                Runnable timeout = timeouts.remove(entry.getKey());
                if (timeout != null) timeoutHandler.removeCallbacks(timeout);
                iterator.remove();
            }
        }
    }

    private void removePendingKind(String kind) {
        Iterator<Map.Entry<Long, Pending>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Pending> entry = iterator.next();
            if (kind.equals(entry.getValue().kind)) {
                Runnable timeout = timeouts.remove(entry.getKey());
                if (timeout != null) timeoutHandler.removeCallbacks(timeout);
                iterator.remove();
            }
        }
    }

    private void clearPending() {
        for (Runnable timeout : timeouts.values()) timeoutHandler.removeCallbacks(timeout);
        timeouts.clear();
        pending.clear();
    }

    private long nextRequestId() {
        nextId = (nextId + 1L) & 0xFFFFFFFFL;
        if (nextId == 0L) nextId = 1L;
        return nextId;
    }

    private void broadcast(
            String kind,
            String path,
            String data,
            String message,
            long current,
            long total) {
        Intent event = new Intent(ACTION_EVENT);
        event.setPackage(context.getPackageName());
        event.putExtra(EXTRA_KIND, kind);
        event.putExtra(EXTRA_PATH, safe(path));
        event.putExtra(EXTRA_DATA, safe(data));
        event.putExtra(EXTRA_MESSAGE, safe(message));
        event.putExtra(EXTRA_CURRENT, current);
        event.putExtra(EXTRA_TOTAL, total);
        context.sendBroadcast(event);
    }

    private static long contentSize(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri,
                new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                long size = cursor.getLong(0);
                if (size >= 0L) return size;
            }
        } catch (RuntimeException ignored) {
        }
        try (AssetFileDescriptor descriptor = resolver.openAssetFileDescriptor(uri, "r")) {
            if (descriptor != null && descriptor.getLength() >= 0L) return descriptor.getLength();
        } catch (IOException | RuntimeException ignored) {
        }
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) return -1L;
            byte[] buffer = new byte[64 * 1024];
            long total = 0L;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                total += read;
                if (total > MAX_FILE_SIZE) return total;
            }
            return total;
        } catch (IOException | RuntimeException ignored) {
            return -1L;
        }
    }

    private static Uri parseUri(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Uri.parse(value);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static String friendlyRemoteError(String raw) {
        String message = safe(raw).trim();
        if (message.isEmpty()) return "File operation failed";
        if ("Directory unavailable".equals(message)) return "Folder unavailable on the Flipper";
        if ("File unavailable".equals(message)) return "File unavailable on the Flipper";
        if ("Internal storage is read-only".equals(message)) return "Internal storage is read-only";
        return message;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String message(Exception error, String fallback) {
        String text = error.getMessage();
        return text == null || text.isEmpty() ? fallback : text;
    }

    private static void closeStreams(Pending operation) {
        try {
            if (operation.input != null) operation.input.close();
        } catch (IOException ignored) {
        }
        try {
            if (operation.output != null) operation.output.close();
        } catch (IOException ignored) {
        }
        operation.input = null;
        operation.output = null;
    }

    private static final class Pending {
        final String kind;
        final String path;
        String message;
        StringBuilder buffer;
        Uri uri;
        InputStream input;
        OutputStream output;
        long offset;
        long total;
        String lastCommand;
        String lastArgument = "";
        long lastValue;
        byte[] lastData;
        int retryCount;
        boolean waitingReconnect;

        Pending(String kind, String path) {
            this.kind = kind;
            this.path = path;
        }
    }
}
