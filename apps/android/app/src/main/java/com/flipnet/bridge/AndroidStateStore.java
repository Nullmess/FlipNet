package com.flipnet.bridge;

import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

// Gère l’état JSON partagé sur Android.
final class AndroidStateStore {
    private static final String TAG = "AndroidStateStore";
    private static final String KEY_STATE = "bridge_state";

    private final SharedPreferences preferences;

    // Initialise le stockage d’état Android.
    AndroidStateStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    // Charge les données persistantes.
    JSONObject load() {
        String raw = preferences.getString(KEY_STATE, "");
        try {
            JSONObject state = raw == null || raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
            if (!state.has("favorites")) state.put("favorites", new JSONArray());
            if (!state.has("history")) state.put("history", new JSONArray());
            if (!state.has("monitors")) state.put("monitors", new JSONArray());
            if (!state.has("alerts")) state.put("alerts", new JSONArray());
            if (!state.has("next_monitor_id")) state.put("next_monitor_id", 1);
            if (!state.has("action_log")) state.put("action_log", new JSONArray());
            if (!state.has("web_results")) state.put("web_results", new JSONArray());
            if (!state.has("web_searches")) state.put("web_searches", new JSONArray());
            if (!state.has("inventory")) state.put("inventory", new JSONObject());
            if (!state.has("inventory_changes")) state.put("inventory_changes", new JSONArray());
            return state;
        } catch (Exception error) {
            JSONObject state = new JSONObject();
            try {
                state.put("favorites", new JSONArray());
                state.put("history", new JSONArray());
                state.put("monitors", new JSONArray());
                state.put("alerts", new JSONArray());
                state.put("next_monitor_id", 1);
                state.put("action_log", new JSONArray());
                state.put("web_results", new JSONArray());
                state.put("web_searches", new JSONArray());
            } catch (Exception ignored) {
            }
            return state;
        }
    }

    // Enregistre les données persistantes.
    void save(JSONObject state) {
        preferences
                .edit()
                .putString(KEY_STATE, state.toString())
                .apply();
    }

    // Indique si le service utilise le stockage.
    private boolean isStateService(String service) {
        String id = service == null ? "" : service.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return id.equals("capabilities") || id.equals("dashboard")
                || id.equals("app_search")
                || id.equals("inventory_summary") || id.equals("inventory_changes")
                || id.equals("favorites") || id.equals("saved")
                || id.equals("favorite_last") || id.equals("favorite_run")
                || id.equals("history") || id.equals("recent") || id.equals("history_last")
                || id.equals("history_clear") || id.equals("monitors") || id.equals("monitor_list")
                || id.equals("monitor_add") || id.equals("monitor_remove") || id.equals("monitor_run")
                || id.equals("alerts") || id.equals("alert_list") || id.equals("alerts_clear")
                || id.equals("playbook_list") || id.equals("playbook_preview")
                || id.equals("playbook_prepare")
                || id.equals("secure_action") || id.equals("action_prepare")
                || id.equals("pending_action") || id.equals("action_pending")
                || id.equals("confirm_action") || id.equals("action_commit")
                || id.equals("cancel_action") || id.equals("action_cancel")
                || id.equals("action_log");
    }

    // Ajoute une entrée dans l’historique.
    void recordHistory(String service, String argument, String status, String title, String body) {
        if (isStateService(service)) return;
        try {
            JSONObject state = load();
            JSONArray old = state.getJSONArray("history");
            JSONArray history = new JSONArray();
            JSONObject item = new JSONObject();
            item.put("timestamp", System.currentTimeMillis());
            item.put("service", service);
            item.put("argument", argument);
            item.put("status", status);
            item.put("title", title);
            item.put("body", body == null ? "" : body.substring(0, Math.min(240, body.length())));
            history.put(item);
            for (int index = 0; index < Math.min(23, old.length()); index++) history.put(old.get(index));
            state.put("history", history);
            save(state);
        } catch (Exception error) {
            Log.w(TAG, "Historique non enregistré", error);
        }
    }}
