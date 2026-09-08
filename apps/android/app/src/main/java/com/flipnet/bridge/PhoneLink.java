package com.flipnet.bridge;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Expose les fonctions liées au téléphone.
final class PhoneLink {
    static final String KEY_NOTIFICATION_PACKAGES = "phone_notification_packages";
    static final String KEY_NOTIFICATIONS = "phone_notifications";
    static final String KEY_CLIPBOARD = "phone_clipboard";
    static final String KEY_CLIPBOARD_AT = "phone_clipboard_at";
    static final String KEY_LOCATION_LAT = "phone_location_lat";
    static final String KEY_LOCATION_LON = "phone_location_lon";
    static final String KEY_LOCATION_ACCURACY = "phone_location_accuracy";
    static final String KEY_LOCATION_AT = "phone_location_at";
    static final String KEY_TRUSTED_DEVICE = "trusted_flipper_address";

    private static final String CHANNEL_ID = "flipnet_phone_link";
    private static final int URL_NOTIFICATION_ID = 2401;
    private static final int MAX_NOTIFICATIONS = 20;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static Ringtone activeRingtone;
    private static Vibrator activeVibrator;

    // Initialise les fonctions Phone Link.
    private PhoneLink() {
    }

    // Décrit un résultat Phone Link.
    static final class PhoneResult {
        final String title;
        final String body;

        // Initialise un résultat Phone Link.
        PhoneResult(String title, String body) {
            this.title = title;
            this.body = body;
        }
    }

    // Indique si Phone Link gère le service.
    static boolean handles(String appId) {
        return appId != null && (appId.equals("phone_status")
                || appId.equals("phone_notifications")
                || appId.equals("phone_clipboard")
                || appId.equals("phone_copy")
                || appId.equals("phone_url")
                || appId.equals("phone_location")
                || appId.equals("phone_find"));
    }

    // Exécute la fonction téléphonique demandée.
    static PhoneResult run(
            Context context, String appId, String value, String language) throws Exception {
        PhoneResult result;
        switch (appId) {
            case "phone_status":
                result = phoneStatus(context);
                break;
            case "phone_notifications":
                result = phoneNotifications(context);
                break;
            case "phone_clipboard":
                result = phoneClipboard(context);
                break;
            case "phone_copy":
                result = copyToPhone(context, value);
                break;
            case "phone_url":
                result = sendUrl(context, value);
                break;
            case "phone_location":
                result = phoneLocation(context);
                break;
            case "phone_find":
                result = findPhone(context);
                break;
            default:
                throw new IllegalArgumentException("Unknown Phone Link app");
        }
        return localize(result, language);
    }

    // Choisit le texte Phone Link localisé.
    private static PhoneResult localize(PhoneResult result, String language) {
        if (!"fr".equalsIgnoreCase(language)) return result;
        String title = result.title
                .replace("Phone notifications", "Notifications du telephone")
                .replace("Phone clipboard", "Presse-papiers du telephone")
                .replace("Copy to phone", "Copier vers le telephone")
                .replace("Copied to phone", "Copie vers le telephone")
                .replace("Send URL", "Envoyer une URL")
                .replace("URL sent to phone", "URL envoyee au telephone")
                .replace("Phone location", "Localisation du telephone")
                .replace("Find phone", "Faire sonner le telephone");
        String body = result.body
                .replace("Enable notification access in the FlipNet Android app.",
                        "Autorise les notifications dans l application Android FlipNet.")
                .replace("Choose notification apps in the FlipNet Android app.",
                        "Choisis les applications autorisees dans FlipNet Android.")
                .replace("No selected notification received yet.",
                        "Aucune notification selectionnee recue.")
                .replace("Open FlipNet Android and tap Sync clipboard.",
                        "Ouvre FlipNet Android et synchronise le presse-papiers.")
                .replace("Text is empty.", "Le texte est vide.")
                .replace("Clipboard unavailable.", "Presse-papiers indisponible.")
                .replace("Invalid URL.", "URL invalide.")
                .replace("Only public HTTP or HTTPS URLs are accepted.",
                        "Seules les URL HTTP ou HTTPS publiques sont acceptees.")
                .replace("Tap the Android notification to open:",
                        "Touche la notification Android pour ouvrir :")
                .replace("Allow location in the FlipNet Android app.",
                        "Autorise la localisation dans FlipNet Android.")
                .replace("Location unavailable. Enable Android location and try again.",
                        "Localisation indisponible. Active-la puis reessaie.")
                .replace("Latitude: ", "Latitude : ")
                .replace("Longitude: ", "Longitude : ")
                .replace("Accuracy: ", "Precision : ")
                .replace("Updated: ", "Mise a jour : ")
                .replace("The phone will ring and vibrate for 15 seconds.",
                        "Le telephone sonnera et vibrera pendant 15 secondes.");
        return new PhoneResult(title, body);
    }

