package com.flipnet.bridge;

import android.content.SharedPreferences;
import android.os.SystemClock;

// Maintient les compteurs de santé de la passerelle.
final class GatewayMetrics {
    private static final int SCHEMA = 2;

    // Bloque la création de la classe utilitaire.
    private GatewayMetrics() {
    }

    // Initialise ou migre les compteurs persistants.
    static void initialize(SharedPreferences preferences) {
        boolean newSession = preferences.getLong(FlipNetService.KEY_STARTED_AT, 0L) == 0L;
        boolean upgrade = preferences.getInt(FlipNetService.KEY_METRICS_SCHEMA, 0) != SCHEMA;
        if (!newSession && !upgrade) return;
        SharedPreferences.Editor editor = preferences.edit()
                .putInt(FlipNetService.KEY_METRICS_SCHEMA, SCHEMA)
                .putLong(FlipNetService.KEY_REQUEST_COUNT, 0L)
                .putLong(FlipNetService.KEY_ERROR_COUNT, 0L)
                .putLong(FlipNetService.KEY_LAST_LATENCY_MS, 0L)
                .putInt(FlipNetService.KEY_RSSI, 0);
        if (newSession) {
            editor.putLong(FlipNetService.KEY_STARTED_AT, System.currentTimeMillis());
        }
        editor.apply();
    }

    // Enregistre la latence et seulement les erreurs non récupérables.
    static void record(SharedPreferences preferences, long startedAt, boolean error) {
        SharedPreferences.Editor editor = preferences.edit()
                .putLong(FlipNetService.KEY_LAST_LATENCY_MS,
                        Math.max(0L, SystemClock.elapsedRealtime() - startedAt));
        if (error) {
            editor.putLong(FlipNetService.KEY_ERROR_COUNT,
                    preferences.getLong(FlipNetService.KEY_ERROR_COUNT, 0L) + 1L);
        }
        editor.apply();
    }
}
