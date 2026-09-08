package com.flipnet.bridge;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

// Démarre la passerelle après le démarrage Android.
public final class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "FlipNetBoot";

    // Traite le message Android reçu.
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean enabled = context
                .getSharedPreferences(FlipNetService.PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(FlipNetService.KEY_BOOT_ENABLED, false);
        if (!enabled || !permissionsGranted(context)) return;

        Intent service = new Intent(context, FlipNetService.class);
        service.setAction(FlipNetService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to restart FlipNet on boot", exception);
        }
    }

    // Indique si les permissions sont accordées.
    private boolean permissionsGranted(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                            == PackageManager.PERMISSION_GRANTED
                    && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                            == PackageManager.PERMISSION_GRANTED;
        }
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
}
