package com.flipnet.bridge;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Gère le transport USB CDC Android.
final class UsbCdcTransport {
    // Définit les événements du transport USB.
    interface Listener {
        void onUsbConnected(String name);
        void onUsbDisconnected(String reason);
        void onUsbBytes(byte[] data);
    }

    private static final String ACTION_USB_PERMISSION = "com.flipnet.bridge.USB_PERMISSION";
    private static final int FLIPPER_VENDOR_ID = 0x0483;
    private static final int FLIPPER_PRODUCT_ID = 0x5740;
    private static final int USB_TIMEOUT_MS = 500;
    private static final int USB_READ_SIZE = 512;
    private static final int USB_WRITE_SIZE = 256;
    private static final long USB_LINK_INTERVAL_MS = 2000L;
    private static final long USB_RESCAN_DELAY_MS = 1000L;
    private static final long USB_ATTACH_SETTLE_MS = 300L;
    private static final long USB_PERMISSION_TIMEOUT_MS = 10000L;
    private static final byte[] USB_LINK_FRAME =
            "FN2|LINK|UP\n".getBytes(StandardCharsets.US_ASCII);

    private final Context context;
    private final Listener listener;
    private final UsbManager manager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private final Object writeLock = new Object();

    private UsbDevice device;
    private UsbDeviceConnection connection;
    private UsbInterface controlInterface;
    private UsbInterface dataInterface;
    private UsbEndpoint inputEndpoint;
    private UsbEndpoint outputEndpoint;
    private volatile boolean started;
    private volatile boolean connected;
    private boolean receiverRegistered;
    private int permissionDeviceId = -1;
    private long permissionRequestedAt;

    // Décrit un canal CDC complet.
    private static final class CdcPort {
        final UsbInterface control;
        final UsbInterface data;
        final UsbEndpoint input;
        final UsbEndpoint output;

        // Initialise le canal CDC.
        CdcPort(
                UsbInterface control,
                UsbInterface data,
                UsbEndpoint input,
                UsbEndpoint output) {
            this.control = control;
            this.data = data;
            this.input = input;
            this.output = output;
        }
    }

    // Initialise le transport USB CDC.
    UsbCdcTransport(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.manager = this.context.getSystemService(UsbManager.class);
    }

    // Démarre le composant.
    synchronized void start() {
        if (started || manager == null) return;
        started = true;
        registerReceiver();
        scan();
    }

