package com.flipnet.bridge;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.util.Log;


import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.CRC32;


// Gère le service Android et les transports.
public final class FlipNetService extends Service {
    public static final String ACTION_START = "com.flipnet.bridge.START";
    public static final String ACTION_STOP = "com.flipnet.bridge.STOP";
    public static final String PREFERENCES = "flipnet_settings";
    public static final String KEY_ENABLED = "background_enabled";
    public static final String KEY_BOOT_ENABLED = "boot_enabled";
    public static final String KEY_STATUS_TEXT = "status_text";
    public static final String KEY_TRANSPORT = "transport_kind";
    public static final String KEY_LOADING = "ui_loading";
    public static final String KEY_REQUEST_COUNT = "request_count";
    public static final String KEY_CONNECTED = "transport_connected";
    public static final String KEY_LAST_LOG = "last_log";
    public static final String KEY_TRAFFIC_LOG = "traffic_log";
    public static final String KEY_ERROR_COUNT = "error_count";
    public static final String KEY_METRICS_SCHEMA = "metrics_schema";
    public static final String KEY_LAST_LATENCY_MS = "last_latency_ms";
    public static final String KEY_RSSI = "ble_rssi";
    public static final String KEY_STARTED_AT = "service_started_at";
    public static final String KEY_TRUSTED_DEVICE = PhoneLink.KEY_TRUSTED_DEVICE;
    public static final String KEY_LANGUAGE = "interface_language";

    private static final String TAG = "FlipNetService";
    private static final String CHANNEL_ID = "flipnet_bridge";
    private static final String ALERT_CHANNEL_ID = "flipnet_alerts";
    private static final int NOTIFICATION_ID = 2001;
    private static final int ALERT_NOTIFICATION_ID = 2002;
    private static final long MONITOR_POLL_MS = 30_000L;

