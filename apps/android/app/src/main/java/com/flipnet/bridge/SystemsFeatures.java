package com.flipnet.bridge;

import android.app.ActivityManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.StatFs;
import android.os.SystemClock;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.Locale;

// Regroupe les états système Android.
final class SystemsFeatures {
    private final FlipNetService gateway;

    // Initialise les fonctions système.
    SystemsFeatures(FlipNetService gateway) {
        this.gateway = gateway;
    }

    // Résume l’état de l’hôte.
    ServiceResult hostStatus() {
        String model = (Build.MANUFACTURER + " " + Build.MODEL).trim();
        long seconds = SystemClock.elapsedRealtime() / 1000L;
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3_600L;

        ActivityManager manager = gateway.getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        if (manager != null) manager.getMemoryInfo(memory);

        StatFs stat = new StatFs(gateway.getFilesDir().getAbsolutePath());
        long diskTotal = stat.getTotalBytes();
        long diskUsed = diskTotal - stat.getAvailableBytes();
        int diskPercent = diskTotal > 0 ? (int) ((diskUsed * 100L) / diskTotal) : 0;

        BatteryManager battery = gateway.getSystemService(BatteryManager.class);
        int batteryLevel = battery == null ? -1
                : battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

        String body = "Android : " + Build.VERSION.RELEASE
                + "\nUptime : " + days + "j " + hours + "h"
                + "\nCPU : " + Runtime.getRuntime().availableProcessors() + " cœur(s)"
                + "\nRAM : " + formatGiB(memory.totalMem - memory.availMem)
                + " / " + formatGiB(memory.totalMem)
                + "\nDisque : " + diskPercent + "%"
                + "\nBatterie : " + (batteryLevel >= 0 ? batteryLevel + "%" : "?");
        return new ServiceResult(model.isEmpty() ? "Android" : model, body);
    }

    // Résume la sécurité Android.
    ServiceResult securityStatus() {
        android.app.KeyguardManager keyguard =
                (android.app.KeyguardManager) gateway.getSystemService(android.content.Context.KEYGUARD_SERVICE);
        boolean secure = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && keyguard != null
                && keyguard.isDeviceSecure();
        String patch = Build.VERSION.SECURITY_PATCH == null || Build.VERSION.SECURITY_PATCH.isEmpty()
                ? "unknown" : Build.VERSION.SECURITY_PATCH;
        String body = "Android: " + Build.VERSION.RELEASE
                + "\nSecurity patch: " + patch
                + "\nDevice lock: " + (secure ? "Secure" : "Not secure")
                + "\nApp sandbox: Active"
                + "\nBLE permission: " + (gateway.hasBluetoothPermissions() ? "Granted" : "Missing");
        return new ServiceResult("Security State", body);
    }

    // Résume les services Android.
    ServiceResult servicesStatus() {
        boolean enabled = gateway.getSharedPreferences(FlipNetService.PREFERENCES, android.content.Context.MODE_PRIVATE)
                .getBoolean(FlipNetService.KEY_ENABLED, false);
        boolean linked = gateway.isBleLinked();
        String body = "FlipNet service: Running"
                + "\nAutostart: " + (enabled ? "Enabled" : "Disabled")
                + "\nBLE link: " + (linked ? "Connected" : "Waiting")
                + "\nScanner: " + (gateway.isScanning() ? "Active" : "Idle")
                + "\nMonitor loop: Active";
        return new ServiceResult("System Services", body);
    }

    // Résume les protections réseau.
    ServiceResult networkGuard() {
        android.net.ConnectivityManager manager =
                (android.net.ConnectivityManager) gateway.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        android.net.Network network = manager == null ? null : manager.getActiveNetwork();
        android.net.NetworkCapabilities caps = manager == null || network == null
                ? null : manager.getNetworkCapabilities(network);
        boolean vpn = caps != null
                && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN);
        String transport = "Offline";
        if (caps != null) {
            if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) transport = "Wi-Fi";
            else if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) transport = "Cellular";
            else if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)) transport = "Ethernet";
            else transport = "Connected";
        }
        String body = "Platform firewall: Android sandbox"
                + "\nNetwork: " + transport
                + "\nVPN: " + (vpn ? "Active" : "Inactive")
                + "\nBLE bridge: " + (gateway.isBleConnected() ? "Linked" : "Waiting");
        return new ServiceResult("Network Guard", body);
    }

    // Résume les charges de travail Android.
    ServiceResult workloadsStatus() {
        ActivityManager manager = gateway.getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        if (manager != null) manager.getMemoryInfo(memory);
        int queued = gateway.txQueueSize();
        String body = "Runtime: Android"
                + "\nBridge process: Active"
                + "\nWorkers: 1"
                + "\nTX queue: " + queued
                + "\nFree RAM: " + formatGiB(memory.availMem)
                + "\nContainers: Not available";
        return new ServiceResult("Workloads", body);
    }

    // Résume les mises à jour Android.
    ServiceResult updatesStatus() {
        String version = appVersion();
        String patch = Build.VERSION.SECURITY_PATCH == null || Build.VERSION.SECURITY_PATCH.isEmpty()
                ? "unknown" : Build.VERSION.SECURITY_PATCH;
        String body = "Android: " + Build.VERSION.RELEASE + " | API " + Build.VERSION.SDK_INT
                + "\nSecurity patch: " + patch
                + "\nFlipNet app: " + version
                + "\nSystem updates: Use Android Settings";
        return new ServiceResult("System Updates", body);
    }

    // Retourne la version de l’application.
    @SuppressWarnings("deprecation")
    private String appVersion() {
        try {
            PackageInfo info;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                info = gateway.getPackageManager().getPackageInfo(
                        gateway.getPackageName(), PackageManager.PackageInfoFlags.of(0));
            } else {
                info = gateway.getPackageManager().getPackageInfo(gateway.getPackageName(), 0);
            }
            return info.versionName == null ? "unknown" : info.versionName;
        } catch (PackageManager.NameNotFoundException error) {
            return "unknown";
        }
    }

    // Formate une taille en gibioctets.
    private String formatGiB(long value) {
        return String.format(Locale.ROOT, "%.1fG", value / 1073741824.0);
    }
}