    // Arrête le composant.
    synchronized void stop() {
        started = false;
        mainHandler.removeCallbacksAndMessages(null);
        close(false, "USB stopped");
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(receiver);
            } catch (IllegalArgumentException ignored) {
            }
            receiverRegistered = false;
        }
        reader.shutdownNow();
    }

    // Indique si le transport est connecté.
    boolean isConnected() {
        return connected && connection != null && outputEndpoint != null;
    }

    // Écrit les données préparées.
    boolean write(byte[] data) {
        if (data == null || data.length == 0) return true;
        synchronized (writeLock) {
            UsbDeviceConnection current = connection;
            UsbEndpoint endpoint = outputEndpoint;
            if (!connected || current == null || endpoint == null) return false;
            int offset = 0;
            while (offset < data.length) {
                int length = Math.min(USB_WRITE_SIZE, data.length - offset);
                byte[] chunk = new byte[length];
                System.arraycopy(data, offset, chunk, 0, length);
                int written = current.bulkTransfer(endpoint, chunk, length, USB_TIMEOUT_MS);
                if (written <= 0) {
                    close(true, "USB write failed");
                    return false;
                }
                offset += written;
            }
            return true;
        }
    }

    // Enregistre le récepteur USB.
    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiverLegacy(filter);
        }
        receiverRegistered = true;
    }

    // Enregistre le récepteur sur un ancien Android.
    @SuppressWarnings("deprecation")
    private void registerReceiverLegacy(IntentFilter filter) {
        context.registerReceiver(receiver, filter);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        // Traite le message Android reçu.
        @Override
        public void onReceive(Context receiverContext, Intent intent) {
            String action = intent.getAction();
            UsbDevice changed = usbDeviceExtra(intent);
            if (ACTION_USB_PERMISSION.equals(action)) {
                permissionDeviceId = -1;
                permissionRequestedAt = 0L;
                if (changed != null
                        && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        && manager != null
                        && manager.hasPermission(changed)
                        && isAttached(changed)) {
                    open(changed);
                } else {
                    scheduleScan();
                }
                return;
            }
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                if (changed != null && changed.getDeviceId() != permissionDeviceId) {
                    permissionDeviceId = -1;
                    permissionRequestedAt = 0L;
                }
                scheduleScan(USB_ATTACH_SETTLE_MS);
                return;
            }
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                if (changed != null && device != null
                        && changed.getDeviceId() == device.getDeviceId()) {
                    close(true, "USB disconnected");
                } else {
                    scheduleScan(USB_ATTACH_SETTLE_MS);
                }
            }
        }
    };

    // Lit l’appareil USB sur un ancien Android.
    @SuppressWarnings("deprecation")
    private static UsbDevice usbDeviceExtraLegacy(Intent intent) {
        return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    }

    // Lit l’appareil USB reçu.
    private static UsbDevice usbDeviceExtra(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
        }
        return usbDeviceExtraLegacy(intent);
    }

    // Recherche le Flipper USB.
    private synchronized void scan() {
        if (!started || connected || manager == null) return;
        if (permissionDeviceId >= 0) {
            long elapsed = SystemClock.elapsedRealtime() - permissionRequestedAt;
            if (elapsed < USB_PERMISSION_TIMEOUT_MS) {
                scheduleScan();
                return;
            }
            permissionDeviceId = -1;
            permissionRequestedAt = 0L;
        }
        for (Map.Entry<String, UsbDevice> entry : manager.getDeviceList().entrySet()) {
            UsbDevice candidate = entry.getValue();
            if (!isFlipperDevice(candidate)) continue;
            if (!isFlipperCandidate(candidate)) {
                scheduleScan();
                continue;
            }
            if (manager.hasPermission(candidate)) {
                open(candidate);
            } else {
                requestPermission(candidate);
            }
            return;
        }
        scheduleScan();
    }

    // Relance la recherche USB.
    private void scheduleScan() {
        scheduleScan(USB_RESCAN_DELAY_MS);
    }

    // Relance la recherche USB après le délai demandé.
    private void scheduleScan(long delayMs) {
        if (!started || connected) return;
        mainHandler.removeCallbacks(rescanRunnable);
        mainHandler.postDelayed(rescanRunnable, delayMs);
    }

    private final Runnable rescanRunnable = this::scan;

    // Demande l’accès à l’appareil USB.
    private void requestPermission(UsbDevice candidate) {
        permissionDeviceId = candidate.getDeviceId();
        permissionRequestedAt = SystemClock.elapsedRealtime();
        Intent permissionIntent = new Intent(ACTION_USB_PERMISSION).setPackage(context.getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
        else flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent permission = PendingIntent.getBroadcast(context, 2402, permissionIntent, flags);
        manager.requestPermission(candidate, permission);
    }

    // Indique si l’appareil est un Flipper USB.
    private static boolean isFlipperDevice(UsbDevice candidate) {
        return candidate != null
                && candidate.getVendorId() == FLIPPER_VENDOR_ID
                && candidate.getProductId() == FLIPPER_PRODUCT_ID;
    }

    // Indique si la FAP expose son canal CDC dédié.
    private static boolean isFlipperCandidate(UsbDevice candidate) {
        return isFlipperDevice(candidate) && findDedicatedPort(candidate) != null;
    }

    // Vérifie que l’appareil appartient encore à l’énumération USB.
    private boolean isAttached(UsbDevice candidate) {
        if (manager == null || candidate == null) return false;
        for (UsbDevice current : manager.getDeviceList().values()) {
            if (current.getDeviceId() == candidate.getDeviceId()) return true;
        }
        return false;
    }

    // Trouve le canal CDC dédié à FlipNet.
    private static CdcPort findDedicatedPort(UsbDevice candidate) {
        CdcPort selected = null;
        for (int index = 0; index < candidate.getInterfaceCount(); index++) {
            UsbInterface item = candidate.getInterface(index);
            if (item.getInterfaceClass() != UsbConstants.USB_CLASS_CDC_DATA) continue;
            UsbEndpoint input = bulkInput(item);
            UsbEndpoint output = bulkOutput(item);
            if (input == null || output == null) continue;
            UsbInterface control = nearestControl(candidate, item.getId());
            CdcPort current = new CdcPort(control, item, input, output);
            if (selected == null || item.getId() > selected.data.getId()) selected = current;
        }
        if (selected == null || selected.data.getId() < 3) return null;
        return selected;
    }

    // Trouve le contrôle associé au canal de données.
    private static UsbInterface nearestControl(UsbDevice candidate, int dataId) {
        UsbInterface selected = null;
        for (int index = 0; index < candidate.getInterfaceCount(); index++) {
            UsbInterface item = candidate.getInterface(index);
            if (item.getInterfaceClass() != UsbConstants.USB_CLASS_COMM) continue;
            if (item.getId() >= dataId) continue;
            if (selected == null || item.getId() > selected.getId()) selected = item;
        }
        return selected;
    }

    // Ouvre le canal CDC dédié.
    private synchronized void open(UsbDevice candidate) {
        if (!started || connected || manager == null || candidate == null) return;
        CdcPort port = findDedicatedPort(candidate);
        if (port == null) {
            scheduleScan();
            return;
        }
        UsbDeviceConnection opened = manager.openDevice(candidate);
        if (opened == null) {
            scheduleScan();
            return;
        }
        if (port.control != null && !opened.claimInterface(port.control, true)) {
            opened.close();
            scheduleScan();
            return;
        }
        if (!opened.claimInterface(port.data, true)) {
            if (port.control != null) opened.releaseInterface(port.control);
            opened.close();
            scheduleScan();
            return;
        }
        if (port.control != null) configureCdc(opened, port.control);
        device = candidate;
        connection = opened;
        controlInterface = port.control;
        dataInterface = port.data;
        inputEndpoint = port.input;
        outputEndpoint = port.output;
        connected = true;
        permissionDeviceId = -1;
        permissionRequestedAt = 0L;
        mainHandler.removeCallbacks(rescanRunnable);
        listener.onUsbConnected(candidate.getDeviceName());
        reader.execute(this::readLoop);
    }

    // Configure le canal CDC retenu.
    private static void configureCdc(UsbDeviceConnection opened, UsbInterface control) {
        byte[] lineCoding = new byte[] {
                0x00, (byte) 0xC2, 0x01, 0x00,
                0x00, 0x00, 0x08
        };
        int controlId = control.getId();
        opened.controlTransfer(0x21, 0x20, 0, controlId, lineCoding, lineCoding.length, 1000);
        opened.controlTransfer(0x21, 0x22, 0x03, controlId, null, 0, 1000);
    }

    // Lit les données USB en continu.
    private void readLoop() {
        byte[] buffer = new byte[USB_READ_SIZE];
        long nextLink = 0L;
        while (started && connected) {
            long now = SystemClock.elapsedRealtime();
            if (now >= nextLink) {
                if (!write(USB_LINK_FRAME)) return;
                nextLink = now + USB_LINK_INTERVAL_MS;
            }
            UsbDeviceConnection current = connection;
            UsbEndpoint endpoint = inputEndpoint;
            if (current == null || endpoint == null) break;
            int length;
            try {
                length = current.bulkTransfer(endpoint, buffer, buffer.length, USB_TIMEOUT_MS);
            } catch (RuntimeException error) {
                close(true, "USB read failed");
                return;
            }
            if (length > 0) {
                byte[] data = new byte[length];
                System.arraycopy(buffer, 0, data, 0, length);
                listener.onUsbBytes(data);
            }
        }
        if (started && connected) close(true, "USB read stopped");
    }

    // Libère les ressources.
    private synchronized void close(boolean notify, String reason) {
        boolean wasConnected = connected;
        connected = false;
        permissionDeviceId = -1;
        permissionRequestedAt = 0L;
        UsbDeviceConnection current = connection;
        connection = null;
        if (current != null) {
            try {
                if (dataInterface != null) current.releaseInterface(dataInterface);
                if (controlInterface != null) current.releaseInterface(controlInterface);
            } catch (RuntimeException ignored) {
            }
            current.close();
        }
        device = null;
        controlInterface = null;
        dataInterface = null;
        inputEndpoint = null;
        outputEndpoint = null;
        if (notify && wasConnected) listener.onUsbDisconnected(reason);
        if (started) scheduleScan();
    }

    // Retourne le canal USB entrant.
    private static UsbEndpoint bulkInput(UsbInterface item) {
        for (int index = 0; index < item.getEndpointCount(); index++) {
            UsbEndpoint endpoint = item.getEndpoint(index);
            if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                    && endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                return endpoint;
            }
        }
        return null;
    }

    // Retourne le canal USB sortant.
    private static UsbEndpoint bulkOutput(UsbInterface item) {
        for (int index = 0; index < item.getEndpointCount(); index++) {
            UsbEndpoint endpoint = item.getEndpoint(index);
            if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                    && endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                return endpoint;
            }
        }
        return null;
    }
}