    // Retourne les préférences Phone Link.
    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(FlipNetService.PREFERENCES, Context.MODE_PRIVATE);
    }

    // Liste les applications de notification.
    static Set<String> selectedNotificationPackages(Context context) {
        Set<String> selected = preferences(context).getStringSet(
                KEY_NOTIFICATION_PACKAGES,
                Collections.emptySet());
        return selected == null ? Collections.emptySet() : new HashSet<>(selected);
    }

    // Enregistre les applications de notification.
    static void setSelectedNotificationPackages(Context context, Set<String> packages) {
        preferences(context).edit()
                .putStringSet(KEY_NOTIFICATION_PACKAGES, new HashSet<>(packages))
                .apply();
    }

    // Indique si les notifications sont autorisées.
    static boolean hasNotificationAccess(Context context) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                "enabled_notification_listeners");
        if (enabled == null || enabled.isEmpty()) return false;
        ComponentName expected = new ComponentName(context, PhoneNotificationListener.class);
        String flat = expected.flattenToString();
        String shortFlat = expected.flattenToShortString();
        for (String item : enabled.split(":")) {
            if (item.equals(flat) || item.equals(shortFlat)) return true;
        }
        return false;
    }

    // Mémorise une notification autorisée.
    static void recordNotification(
            Context context,
            String packageName,
            String appLabel,
            String title,
            String text,
            long timestamp) {
        if (!selectedNotificationPackages(context).contains(packageName)) return;
        String cleanTitle = clean(title, 100);
        String cleanText = clean(text, 220);
        if (cleanTitle.isEmpty() && cleanText.isEmpty()) return;
        try {
            SharedPreferences preferences = preferences(context);
            String raw = preferences.getString(KEY_NOTIFICATIONS, "[]");
            JSONArray previous = raw == null ? new JSONArray() : new JSONArray(raw);
            JSONArray updated = new JSONArray();
            JSONObject item = new JSONObject();
            item.put("package", packageName);
            item.put("app", clean(appLabel, 48));
            item.put("title", cleanTitle);
            item.put("text", cleanText);
            item.put("timestamp", timestamp);
            updated.put(item);
            for (int index = 0; index < Math.min(MAX_NOTIFICATIONS - 1, previous.length()); index++) {
                updated.put(previous.get(index));
            }
            preferences.edit().putString(KEY_NOTIFICATIONS, updated.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    // Mémorise le presse-papiers.
    static boolean captureClipboard(Context context) {
        ClipboardManager clipboard = context.getSystemService(ClipboardManager.class);
        if (clipboard == null || !clipboard.hasPrimaryClip()) return false;
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return false;
        CharSequence text = clip.getItemAt(0).coerceToText(context);
        if (text == null) return false;
        String value = clean(text.toString(), 700);
        if (value.isEmpty()) return false;
        preferences(context).edit()
                .putString(KEY_CLIPBOARD, value)
                .putLong(KEY_CLIPBOARD_AT, System.currentTimeMillis())
                .apply();
        return true;
    }

    // Résume le presse-papiers.
    static String clipboardSummary(Context context) {
        SharedPreferences preferences = preferences(context);
        String value = preferences.getString(KEY_CLIPBOARD, "");
        if (value == null || value.isEmpty()) return "Not synced";
        long at = preferences.getLong(KEY_CLIPBOARD_AT, 0L);
        String prefix = ageLabel(at);
        return prefix + " · " + clean(value, 34);
    }

    // Indique si la position est autorisée.
    static boolean hasLocationPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
    }

    // Actualise la position du téléphone.
    static boolean updateLocationSnapshot(Context context) {
        if (!hasLocationPermission(context)) return false;
        LocationManager manager = context.getSystemService(LocationManager.class);
        if (manager == null) return false;
        Location best = null;
        try {
            for (String provider : manager.getProviders(true)) {
                Location candidate = manager.getLastKnownLocation(provider);
                if (candidate == null) continue;
                if (best == null || candidate.getTime() > best.getTime()
                        || (candidate.getTime() == best.getTime()
                        && candidate.getAccuracy() < best.getAccuracy())) {
                    best = candidate;
                }
            }
        } catch (SecurityException ignored) {
            return false;
        }
        if (best == null) return false;
        preferences(context).edit()
                .putString(KEY_LOCATION_LAT, Double.toString(best.getLatitude()))
                .putString(KEY_LOCATION_LON, Double.toString(best.getLongitude()))
                .putFloat(KEY_LOCATION_ACCURACY, best.getAccuracy())
                .putLong(KEY_LOCATION_AT, best.getTime())
                .apply();
        return true;
    }

    // Résume la dernière position.
    static String locationSummary(Context context) {
        SharedPreferences preferences = preferences(context);
        String latitude = preferences.getString(KEY_LOCATION_LAT, "");
        if (latitude == null || latitude.isEmpty()) {
            return hasLocationPermission(context) ? "No location yet" : "Permission required";
        }
        return ageLabel(preferences.getLong(KEY_LOCATION_AT, 0L));
    }

    // Retourne l’appareil BLE approuvé.
    static String trustedDevice(Context context) {
        String value = preferences(context).getString(KEY_TRUSTED_DEVICE, "");
        return value == null ? "" : value;
    }

    // Approuve un appareil BLE.
    static void trustDevice(Context context, String address) {
        if (address == null || address.trim().isEmpty()) return;
        preferences(context).edit().putString(KEY_TRUSTED_DEVICE, address.trim()).apply();
    }

    // Retire l’appareil BLE approuvé.
    static void clearTrustedDevice(Context context) {
        preferences(context).edit().remove(KEY_TRUSTED_DEVICE).apply();
    }

    // Lit l’état de la batterie.
    private static Intent batteryIntent(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED);
        }
        return batteryIntentLegacy(context, filter);
    }

    // Lit la batterie sur un ancien Android.
    @SuppressWarnings("deprecation")
    private static Intent batteryIntentLegacy(Context context, IntentFilter filter) {
        return context.registerReceiver(null, filter);
    }

    // Résume l’état du téléphone.
    private static PhoneResult phoneStatus(Context context) {
        Intent battery = batteryIntent(context);
        int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int percent = level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : -1;
        int status = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        int temperature = battery == null ? 0 : battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);

        String network = activeNetwork(context);
        BluetoothManager bluetoothManager = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = bluetoothManager == null ? null : bluetoothManager.getAdapter();
        String bluetooth = adapter != null && safeBluetoothEnabled(adapter) ? "On" : "Off";
        int selected = selectedNotificationPackages(context).size();
        String trusted = trustedDevice(context);

        StringBuilder body = new StringBuilder();
        body.append("Battery: ").append(percent >= 0 ? percent + "%" : "Unknown")
                .append(charging ? " charging" : "")
                .append('\n');
        if (temperature > 0) {
            body.append("Temp: ")
                    .append(String.format(Locale.ROOT, "%.1f C", temperature / 10.0))
                    .append('\n');
        }
        body.append("Network: ").append(network).append('\n')
                .append("Bluetooth: ").append(bluetooth).append('\n')
                .append("Notifications: ")
                .append(hasNotificationAccess(context) ? "On" : "Off")
                .append(" (").append(selected).append(" apps)\n")
                .append("Location: ")
                .append(hasLocationPermission(context) ? "Allowed" : "Off")
                .append('\n')
                .append("Trusted Flipper: ")
                .append(trusted.isEmpty() ? "First connection" : "Yes");
        return new PhoneResult(Build.MANUFACTURER + " " + Build.MODEL, body.toString());
    }

    // Liste les notifications retenues.
    private static PhoneResult phoneNotifications(Context context) throws Exception {
        if (!hasNotificationAccess(context)) {
            return new PhoneResult("Notifications", "Enable notification access in the FlipNet Android app.");
        }
        if (selectedNotificationPackages(context).isEmpty()) {
            return new PhoneResult("Notifications", "Choose notification apps in the FlipNet Android app.");
        }
        String raw = preferences(context).getString(KEY_NOTIFICATIONS, "[]");
        JSONArray items = raw == null ? new JSONArray() : new JSONArray(raw);
        if (items.length() == 0) {
            return new PhoneResult("Notifications", "No selected notification received yet.");
        }
        StringBuilder body = new StringBuilder();
        for (int index = 0; index < Math.min(8, items.length()); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item == null) continue;
            if (body.length() > 0) body.append("\n\n");
            body.append(shortTime(item.optLong("timestamp", 0L)))
                    .append(" ")
                    .append(item.optString("app", "App"));
            String title = item.optString("title", "");
            String text = item.optString("text", "");
            if (!title.isEmpty()) body.append("\n").append(title);
            if (!text.isEmpty()) body.append("\n").append(text);
            if (body.length() > 930) break;
        }
        return new PhoneResult("Phone notifications", body.toString());
    }

    // Lit le presse-papiers du téléphone.
    private static PhoneResult phoneClipboard(Context context) {
        SharedPreferences preferences = preferences(context);
        String value = preferences.getString(KEY_CLIPBOARD, "");
        if (value == null || value.isEmpty()) {
            return new PhoneResult("Phone clipboard", "Open FlipNet Android and tap Sync clipboard.");
        }
        long at = preferences.getLong(KEY_CLIPBOARD_AT, 0L);
        return new PhoneResult("Phone clipboard", ageLabel(at) + "\n\n" + value);
    }

    // Écrit dans le presse-papiers.
    private static PhoneResult copyToPhone(Context context, String value) {
        String text = clean(value, 700);
        if (text.isEmpty()) return new PhoneResult("Copy to phone", "Text is empty.");
        ClipboardManager clipboard = context.getSystemService(ClipboardManager.class);
        if (clipboard == null) return new PhoneResult("Copy to phone", "Clipboard unavailable.");
        clipboard.setPrimaryClip(ClipData.newPlainText("FlipNet", text));
        preferences(context).edit()
                .putString(KEY_CLIPBOARD, text)
                .putLong(KEY_CLIPBOARD_AT, System.currentTimeMillis())
                .apply();
        return new PhoneResult("Copied to phone", text);
    }

    // Ouvre une URL sur le téléphone.
    private static PhoneResult sendUrl(Context context, String value) {
        String text = value == null ? "" : value.trim();
        Uri uri;
        try {
            uri = Uri.parse(text);
        } catch (RuntimeException error) {
            return new PhoneResult("Send URL", "Invalid URL.");
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (host == null || host.isEmpty()
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || !isPublicHost(host)) {
            return new PhoneResult("Send URL", "Only public HTTP or HTTPS URLs are accepted.");
        }
        ensureChannel(context);
        Intent openIntent = new Intent(Intent.ACTION_VIEW, uri);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent open = PendingIntent.getActivity(
                context,
                2401,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_flipnet)
                .setContentTitle("URL received from FlipNet")
                .setContentText(host)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(URL_NOTIFICATION_ID, notification);
        return new PhoneResult("URL sent to phone", "Tap the Android notification to open:\n" + text);
    }

    // Retourne la dernière position.
    private static PhoneResult phoneLocation(Context context) {
        if (!hasLocationPermission(context)) {
            return new PhoneResult("Phone location", "Allow location in the FlipNet Android app.");
        }
        updateLocationSnapshot(context);
        SharedPreferences preferences = preferences(context);
        String latitude = preferences.getString(KEY_LOCATION_LAT, "");
        String longitude = preferences.getString(KEY_LOCATION_LON, "");
        if (latitude == null || latitude.isEmpty() || longitude == null || longitude.isEmpty()) {
            return new PhoneResult("Phone location", "Location unavailable. Enable Android location and try again.");
        }
        float accuracy = preferences.getFloat(KEY_LOCATION_ACCURACY, 0f);
        long at = preferences.getLong(KEY_LOCATION_AT, 0L);
        String body = "Latitude: " + latitude
                + "\nLongitude: " + longitude
                + (accuracy > 0f ? "\nAccuracy: " + Math.round(accuracy) + " m" : "")
                + "\nUpdated: " + ageLabel(at)
                + "\nMaps: https://maps.google.com/?q=" + latitude + "," + longitude;
        return new PhoneResult("Phone location", body);
    }

    // Fait sonner le téléphone.
    private static PhoneResult findPhone(Context context) {
        MAIN_HANDLER.post(() -> {
            stopFindPhone();
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            if (ringtoneUri == null) {
                ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            activeRingtone = ringtoneUri == null
                    ? null : RingtoneManager.getRingtone(context, ringtoneUri);
            if (activeRingtone != null) activeRingtone.play();
            activeVibrator = context.getSystemService(Vibrator.class);
            if (activeVibrator != null && activeVibrator.hasVibrator()) {
                long[] pattern = {0L, 500L, 300L, 500L, 300L};
                activeVibrator.vibrate(VibrationEffect.createWaveform(pattern, 1));
            }
            MAIN_HANDLER.postDelayed(PhoneLink::stopFindPhone, 15_000L);
        });
        return new PhoneResult("Find phone", "The phone will ring and vibrate for 15 seconds.");
    }

    // Arrête la sonnerie du téléphone.
    private static void stopFindPhone() {
        if (activeRingtone != null) {
            try {
                activeRingtone.stop();
            } catch (RuntimeException ignored) {
            }
            activeRingtone = null;
        }
        if (activeVibrator != null) {
            activeVibrator.cancel();
            activeVibrator = null;
        }
    }

    // Indique si l’hôte est public.
    private static boolean isPublicHost(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        if (lower.equals("localhost") || lower.endsWith(".localhost")
                || lower.endsWith(".local") || lower.endsWith(".internal")) {
            return false;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    return false;
                }
            }
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    // Retourne le réseau Android actif.
    private static String activeNetwork(Context context) {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        if (manager == null) return "Offline";
        Network network = manager.getActiveNetwork();
        NetworkCapabilities capabilities = network == null ? null : manager.getNetworkCapabilities(network);
        if (capabilities == null
                || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return "Offline";
        }
        String kind;
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) kind = "Wi-Fi";
        else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) kind = "Mobile";
        else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) kind = "Ethernet";
        else kind = "Connected";
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) kind += " + VPN";
        return kind + (manager.isActiveNetworkMetered() ? " metered" : "");
    }

    // Lit l’état Bluetooth sans erreur.
    private static boolean safeBluetoothEnabled(BluetoothAdapter adapter) {
        try {
            return adapter.isEnabled();
        } catch (SecurityException ignored) {
            return false;
        }
    }

    // Crée le canal de recherche du téléphone.
    private static void ensureChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "FlipNet Phone Link",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Phone Link transfers from the Flipper");
        manager.createNotificationChannel(channel);
    }

    // Lit le nom d’une application.
    static String applicationLabel(Context context, String packageName) {
        PackageManager manager = context.getPackageManager();
        try {
            ApplicationInfo info;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                info = manager.getApplicationInfo(
                        packageName,
                        PackageManager.ApplicationInfoFlags.of(0L));
            } else {
                info = getApplicationInfoLegacy(manager, packageName);
            }
            CharSequence label = manager.getApplicationLabel(info);
            return label == null ? packageName : label.toString();
        } catch (PackageManager.NameNotFoundException error) {
            return packageName;
        }
    }

    // Lit une application sur un ancien Android.
    @SuppressWarnings("deprecation")
    private static ApplicationInfo getApplicationInfoLegacy(
            PackageManager manager,
            String packageName) throws PackageManager.NameNotFoundException {
        return manager.getApplicationInfo(packageName, 0);
    }

    // Nettoie le texte reçu.
    private static String clean(String value, int limit) {
        if (value == null) return "";
        String cleaned = value.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('|', '/')
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.substring(0, Math.min(limit, cleaned.length()));
    }

    // Formate l’âge d’une donnée.
    private static String ageLabel(long timestamp) {
        if (timestamp <= 0L) return "Unknown";
        long seconds = Math.max(0L, (System.currentTimeMillis() - timestamp) / 1000L);
        if (seconds < 60L) return "Just now";
        long minutes = seconds / 60L;
        if (minutes < 60L) return minutes + " min ago";
        long hours = minutes / 60L;
        if (hours < 24L) return hours + " h ago";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(new Date(timestamp));
    }

    // Formate une heure courte.
    private static String shortTime(long timestamp) {
        if (timestamp <= 0L) return "--:--";
        return new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date(timestamp));
    }
}