    private static final UUID SERIAL_SERVICE_UUID = UUID.fromString("8fe5b3d5-2e7f-4a98-2a48-7acc60fe0000");
    private static final UUID FLIPPER_TO_HOST_UUID = UUID.fromString("19ed82ae-ed21-4c9d-4145-228e61fe0000");
    private static final UUID HOST_TO_FLIPPER_UUID = UUID.fromString("19ed82ae-ed21-4c9d-4145-228e62fe0000");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final int BLE_CHUNK_DEFAULT = 20;
    private static final int BLE_CHUNK_MAX = 240;
    private static final byte[] BLE_LINK_FRAME =
            "FN2|LINK|UP\n".getBytes(StandardCharsets.US_ASCII);
    private static final long BLE_LINK_INTERVAL_MS = 4000L;
    private static final long USB_PRIORITY_DELAY_MS = 1500L;
    private static final long BLE_CHUNK_DELAY_MS = 16L;
    private static final long BLE_TX_RETRY_DELAY_MS = 80L;
    private static final int BLE_TX_RETRY_MAX = 8;
    private static final long RECONNECT_DELAY_MS = 3000L;
    private static final long RECONNECT_MAX_MS = 30000L;
    private static final int BLE_MTU_REQUEST = 247;
    private static final long BLE_READY_SETTLE_MS = 700L;
    private static final long BLE_WRITE_ACK_TIMEOUT_MS = 2500L;
    private static final int TRAFFIC_LOG_LIMIT = 12000;
    private static final long RSSI_POLL_MS = 10_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ByteArrayOutputStream bleReceiveBuffer = new ByteArrayOutputStream();
    private final ByteArrayOutputStream usbReceiveBuffer = new ByteArrayOutputStream();
    private final ArrayDeque<byte[]> txQueue = new ArrayDeque<>();

    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic rxCharacteristic;
    private BluetoothGattCharacteristic txCharacteristic;
    private boolean scanning;
    private boolean stopping;
    private boolean txRunning;
    private boolean txAwaitingAck;
    private boolean bleReady;
    private byte[] txCurrentChunk;
    private int txRetryCount;
    private boolean discoveryStarted;
    private boolean bluetoothReceiverRegistered;
    private int bleChunkBytes = BLE_CHUNK_DEFAULT;
    private long reconnectDelayMs = RECONNECT_DELAY_MS;
    private PowerManager.WakeLock wakeLock;
    private UsbCdcTransport usbTransport;
    private ServiceRegistry serviceRegistry;
    private AndroidFeatures features;
    private AndroidStateStore featureState;
    private FlipperFileTransferManager fileTransferManager;
    private final Runnable reconnectRunnable = this::startScanWhenReady;
    private final Runnable bleWriteAckWatchdog = () -> {
        BluetoothGatt currentGatt = gatt;
        if (!txAwaitingAck || currentGatt == null || stopping) return;
        txAwaitingAck = false;
        retryBleChunk(currentGatt, "BLE write acknowledgement timeout");
    };

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        // Traite le message Android reçu.
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) return;
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            if (state == BluetoothAdapter.STATE_ON) {
                appendTrafficLog("SYS", "Bluetooth enabled");
                reconnectDelayMs = RECONNECT_DELAY_MS;
                handler.removeCallbacksAndMessages(null);
                handler.post(reconnectRunnable);
                handler.postDelayed(monitorScheduler, 5_000L);
            } else if (state == BluetoothAdapter.STATE_OFF) {
                appendTrafficLog("SYS", "Bluetooth disabled");
                stopScan();
                closeGatt();
                if (usbTransport != null && usbTransport.isConnected()) {
                    persistUiState("Connected to Flipper.", "USB", true, false);
                    updateNotification("Connected to Flipper over USB");
                    worker.execute(() -> {
                        if (fileTransferManager != null) fileTransferManager.onConnected();
                    });
                } else {
                    persistUiState("Bluetooth is disabled.", "", false, false);
                    updateNotification("Enable Bluetooth or connect USB OTG");
                }
            }
        }
    };

    private final Runnable rssiScheduler = new Runnable() {
        // Actualise la puissance du signal BLE.
        @Override
        public void run() {
            BluetoothGatt currentGatt = gatt;
            if (!stopping && currentGatt != null && hasBluetoothPermissions()) {
                if (fileTransferManager == null || !fileTransferManager.isTransferActive()) {
                    try {
                        currentGatt.readRemoteRssi();
                    } catch (RuntimeException error) {
                        Log.w(TAG, "RSSI read failed", error);
                    }
                }
                handler.postDelayed(this, RSSI_POLL_MS);
            }
        }
    };

    private final Runnable bleLinkScheduler = new Runnable() {
        // Envoie le signal de présence BLE.
        @Override
        public void run() {
            if (stopping || !isBleLinked()
                    || (usbTransport != null && usbTransport.isConnected())) return;
            if (txRunning || !txQueue.isEmpty()
                    || (fileTransferManager != null && fileTransferManager.isTransferActive())) {
                handler.postDelayed(this, BLE_LINK_INTERVAL_MS);
                return;
            }
            enqueueResponse(BLE_LINK_FRAME, "BLE");
            handler.postDelayed(this, BLE_LINK_INTERVAL_MS);
        }
    };

    private final Runnable bleFallbackScheduler = this::startScanWhenReady;

    private final Runnable monitorScheduler = new Runnable() {
        // Exécute les surveillances planifiées.
        @Override
        public void run() {
            if (stopping) return;
            worker.execute(() -> {
                try {
                    featureModules().operations.runScheduledMonitorCycle();
                } catch (Exception error) {
                    Log.e(TAG, "Monitor error", error);
                }
            });
            handler.postDelayed(this, MONITOR_POLL_MS);
        }
    };

    // Initialise le composant Android.
    @Override
    public void onCreate() {
        super.onCreate();
        featureState = new AndroidStateStore(
                getSharedPreferences(PREFERENCES, MODE_PRIVATE));
        features = new AndroidFeatures(this, featureState);
        serviceRegistry = AndroidFeatureRegistry.create(features);
        fileTransferManager = new FlipperFileTransferManager(this, this::sendFileFrame);
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
        registerBluetoothReceiver();
        acquireWakeLock();
        createNotificationChannel();
        usbTransport = new UsbCdcTransport(this, new UsbCdcTransport.Listener() {
            // Active le transport USB.
            @Override
            public void onUsbConnected(String name) {
                handler.post(() -> {
                    stopScan();
                    handler.removeCallbacks(bleLinkScheduler);
                    handler.removeCallbacks(bleFallbackScheduler);
                    closeGatt();
                    synchronized (bleReceiveBuffer) {
                        bleReceiveBuffer.reset();
                    }
                    appendTrafficLog("USB", "Connected: " + name);
                    persistUiState("Connected to Flipper.", "USB", true, false);
                    updateNotification("Connected to Flipper over USB");
                    worker.execute(() -> {
                        if (fileTransferManager != null) fileTransferManager.onConnected();
                    });
                });
            }

            // Désactive le transport USB.
            @Override
            public void onUsbDisconnected(String reason) {
                synchronized (usbReceiveBuffer) {
                    usbReceiveBuffer.reset();
                }
                worker.execute(() -> {
                    if (fileTransferManager != null) fileTransferManager.onDisconnected();
                });
                handler.post(() -> {
                    appendTrafficLog("USB", reason);
                    if (!stopping) {
                        persistUiState("USB disconnected. Searching over BLE…", "", false, true);
                        updateNotification("USB disconnected; searching over BLE");
                        startScanWhenReady();
                    }
                });
            }

            // Traite les octets USB reçus.
            @Override
            public void onUsbBytes(byte[] data) {
                handleIncoming("USB", usbReceiveBuffer, data);
            }
        });
        persistUiState("Starting USB gateway…", "USB", false, true);
        startAsForeground("Searching for the Flipper…");
    }

    // Traite le démarrage du service Android.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null
                && FlipperFileTransferManager.ACTION_COMMAND.equals(intent.getAction())) {
            Intent commandIntent = new Intent(intent);
            worker.execute(() -> {
                if (fileTransferManager != null) fileTransferManager.handleCommand(commandIntent);
            });
            return START_STICKY;
        }
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_ENABLED, false)
                    .apply();
            stopping = true;
            persistUiState("FlipNet stopped.", "", false, false);
            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                    .putLong(KEY_STARTED_AT, 0L)
                    .putInt(KEY_RSSI, 0)
                    .apply();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, true)
                .apply();
        stopping = false;
        reconnectDelayMs = RECONNECT_DELAY_MS;
        SharedPreferences preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        GatewayMetrics.initialize(preferences);
        appendTrafficLog("SYS", "Service started");
        persistUiState("Searching over USB…", "USB", false, true);
        if (usbTransport != null) usbTransport.start();
        handler.removeCallbacks(bleFallbackScheduler);
        handler.postDelayed(bleFallbackScheduler, USB_PRIORITY_DELAY_MS);
        handler.removeCallbacks(monitorScheduler);
        handler.postDelayed(monitorScheduler, 5_000L);
        return START_STICKY;
    }

    // Gère la liaison au service Android.
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Crée le canal de notification.
    private void createNotificationChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Persistent connection between Android and FlipNet");
        manager.createNotificationChannel(channel);
        NotificationChannel alerts = new NotificationChannel(
                ALERT_CHANNEL_ID,
                "FlipNet alerts",
                NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("Monitor state changes");
        manager.createNotificationChannel(alerts);
    }

    // Construit la notification du service.
    private Notification buildNotification(String text) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, FlipNetService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_flipnet)
                .setContentTitle("FlipNet")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setColor(getColor(R.color.notification_color))
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_flipnet),
                        getString(R.string.stop_service),
                        stop).build())
                .build();
    }

    // Place le service au premier plan.
    private void startAsForeground(String text) {
        Notification notification = buildNotification(text);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    // Actualise la notification du service.
    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    // Enregistre l’état visible par l’application.
    private void persistUiState(String text, String transport, boolean linked, boolean loading) {
        getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .edit()
                .putString(KEY_STATUS_TEXT, text)
                .putString(KEY_TRANSPORT, transport)
                .putBoolean(KEY_LOADING, loading)
                .putBoolean(KEY_CONNECTED, linked)
                .apply();
    }

    // Ajoute une ligne au journal du trafic.
    private synchronized void appendTrafficLog(String direction, String message) {
        String safe = message == null ? "" : message.replace("\r", "").replace("\n", " ");
        if (safe.length() > 1000) safe = safe.substring(0, 1000) + "…";
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());
        String line = timestamp + "  " + direction + "  " + safe;
        SharedPreferences preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        String current = preferences.getString(KEY_TRAFFIC_LOG, "");
        String updated = current == null || current.isEmpty() ? line : current + "\n" + line;
        if (updated.length() > TRAFFIC_LOG_LIMIT) {
            int cut = updated.indexOf('\n', updated.length() - TRAFFIC_LOG_LIMIT);
            updated = cut >= 0 ? updated.substring(cut + 1) : updated.substring(updated.length() - TRAFFIC_LOG_LIMIT);
        }
        preferences.edit().putString(KEY_TRAFFIC_LOG, updated).apply();
    }

    // Enregistre le récepteur Bluetooth.
    private void registerBluetoothReceiver() {
        if (bluetoothReceiverRegistered) return;
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerBluetoothReceiverLegacy(filter);
        }
        bluetoothReceiverRegistered = true;
    }

    // Enregistre le récepteur sur un ancien Android.
    @SuppressWarnings("deprecation")
    private void registerBluetoothReceiverLegacy(IntentFilter filter) {
        registerReceiver(bluetoothReceiver, filter);
    }

    // Garde le processeur actif.
    private void acquireWakeLock() {
        PowerManager manager = getSystemService(PowerManager.class);
        if (manager == null) return;
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FlipNet:Gateway");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
    }

    // Planifie une reconnexion BLE.
    private void scheduleReconnect() {
        if (stopping) return;
        handler.removeCallbacks(reconnectRunnable);
        handler.postDelayed(reconnectRunnable, reconnectDelayMs);
        reconnectDelayMs = Math.min(reconnectDelayMs * 2L, RECONNECT_MAX_MS);
    }

    // Ferme la connexion GATT.
    private void closeGatt() {
        BluetoothGatt currentGatt = gatt;
        gatt = null;
        if (currentGatt == null) return;
        try {
            currentGatt.disconnect();
            currentGatt.close();
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to close GATT", error);
        }
        rxCharacteristic = null;
        txCharacteristic = null;
        txQueue.clear();
        txRunning = false;
        handler.removeCallbacks(bleWriteAckWatchdog);
        txAwaitingAck = false;
        bleReady = false;
        txCurrentChunk = null;
        txRetryCount = 0;
        bleChunkBytes = BLE_CHUNK_DEFAULT;
        discoveryStarted = false;
    }

    // Indique si les permissions Bluetooth sont présentes.
    boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    // Démarre la recherche BLE quand possible.
    private void startScanWhenReady() {
        if (stopping || scanning || gatt != null
                || (usbTransport != null && usbTransport.isConnected())) return;
        if (!hasBluetoothPermissions()) {
            persistUiState("Bluetooth permission required.", "", false, false);
            updateNotification("Bluetooth permissions required");
            return;
        }
        if (adapter == null || !adapter.isEnabled()) {
            persistUiState("Bluetooth is disabled.", "", false, false);
            updateNotification("Enable Bluetooth");
            scheduleReconnect();
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            persistUiState("Bluetooth scanner unavailable.", "", false, false);
            updateNotification("Bluetooth scanner unavailable");
            scheduleReconnect();
            return;
        }
        scanning = true;
        appendTrafficLog("BLE", "Scan started");
        persistUiState("Searching for the Flipper…", "", false, true);
        updateNotification("Searching for the Flipper…");
        try {
            scanner.startScan(scanCallback);
        } catch (RuntimeException error) {
            scanning = false;
            persistUiState("Unable to start BLE scan.", "", false, false);
            updateNotification("BLE scan unavailable");
            scheduleReconnect();
            return;
        }
        handler.postDelayed(() -> {
            if (scanning) {
                stopScan();
                scheduleReconnect();
            }
        }, 10_000L);
    }

    // Arrête la recherche BLE.
    private void stopScan() {
        if (!scanning) return;
        scanning = false;
        try {
            if (scanner != null && hasBluetoothPermissions()) {
                scanner.stopScan(scanCallback);
            }
        } catch (Exception ignored) {
        }
    }

    // Indique si l’analyse correspond à FlipNet.
    private boolean isFlipNetResult(ScanResult result) {
        if (result.getScanRecord() != null) {
            List<ParcelUuid> uuids = result.getScanRecord().getServiceUuids();
            if (uuids != null) {
                for (ParcelUuid uuid : uuids) {
                    if (SERIAL_SERVICE_UUID.equals(uuid.getUuid())) return true;
                }
            }
            String name = result.getScanRecord().getDeviceName();
            if (name != null) {
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.contains("flipper")
                        || lower.startsWith("fz")
                        || lower.startsWith("flip_")) return true;
            }
        }
        return false;
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        // Traite un appareil BLE trouvé.
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (!isFlipNetResult(result) || gatt != null || stopping) return;
            BluetoothDevice device = result.getDevice();
            String trusted = PhoneLink.trustedDevice(FlipNetService.this);
            if (!trusted.isEmpty()) {
                try {
                    if (!trusted.equalsIgnoreCase(device.getAddress())) return;
                } catch (SecurityException error) {
                    return;
                }
            }
            stopScan();
            connect(device);
        }

        // Traite un échec de recherche BLE.
        @Override
        public void onScanFailed(int errorCode) {
            scanning = false;
            persistUiState("BLE scan error " + errorCode, "", false, false);
            updateNotification("BLE scan error " + errorCode);
            scheduleReconnect();
        }
    };

    // Ouvre la connexion BLE.
    private void connect(BluetoothDevice device) {
        if (!hasBluetoothPermissions()) return;
        String name = safeDeviceName(device);
        appendTrafficLog("BLE", "Connecting to " + name);
        persistUiState("Connecting to " + name + "…", "", false, true);
        updateNotification("Connecting to " + name);
        try {
            gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (RuntimeException error) {
            gatt = null;
            persistUiState("BLE connection failed. Retrying…", "", false, true);
            updateNotification("BLE connection failed");
            scheduleReconnect();
        }
    }

    // Recherche les services GATT FlipNet.
    private void discoverFlipNetServices(BluetoothGatt currentGatt) {
        if (currentGatt != gatt || discoveryStarted || stopping) return;
        discoveryStarted = true;
        persistUiState("Discovering services…", "", false, true);
        updateNotification("Discovering services…");
        if (!currentGatt.discoverServices()) {
            handleDisconnect(currentGatt, "BLE services unavailable. Reconnecting…");
        }
    }

    // Lit le nom Bluetooth sans erreur.
    private String safeDeviceName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name == null ? "Flipper" : name;
        } catch (SecurityException exception) {
            return "Flipper";
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        // Traite un changement de connexion GATT.
        @Override
        public void onConnectionStateChange(BluetoothGatt currentGatt, int status, int newState) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handleDisconnect(currentGatt, "BLE connection interrupted. Reconnecting…");
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                reconnectDelayMs = RECONNECT_DELAY_MS;
                discoveryStarted = false;
                bleReady = false;
                persistUiState("Negotiating BLE…", "", false, true);
                updateNotification("Negotiating BLE…");
                if (!currentGatt.requestMtu(BLE_MTU_REQUEST)) {
                    discoverFlipNetServices(currentGatt);
                } else {
                    handler.postDelayed(() -> discoverFlipNetServices(currentGatt), 1500L);
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                handleDisconnect(currentGatt);
            }
        }

        // Mémorise la taille MTU négociée.
        @Override
        public void onMtuChanged(BluetoothGatt currentGatt, int mtu, int status) {
            if (currentGatt != gatt) return;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                bleChunkBytes = Math.max(BLE_CHUNK_DEFAULT,
                        Math.min(BLE_CHUNK_MAX, mtu - 3));
                Log.i(TAG, "MTU BLE: " + mtu);
                appendTrafficLog("BLE", "Payload chunk: " + bleChunkBytes + " bytes");
            } else {
                bleChunkBytes = BLE_CHUNK_DEFAULT;
                Log.w(TAG, "BLE MTU rejected: " + status);
            }
            discoverFlipNetServices(currentGatt);
        }

        // Configure les services GATT trouvés.
        @Override
        public void onServicesDiscovered(BluetoothGatt currentGatt, int status) {
            if (currentGatt != gatt) return;
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handleDisconnect(currentGatt, "BLE discovery failed. Reconnecting…");
                return;
            }
            BluetoothGattService service = currentGatt.getService(SERIAL_SERVICE_UUID);
            if (service == null) {
                handleDisconnect(currentGatt, "Open FlipNet on the Flipper.");
                return;
            }
            rxCharacteristic = service.getCharacteristic(FLIPPER_TO_HOST_UUID);
            txCharacteristic = service.getCharacteristic(HOST_TO_FLIPPER_UUID);
            if (rxCharacteristic == null || txCharacteristic == null) {
                handleDisconnect(currentGatt, "Incompatible FlipNet BLE profile.");
                return;
            }
            if (!currentGatt.setCharacteristicNotification(rxCharacteristic, true)) {
                handleDisconnect(currentGatt, "BLE notifications unavailable.");
                return;
            }
            BluetoothGattDescriptor descriptor = rxCharacteristic.getDescriptor(CCCD_UUID);
            byte[] value = notificationValue(rxCharacteristic);
            if (descriptor == null || value == null) {
                handleDisconnect(currentGatt, "BLE notifications unavailable.");
                return;
            }
            boolean accepted;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                accepted = currentGatt.writeDescriptor(descriptor, value)
                        == BluetoothGatt.GATT_SUCCESS;
            } else {
                accepted = writeDescriptorLegacy(currentGatt, descriptor, value);
            }
            if (!accepted) {
                handleDisconnect(currentGatt, "BLE activation failed. Reconnecting…");
            }
        }

        // Poursuit après l’écriture du descripteur.
        @Override
        public void onDescriptorWrite(BluetoothGatt currentGatt, BluetoothGattDescriptor descriptor, int status) {
            if (currentGatt != gatt || !CCCD_UUID.equals(descriptor.getUuid())) return;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                appendTrafficLog("BLE", "Notifications enabled; settling link");
                try {
                    BluetoothDevice device = currentGatt.getDevice();
                    PhoneLink.trustDevice(FlipNetService.this, device.getAddress());
                } catch (SecurityException ignored) {
                }
                /* The Flipper BT service temporarily installs its stock serial/RPC
                 * callback while a serial-profile connection comes up. Give the FAP
                 * status callback time to reclaim the serial callback before sending
                 * the first ASCII frame. This avoids feeding FN2/FNFS bytes into the
                 * stock protobuf RPC decoder during reconnect races. */
                handler.postDelayed(() -> {
                    if (currentGatt != gatt || stopping) return;
                    bleReady = true;
                    appendTrafficLog("BLE", "Link ready");
                    persistUiState("Connected to Flipper.", "BLE", true, false);
                    updateNotification("Connected to Flipper");
                    handler.removeCallbacks(rssiScheduler);
                    handler.postDelayed(rssiScheduler, RSSI_POLL_MS);
                    handler.removeCallbacks(bleLinkScheduler);
                    handler.postDelayed(bleLinkScheduler, BLE_LINK_INTERVAL_MS);
                    worker.execute(() -> {
                        if (fileTransferManager != null) fileTransferManager.onConnected();
                    });
                }, BLE_READY_SETTLE_MS);
            } else {
                handleDisconnect(currentGatt, "BLE activation rejected. Reconnecting…");
            }
        }

        // Mémorise la puissance du signal BLE.
        @Override
        public void onReadRemoteRssi(BluetoothGatt currentGatt, int rssi, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS && currentGatt == gatt) {
                getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                        .edit()
                        .putInt(KEY_RSSI, rssi)
                        .apply();
            }
        }

        // Traite une notification GATT reçue.
        @Override
        public void onCharacteristicChanged(
                BluetoothGatt currentGatt,
                BluetoothGattCharacteristic characteristic,
                byte[] value) {
            if (currentGatt == gatt && FLIPPER_TO_HOST_UUID.equals(characteristic.getUuid())) {
                handleIncoming("BLE", bleReceiveBuffer, value);
            }
        }

        // Traite une notification GATT reçue.
        @SuppressWarnings("deprecation")
        @Override
        public void onCharacteristicChanged(
                BluetoothGatt currentGatt,
                BluetoothGattCharacteristic characteristic) {
            if (currentGatt == gatt && FLIPPER_TO_HOST_UUID.equals(characteristic.getUuid())) {
                handleIncoming("BLE", bleReceiveBuffer, characteristic.getValue());
            }
        }

        // Poursuit après une écriture GATT.
        @Override
        public void onCharacteristicWrite(
                BluetoothGatt currentGatt,
                BluetoothGattCharacteristic characteristic,
                int status) {
            if (!HOST_TO_FLIPPER_UUID.equals(characteristic.getUuid())) return;
            /* GATT callbacks are not guaranteed to run on our main Handler. Keep
             * the TX queue state single-threaded and ignore callbacks belonging
             * to a GATT instance that has already been replaced. */
            handler.post(() -> {
                if (currentGatt != gatt) return;
                handler.removeCallbacks(bleWriteAckWatchdog);
                txAwaitingAck = false;
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    retryBleChunk(currentGatt, "BLE write rejected");
                    return;
                }
                txCurrentChunk = null;
                txRetryCount = 0;
                handler.postDelayed(FlipNetService.this::sendNextChunk, BLE_CHUNK_DELAY_MS);
            });
        }
    };


    // Lit la valeur d’une notification GATT.
    private static byte[] notificationValue(BluetoothGattCharacteristic characteristic) {
        int properties = characteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            return BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
        }
        if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
            return BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
        }
        return null;
    }

    // Écrit un descripteur sur un ancien Android.
    @SuppressWarnings("deprecation")
    private static boolean writeDescriptorLegacy(
            BluetoothGatt currentGatt,
            BluetoothGattDescriptor descriptor,
            byte[] value) {
        descriptor.setValue(value);
        return currentGatt.writeDescriptor(descriptor);
    }

    // Écrit une caractéristique sur un ancien Android.
    @SuppressWarnings("deprecation")
    private static boolean writeCharacteristicLegacy(
            BluetoothGatt currentGatt,
            BluetoothGattCharacteristic characteristic,
            byte[] value,
            int writeType) {
        characteristic.setWriteType(writeType);
        characteristic.setValue(value);
        return currentGatt.writeCharacteristic(characteristic);
    }

    // Choisit le mode d’écriture GATT.
    private static int responseWriteType(BluetoothGattCharacteristic characteristic) {
        int properties = characteristic.getProperties();
        // Prefer acknowledged writes. File transfers send long logical frames and
        // WRITE_NO_RESPONSE can overflow the Android/Flipper BLE queues under load.
        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
            return BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
        }
        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            return BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
        }
        return -1;
    }

    // Traite une déconnexion BLE.
    private void handleDisconnect(BluetoothGatt disconnectedGatt) {
        handleDisconnect(disconnectedGatt, "Flipper disconnected. Reconnecting…");
    }

    // Traite une déconnexion BLE.
    private void handleDisconnect(BluetoothGatt disconnectedGatt, String message) {
        appendTrafficLog("BLE", message);
        handler.post(() -> {
            if (gatt != null && gatt != disconnectedGatt) {
                try {
                    disconnectedGatt.close();
                } catch (Exception ignored) {
                }
                return;
            }
            if (gatt == disconnectedGatt) {
                try {
                    gatt.close();
                } catch (Exception ignored) {
                }
                gatt = null;
            }
            rxCharacteristic = null;
            txCharacteristic = null;
            txQueue.clear();
            txRunning = false;
            handler.removeCallbacks(bleWriteAckWatchdog);
            txAwaitingAck = false;
            bleReady = false;
            txCurrentChunk = null;
            txRetryCount = 0;
            bleChunkBytes = BLE_CHUNK_DEFAULT;
            discoveryStarted = false;
            handler.removeCallbacks(rssiScheduler);
            handler.removeCallbacks(bleLinkScheduler);
            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putInt(KEY_RSSI, 0).apply();
            synchronized (bleReceiveBuffer) {
                bleReceiveBuffer.reset();
            }
            worker.execute(() -> {
                if (fileTransferManager == null) return;
                UsbCdcTransport currentUsb = usbTransport;
                if (currentUsb != null && currentUsb.isConnected()) {
                    fileTransferManager.onConnected();
                } else {
                    fileTransferManager.onDisconnected();
                }
            });
            if (!stopping) {
                if (usbTransport != null && usbTransport.isConnected()) {
                    persistUiState("Connected to Flipper.", "USB", true, false);
                    updateNotification("Connected to Flipper over USB");
                    worker.execute(() -> {
                        if (fileTransferManager != null) fileTransferManager.onConnected();
                    });
                } else {
                    persistUiState(message, "", false, true);
                    updateNotification(message);
                    scheduleReconnect();
                }
            }
        });
    }

    // Traite les octets reçus.
    private void handleIncoming(
            String transport,
            ByteArrayOutputStream buffer,
            byte[] data) {
        if (data == null || data.length == 0) return;
        synchronized (buffer) {
            for (byte value : data) {
                if (value == '\n' || value == '\r') {
                    if (buffer.size() > 0) {
                        String line = new String(buffer.toByteArray(), StandardCharsets.US_ASCII);
                        buffer.reset();
                        appendTrafficLog(transport + " RX", trafficSummary(line));
                        worker.execute(() -> {
                            if (fileTransferManager != null
                                    && fileTransferManager.handleResponseLine(line)) return;
                            processRequest(line, transport);
                        });
                    }
                } else if (buffer.size() < 2303) {
                    buffer.write(value);
                } else {
                    buffer.reset();
                }
            }
        }
    }

    // Exécute une requête reçue.
    private void processRequest(String line, String transport) {
        long startedAt = SystemClock.elapsedRealtime();
        Request request;
        try {
            request = parseRequest(line, transport);
        } catch (Exception error) {
            Log.w(TAG, "Invalid request: " + error.getMessage());
            recordGatewayMetrics(startedAt, true);
            return;
        }
        getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .edit()
                .putLong(KEY_REQUEST_COUNT,
                        getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                                .getLong(KEY_REQUEST_COUNT, 0L) + 1L)
                .putString(KEY_STATUS_TEXT, "Flipper request: " + request.service)
                .putString(KEY_LAST_LOG, "Request received: " + request.service)
                .putString(KEY_TRANSPORT, request.transport)
                .putString(KEY_LANGUAGE, request.language)
                .putBoolean(KEY_LOADING, true)
                .apply();
        try {
            ServiceResult result = localizeResult(
                    dispatch(request.service, request.argument, request.language),
                    request.language);
            featureState().recordHistory(request.service, request.argument, "OK", result.title, result.body);
            enqueueResponse(buildResponse(request, "OK", result.title, result.body), request.transport);
            getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_LOG, "Response sent: " + request.service)
                    .apply();
            persistUiState("Connected to Flipper.", request.transport, true, false);
            recordGatewayMetrics(startedAt, false);
        } catch (UserError error) {
            String title = AndroidFeatureSupport.tr(request.language, "Error", "Erreur");
            String messageEn = localizeError(error.getMessage(), "en");
            String messageFr = localizeError(error.getMessage(), "fr");
            String message = AndroidFeatureSupport.isFrench(request.language)
                    ? messageFr : messageEn;
            String body = ErrorProtocol.payload(error.code, error.category,
                    request.service, error.recoverable, messageEn, messageFr);
            featureState().recordHistory(request.service, request.argument, "ERR", title, message);
            enqueueResponse(buildResponse(request, "ERR", title, body), request.transport);
            getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_LOG, "Error: " + request.service)
                    .apply();
            persistUiState("Connected to Flipper.", request.transport, true, false);
            recordGatewayMetrics(startedAt, !error.recoverable);
        } catch (Exception error) {
            Log.e(TAG, "Service error", error);
            String messageEn = "The service failed unexpectedly";
            String messageFr = "Le service a echoue de maniere inattendue";
            String message = AndroidFeatureSupport.isFrench(request.language)
                    ? messageFr : messageEn;
            String title = AndroidFeatureSupport.tr(request.language, "Internal error", "Erreur interne");
            String body = ErrorProtocol.payload("INTERNAL_ERROR", "runtime",
                    request.service, false, messageEn, messageFr);
            featureState().recordHistory(request.service, request.argument, "ERR", title, message);
            enqueueResponse(buildResponse(request, "ERR", title, body), request.transport);
            getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_LOG, "Internal error")
                    .apply();
            persistUiState("Connected to Flipper.", request.transport, true, false);
            recordGatewayMetrics(startedAt, true);
        }
    }

    // Met à jour les mesures de la passerelle.
    private void recordGatewayMetrics(long startedAt, boolean error) {
        GatewayMetrics.record(getSharedPreferences(PREFERENCES, MODE_PRIVATE), startedAt, error);
    }

    // Retourne le transport actif.
    private String currentTransport() {
        if (usbTransport != null && usbTransport.isConnected()) return "USB";
        if (isBleLinked()) return "BLE";
        return "";
    }

    // Envoie une trame de gestion de fichiers sur la liaison active.
    private boolean sendFileFrame(byte[] frame) {
        String transport = currentTransport();
        if (transport.isEmpty()) return false;
        enqueueResponse(frame, transport);
        return true;
    }

    // Evite d'ecrire le contenu binaire des transferts de fichiers dans la console.
    private static String trafficSummary(String line) {
        if (line == null) return "";
        String fileFrame = FlipperFileProtocol.extractFrame(line);
        if (fileFrame == null) return line;
        String[] fields = fileFrame.split("\\|", -1);
        String direction = fields.length > 1 ? fields[1] : "FILE";
        String id = fields.length > 2 ? fields[2] : "?";
        int commandIndex = "RES".equals(direction) ? 4 : 3;
        String command = fields.length > commandIndex ? fields[commandIndex] : "?";
        return FlipperFileProtocol.VERSION + "|" + direction + "|" + id + "|" + command + "|[file frame]";
    }

    // Ajoute une réponse sur le transport actif.
    private void enqueueResponse(byte[] response) {
        enqueueResponse(response, currentTransport());
    }

    // Ajoute une réponse sur le transport source.
    private void enqueueResponse(byte[] response, String transport) {
        String line = new String(response, StandardCharsets.US_ASCII).trim();
        appendTrafficLog(transport + " TX", trafficSummary(line));
        if ("USB".equals(transport)) {
            UsbCdcTransport currentUsb = usbTransport;
            if (currentUsb == null || !currentUsb.isConnected() || !currentUsb.write(response)) {
                appendTrafficLog("USB", "Response write failed");
            }
            return;
        }
        if (!"BLE".equals(transport)) {
            appendTrafficLog("SYS", "No active response transport");
            return;
        }
        handler.post(() -> {
            int chunkSize = Math.max(BLE_CHUNK_DEFAULT, bleChunkBytes);
            for (int offset = 0; offset < response.length; offset += chunkSize) {
                int length = Math.min(chunkSize, response.length - offset);
                byte[] chunk = new byte[length];
                System.arraycopy(response, offset, chunk, 0, length);
                txQueue.add(chunk);
            }
            if (!txRunning) {
                txRunning = true;
                sendNextChunk();
            }
        });
    }

    // Envoie le prochain fragment.
    private void sendNextChunk() {
        if (txAwaitingAck) return;
        BluetoothGatt currentGatt = gatt;
        BluetoothGattCharacteristic characteristic = txCharacteristic;
        if (txCurrentChunk == null) txCurrentChunk = txQueue.poll();
        byte[] chunk = txCurrentChunk;
        if (chunk == null) {
            txRunning = false;
            txRetryCount = 0;
            return;
        }
        if (currentGatt == null || characteristic == null || !bleReady) {
            txQueue.clear();
            txCurrentChunk = null;
            txRunning = false;
            txRetryCount = 0;
            return;
        }

        int writeType = responseWriteType(characteristic);
        if (writeType < 0) {
            txQueue.clear();
            txCurrentChunk = null;
            txRunning = false;
            txRetryCount = 0;
            handleDisconnect(currentGatt, "BLE write unavailable. Reconnecting…");
            return;
        }
        boolean accepted;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            accepted = currentGatt.writeCharacteristic(characteristic, chunk, writeType)
                    == BluetoothGatt.GATT_SUCCESS;
        } else {
            accepted = writeCharacteristicLegacy(currentGatt, characteristic, chunk, writeType);
        }
        if (!accepted) {
            retryBleChunk(currentGatt, "BLE write busy");
            return;
        }
        if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
            txAwaitingAck = true;
            handler.removeCallbacks(bleWriteAckWatchdog);
            handler.postDelayed(bleWriteAckWatchdog, BLE_WRITE_ACK_TIMEOUT_MS);
        } else {
            txCurrentChunk = null;
            txRetryCount = 0;
            handler.postDelayed(this::sendNextChunk, BLE_CHUNK_DELAY_MS);
        }
    }

    // A transient GATT busy/rejected status must not tear down a healthy link.
    private void retryBleChunk(BluetoothGatt currentGatt, String reason) {
        txAwaitingAck = false;
        if (currentGatt != gatt || stopping) return;
        if (txRetryCount < BLE_TX_RETRY_MAX) {
            txRetryCount++;
            appendTrafficLog("BLE", reason + "; retry " + txRetryCount + "/" + BLE_TX_RETRY_MAX);
            long delay = Math.min(600L, BLE_TX_RETRY_DELAY_MS * txRetryCount);
            handler.postDelayed(this::sendNextChunk, delay);
            return;
        }
        txQueue.clear();
        txCurrentChunk = null;
        txRunning = false;
        txRetryCount = 0;
        appendTrafficLog("BLE", "Write pipeline stalled; requesting clean reconnect");
        try {
            currentGatt.disconnect();
        } catch (RuntimeException error) {
            handleDisconnect(currentGatt, "BLE transfer stalled. Reconnecting…");
            return;
        }
        handler.postDelayed(() -> {
            if (gatt == currentGatt) {
                handleDisconnect(currentGatt, "BLE transfer stalled. Reconnecting…");
            }
        }, 1800L);
    }

    // Décrit une requête FN.
    private static final class Request {
        final long session;
        final long id;
        final int priority;
        final String language;
        final String service;
        final String argument;
        final String transport;

        // Initialise une requête FN.
        Request(long session, long id, int priority,
                String language, String service, String argument,
                String transport) {
            this.session = session;
            this.id = id;
            this.priority = priority;
            this.language = language;
            this.service = service;
            this.argument = argument;
            this.transport = transport;
        }
    }


    // Analyse une requête FN2.
    private Request parseRequest(String line, String transport) throws UserError {
        return parseRequestV2(line.trim(), transport);
    }

    // Analyse une requête FN2.
    private Request parseRequestV2(String line, String transport) throws UserError {
        int split = line.lastIndexOf('|');
        if (split <= 0 || line.length() - split - 1 != 8) {
            throw new UserError("Invalid FN2 checksum");
        }
        String payload = line.substring(0, split);
        String checksum = line.substring(split + 1);
        CRC32 crc = new CRC32();
        crc.update(payload.getBytes(StandardCharsets.US_ASCII));
        if (!String.format(Locale.ROOT, "%08X", crc.getValue()).equals(checksum)) {
            throw new UserError("FN2 checksum mismatch");
        }
        String[] fields = payload.split("\\|", -1);
        if (fields.length != 7 || !"FN2".equals(fields[0]) || !"REQ".equals(fields[1])) {
            throw new UserError("Invalid FN2 frame");
        }
        long session = parseUnsigned(fields[2], "session id");
        long id = parseUnsigned(fields[3], "request id");
        int priority;
        try {
            priority = Integer.parseInt(fields[4]);
        } catch (NumberFormatException exception) {
            throw new UserError("Invalid priority");
        }
        if (priority < 0 || priority > 3) throw new UserError("Invalid priority");
        validateService(fields[5]);
        return localizedRequest(session, id, priority, fields[5], decodeHex(fields[6]), transport);
    }


    // Applique la langue de la requête.
    private Request localizedRequest(
            long session, long id, int priority, String service,
            String rawArgument, String transport) {
        String language = "fr";
        String argument = rawArgument == null ? "" : rawArgument;
        if (argument.length() >= 4 && argument.charAt(0) == '@' && argument.charAt(3) == ':') {
            String candidate = argument.substring(1, 3).toLowerCase(Locale.ROOT);
            if (candidate.equals("en") || candidate.equals("fr")) {
                language = candidate;
                argument = argument.substring(4);
            }
        }
        return new Request(session, id, priority, language, service, argument, transport);
    }

    // Analyse un entier positif.
    private long parseUnsigned(String value, String field) throws UserError {
        long result;
        try {
            result = Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new UserError("Invalid " + field);
        }
        if (result < 0 || result > 0xFFFFFFFFL) throw new UserError("Invalid " + field);
        return result;
    }

    // Valide l’identifiant du service.
    private void validateService(String service) throws UserError {
        if (!service.matches("[a-z0-9_]{1,32}")) throw new UserError("Invalid service");
    }

    // Traduit une erreur stable.
    private static String localizeError(String value, String language) {
        String result = value == null ? "" : value;
        if (AndroidFeatureSupport.isFrench(language)) {
            return result
                    .replace("Service unavailable on this companion. Refresh features.",
                            "Service indisponible sur ce compagnon. Actualise les fonctions.")
                    .replace("Invalid", "Invalide")
                    .replace("Unknown", "Inconnu")
                    .replace("not found", "introuvable")
                    .replace("unavailable", "indisponible")
                    .replace("No result", "Aucun resultat")
                    .replace("Enter", "Indique")
                    .replace("Too many", "Trop de");
        }
        return result
                .replace("URL HTTP ou HTTPS invalide", "Invalid HTTP or HTTPS URL")
                .replace("Erreur reseau", "Network error")
                .replace("Reponse JSON invalide", "Invalid JSON response")
                .replace("Recherche Web indisponible", "Web search unavailable")
                .replace("Resultat Web invalide", "Invalid Web result")
                .replace("Resultat Web expire", "Expired Web result")
                .replace("Page sans texte lisible", "Page has no readable text")
                .replace("Page trop volumineuse", "Page too large")
                .replace("Indique une URL", "Enter a URL")
                .replace("Indique une ville", "Enter a city")
                .replace("Indique une recherche", "Enter a search")
                .replace("Ville introuvable", "City not found")
                .replace("Ville invalide", "Invalid city")
                .replace("Aucun article trouve", "No article found")
                .replace("Article invalide", "Invalid article")
                .replace("Article sans resume disponible", "No summary available")
                .replace("Domaine introuvable", "Domain not found")
                .replace("Adresse locale interdite", "Local address forbidden")
                .replace("Adresse privee ou locale interdite", "Private or local address forbidden")
                .replace("Port invalide", "Invalid port")
                .replace("Traduction indisponible", "Translation unavailable")
                .replace("Definition introuvable", "Definition not found")
                .replace("Application inconnue", "Unknown application")
                .replace("Aucun resultat correspondant", "No matching result")
                .replace("Saisis au moins", "Enter at least");
    }

    // Traduit un résultat stable.
    private static ServiceResult localizeResult(ServiceResult result, String language) {
        if (result == null) return null;
        boolean protocol = result.body.startsWith("FNSEL1\n")
                || result.body.startsWith("FNWEB1\n")
                || result.body.startsWith("FNLOC1\n")
                || result.body.startsWith("FNWIKI1\n")
                || result.body.startsWith("FNFEED1\n");
        if (AndroidFeatureSupport.isFrench(language)) {
            if (protocol) return result;
            String title = result.title
                    .replace("Online apps", "Applications en ligne")
                    .replace("Web results", "Resultats Web")
                    .replace("Cached Web results", "Resultats Web en cache")
                    .replace("Phone notifications", "Notifications du telephone")
                    .replace("Phone clipboard", "Presse-papiers du telephone")
                    .replace("Phone location", "Localisation du telephone")
                    .replace("Phone status", "Etat du telephone")
                    .replace("Find phone", "Faire sonner le telephone")
                    .replace("Copy to phone", "Copier vers le telephone")
                    .replace("Copied to phone", "Copie vers le telephone")
                    .replace("Send URL", "Envoyer une URL")
                    .replace("URL sent to phone", "URL envoyee au telephone")
                    .replace("Calculator", "Calculatrice")
                    .replace("Unit converter", "Convertisseur")
                    .replace("Currency", "Devises")
                    .replace("World time", "Heure mondiale");
            String body = result.body
                    .replace("Battery: ", "Batterie : ")
                    .replace(" charging", " en charge")
                    .replace("Network: ", "Reseau : ")
                    .replace("Bluetooth: ", "Bluetooth : ")
                    .replace("Notifications: ", "Notifications : ")
                    .replace("Location: ", "Localisation : ")
                    .replace("Trusted Flipper: ", "Flipper de confiance : ")
                    .replace("Unknown", "Inconnu")
                    .replace("Offline", "Hors ligne")
                    .replace("Allowed", "Autorisee")
                    .replace("First connection", "Premiere connexion")
                    .replace("Just now", "A l'instant")
                    .replace(" min ago", " min")
                    .replace(" h ago", " h")
                    .replace("Source: ", "Source : ")
                    .replace("Cached page", "Page en cache");
            return new ServiceResult(title, body);
        }
        if (protocol) return result;
        String title = result.title
                .replace("Applications en ligne", "Online apps")
                .replace("Resultats Web en cache", "Cached Web results")
                .replace("Resultats Web", "Web results")
                .replace("Calculatrice", "Calculator")
                .replace("Convertisseur", "Unit converter")
                .replace("Devises", "Currency")
                .replace("Heure mondiale", "World time")
                .replace("Erreur interne", "Internal error")
                .replace("Erreur", "Error");
        String body = result.body
                .replace("Temperature : ", "Temperature: ")
                .replace("Ressenti : ", "Feels like: ")
                .replace("Pluie max : ", "Max rain: ")
                .replace("Vent : ", "Wind: ")
                .replace("Serveur : ", "Server: ")
                .replace("Temps : ", "Time: ")
                .replace("Emetteur : ", "Issuer: ")
                .replace("Expiration : ", "Expires: ")
                .replace("Jours restants : ", "Days left: ")
                .replace("Adresses : ", "Addresses: ")
                .replace("Ouverts : ", "Open: ")
                .replace("Trouves : ", "Found: ")
                .replace("Aucune cible enregistree", "No saved target")
                .replace("Aucun resultat recent", "No recent result")
                .replace("Aucun monitor configure", "No monitor configured")
                .replace("Aucune alerte active", "No active alert")
                .replace("Historique efface", "History cleared")
                .replace("Alertes acquittees", "Alerts acknowledged")
                .replace("Page en cache", "Cached page")
                .replace("Source : ", "Source: ");
        return new ServiceResult(title, body);
    }

    // Construit la réponse FN.
    private byte[] buildResponse(Request request, String status, String title, String body) {
        String titleHex = encodeHex(AndroidFeatureSupport.truncateUtf8(AndroidFeatureSupport.asciiText(title), 64));
        String bodyHex = encodeHex(AndroidFeatureSupport.truncateUtf8(AndroidFeatureSupport.asciiText(body), 1024));
        String payload = "FN2|RES|" + request.session + "|" + request.id + "|"
                + status + "|" + titleHex + "|" + bodyHex;
        CRC32 crc = new CRC32();
        crc.update(payload.getBytes(StandardCharsets.US_ASCII));
        String line = payload + "|" + String.format(Locale.ROOT, "%08X", crc.getValue()) + "\n";
        return line.getBytes(StandardCharsets.US_ASCII);
    }

    // Décode une valeur hexadécimale.
    private String decodeHex(String hex) throws UserError {
        if ((hex.length() & 1) != 0 || !hex.matches("[0-9A-Fa-f]*")) {
            throw new UserError("Hexadécimal invalide");
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        if (bytes.length > 192) throw new UserError("Argument trop long");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // Encode une valeur hexadécimale.
    private String encodeHex(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            result.append(String.format(Locale.ROOT, "%02X", current & 0xFF));
        }
        return result.toString();
    }

    // Retourne le registre des services.
    private ServiceRegistry serviceRegistry() {
        featureModules();
        if (serviceRegistry == null) serviceRegistry = AndroidFeatureRegistry.create(features);
        return serviceRegistry;
    }

    // Distribue la requête à un module.
    ServiceResult dispatchFeature(String service, String argument) throws Exception {
        String language = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .getString(KEY_LANGUAGE, "fr");
        return dispatch(service, argument, language == null ? "fr" : language);
    }
    // Distribue la requête au bon service.
    private ServiceResult dispatch(String service, String argument, String language) throws Exception {
        ServiceResult result = serviceRegistry().dispatch(service, argument, language);
        if (result == null) throw UserError.serviceNotFound();
        return result;
    }

    // Retourne les modules Android.
    private AndroidFeatures featureModules() {
        if (featureState == null) {
            featureState = new AndroidStateStore(
                    getSharedPreferences(PREFERENCES, MODE_PRIVATE));
        }
        if (features == null) features = new AndroidFeatures(this, featureState);
        return features;
    }

    // Retourne le stockage Android.
    private AndroidStateStore featureState() {
        featureModules();
        return featureState;
    }

    // Planifie une reconnexion BLE.
    void requestBleReconnect(long delayMs) {
        handler.postDelayed(() -> {
            BluetoothGatt current = gatt;
            if (current != null) current.disconnect();
            else startScanWhenReady();
        }, Math.max(0L, delayMs));
    }

    // Publie une alerte de fonctionnalité.
    void publishFeatureAlert(String title, String body) {
        Request event = new Request(0L, 0L, 1, "fr", "alert", "", currentTransport());
        String payload = ErrorProtocol.payload("ALERT", "event", "", true, body, body);
        enqueueResponse(buildResponse(event, "ERR", title, payload));
        NotificationManager manager = getSystemService(NotificationManager.class);
        Notification notification = new Notification.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_flipnet)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .build();
        manager.notify(ALERT_NOTIFICATION_ID, notification);
    }

    // Indique si la liaison BLE est active.
    boolean isBleLinked() {
        return bleReady && gatt != null && txCharacteristic != null && rxCharacteristic != null;
    }

    // Indique si le GATT est connecté.
    boolean isBleConnected() {
        return gatt != null;
    }

    // Indique si une recherche BLE est active.
    boolean isScanning() {
        return scanning;
    }

    // Retourne la taille de la file d’envoi.
    int txQueueSize() {
        synchronized (txQueue) {
            return txQueue.size();
        }
    }

    // Maintient le service après fermeture de l’écran.
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        boolean enabled = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
        if (enabled && !stopping) {
            Intent restart = new Intent(getApplicationContext(), FlipNetService.class);
            restart.setAction(ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getApplicationContext().startForegroundService(restart);
            } else {
                getApplicationContext().startService(restart);
            }
        }
        super.onTaskRemoved(rootIntent);
    }

    // Libère le composant Android.
    @Override
    public void onDestroy() {
        stopping = true;
        handler.removeCallbacksAndMessages(null);
        handler.removeCallbacks(rssiScheduler);
        handler.removeCallbacks(bleLinkScheduler);
        stopScan();
        if (usbTransport != null) usbTransport.stop();
        closeGatt();
        if (bluetoothReceiverRegistered) {
            unregisterReceiver(bluetoothReceiver);
            bluetoothReceiverRegistered = false;
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (fileTransferManager != null) fileTransferManager.close();
        persistUiState("FlipNet stopped.", "", false, false);
        worker.shutdownNow();
        super.onDestroy();
    }
}
