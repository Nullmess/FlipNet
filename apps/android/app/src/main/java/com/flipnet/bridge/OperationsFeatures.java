package com.flipnet.bridge;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

// Regroupe les opérations persistantes Android.
final class OperationsFeatures {
    private static final String TAG = "OperationsFeatures";

    private final FlipNetService gateway;
    private final AndroidStateStore store;
    private String pendingActionKind = "";
    private String pendingActionOperation = "";
    private String pendingActionTarget = "";
    private long pendingActionExpiresMs;

    // Initialise les fonctions opérationnelles.
    OperationsFeatures(FlipNetService gateway, AndroidStateStore store) {
        this.gateway = gateway;
        this.store = store;
    }

    // Construit le tableau de bord.
    ServiceResult dashboard() throws Exception {
        JSONObject state = store.load();
        JSONArray monitors = state.getJSONArray("monitors");
        int ok = 0;
        int errors = 0;
        for (int index = 0; index < monitors.length(); index++) {
            String status = monitors.getJSONObject(index).optString("last_status", "NEW");
            if ("OK".equals(status)) ok++;
            if ("ERR".equals(status)) errors++;
        }
        int alerts = 0;
        JSONArray alertItems = state.getJSONArray("alerts");
        for (int index = 0; index < alertItems.length(); index++) {
            if (!alertItems.getJSONObject(index).optBoolean("acknowledged", false)) alerts++;
        }
        JSONObject inventory = state.optJSONObject("inventory");
        int devices = inventory == null ? 0 : inventory.optJSONArray("assets") == null ? 0 : inventory.optJSONArray("assets").length();
        JSONArray changes = state.optJSONArray("inventory_changes");
        JSONObject latest = changes != null && changes.length() > 0 ? changes.optJSONObject(0) : null;
        int added = latest == null || latest.optJSONArray("added") == null ? 0 : latest.optJSONArray("added").length();
        int removed = latest == null || latest.optJSONArray("removed") == null ? 0 : latest.optJSONArray("removed").length();
        int changed = latest == null || latest.optJSONArray("changed") == null ? 0 : latest.optJSONArray("changed").length();
        String body = "Gateway=OK\nInternet=OK"
                + "\nDevices=" + devices + "\nNew=" + added + "\nMissing=" + removed + "\nChanged=" + changed
                + "\nMonitors=" + monitors.length()
                + "\nOK=" + ok
                + "\nError=" + errors
                + "\nAlerts=" + alerts
                + "\nTargets=0"
                + "\nFavorites=" + state.getJSONArray("favorites").length()
                + "\nHistory=" + state.getJSONArray("history").length()
                + "\nLastSync=" + (inventory == null ? "never" : inventory.optString("scanned_at", "never"));
        return new ServiceResult("Smart Dashboard", body);
    }

    // Liste les événements récents.
    ServiceResult timelineList() throws Exception {
        JSONObject state = store.load();
        JSONArray alerts = state.optJSONArray("alerts");
        JSONArray changes = state.optJSONArray("inventory_changes");
        StringBuilder body = new StringBuilder();
        if (alerts != null) {
            for (int i = 0; i < Math.min(8, alerts.length()); i++) {
                JSONObject item = alerts.optJSONObject(i);
                if (item == null) continue;
                if (body.length() > 0) body.append('\n');
                String stamp = item.optString("timestamp", "");
                body.append(stamp.length() >= 16 ? stamp.substring(11, 16) : "--:--")
                        .append(" [HIGH] ").append(item.optString("title", "Alert"));
            }
        }
        if (changes != null && changes.length() > 0 && body.length() < 700) {
            JSONObject item = changes.optJSONObject(0);
            if (item != null) {
                if (body.length() > 0) body.append('\n');
                body.append("--:-- [INFO] Network changed");
            }
        }
        if (body.length() == 0) body.append("No recent events");
        return new ServiceResult("Timeline", body.toString());
    }

    // Liste les surveillances.
    ServiceResult monitorsList() throws Exception {
        JSONArray items = store.load().getJSONArray("monitors");
        if (items.length() == 0) return new ServiceResult("Monitors", "Aucun monitor.\nAjoute une cible.");
        StringBuilder body = new StringBuilder();
        for (int index = 0; index < Math.min(12, items.length()); index++) {
            JSONObject item = items.getJSONObject(index);
            if (index > 0) body.append('\n');
            body.append('#').append(item.optInt("id")).append(" [")
                    .append(item.optString("last_status", "NEW")).append("] ")
                    .append(item.optString("name", "Monitor"));
        }
        return new ServiceResult("Monitors", body.toString());
    }

    // Ajoute une surveillance.
    ServiceResult monitorAdd(String argument) throws Exception {
        String[] parts = argument.split("\\|", -1);
        String name;
        String service = "endpoint_check";
        String target;
        int interval = 60;
        if (parts.length == 1) {
            name = parts[0].trim();
            target = name;
        } else if (parts.length == 2) {
            name = parts[0].trim().isEmpty() ? parts[1].trim() : parts[0].trim();
            target = parts[1].trim();
        } else if (parts.length == 3 || parts.length == 4) {
            name = parts[0].trim();
            service = parts[1].trim().replace('-', '_');
            target = parts[2].trim();
            if (parts.length == 4) interval = Integer.parseInt(parts[3].trim());
        } else {
            throw new UserError("Format: Nom|cible");
        }
        if (target.isEmpty()) throw new UserError("Cible vide");
        if (!(service.equals("endpoint_check") || service.equals("domain_audit")
                || service.equals("tls") || service.equals("dns") || service.equals("headers"))) {
            throw new UserError("Type de monitor non supporté");
        }
        JSONObject state = store.load();
        int id = state.optInt("next_monitor_id", 1);
        JSONObject item = new JSONObject();
        item.put("id", id);
        item.put("name", name.isEmpty() ? target : name);
        item.put("service", service);
        item.put("argument", target);
        item.put("interval_seconds", Math.max(30, Math.min(interval, 86400)));
        item.put("last_status", "NEW");
        item.put("last_title", "");
        item.put("last_body", "");
        item.put("next_check_epoch", 0L);
        state.getJSONArray("monitors").put(item);
        state.put("next_monitor_id", id + 1);
        store.save(state);
        return new ServiceResult("Monitor added", "#" + id + " " + item.optString("name") + "\nEvery " + item.optInt("interval_seconds") + " s");
    }

    // Retire une surveillance.
    ServiceResult monitorRemove(String argument) throws Exception {
        int id;
        try {
            id = Integer.parseInt(argument.trim().replace("#", ""));
        } catch (Exception error) {
            throw new UserError("Identifiant invalide");
        }
        JSONObject state = store.load();
        JSONArray old = state.getJSONArray("monitors");
        JSONArray items = new JSONArray();
        boolean removed = false;
        for (int index = 0; index < old.length(); index++) {
            JSONObject item = old.getJSONObject(index);
            if (item.optInt("id") == id) removed = true;
            else items.put(item);
        }
        if (!removed) throw new UserError("Monitor introuvable");
        state.put("monitors", items);
        store.save(state);
        return new ServiceResult("Monitor removed", "#" + id);
    }

    // Exécute une surveillance.
    ServiceResult monitorRun() throws Exception {
        int[] result = runMonitorCycle(true);
        return new ServiceResult("Monitor run", result[0] + " OK\n" + result[1] + " ERR\n" + (result[0] + result[1]) + " checked");
    }

    // Liste les alertes.
    ServiceResult alertsList() throws Exception {
        JSONArray items = store.load().getJSONArray("alerts");
        StringBuilder body = new StringBuilder();
        int visible = 0;
        for (int index = 0; index < items.length() && visible < 10; index++) {
            JSONObject item = items.getJSONObject(index);
            if (item.optBoolean("acknowledged", false)) continue;
            if (visible > 0) body.append('\n');
            body.append(visible + 1).append(". ").append(item.optString("title", "Alerte"));
            visible++;
        }
        return new ServiceResult("Alerts", visible == 0 ? "Aucune alerte active" : body.toString());
    }

    // Efface les alertes.
    ServiceResult alertsClear() throws Exception {
        JSONObject state = store.load();
        JSONArray items = state.getJSONArray("alerts");
        for (int index = 0; index < items.length(); index++) items.getJSONObject(index).put("acknowledged", true);
        store.save(state);
        return new ServiceResult("Alerts", "Alertes acquittées");
    }

    // Liste les procédures.
    ServiceResult playbookList(String language) {
        boolean fr = language != null && language.toLowerCase(Locale.ROOT).startsWith("fr");
        return new ServiceResult("Playbooks", "reconnect-phone | "
                + (fr ? "Reconnecter le lien Android" : "Reconnect Android link"));
    }

    // Affiche le contenu d’une procédure.
    ServiceResult playbookPreview(String argument, String language) throws Exception {
        if (!argument.trim().equalsIgnoreCase("reconnect-phone")) {
            throw new UserError("Playbook introuvable");
        }
        boolean fr = language != null && language.toLowerCase(Locale.ROOT).startsWith("fr");
        String body = fr
                ? "1. Verifier le lien Android\n2. Deconnecter BLE\n3. Relancer la recherche\n\nRecuperation:\nRouvrir FlipNet Android si le lien ne revient pas."
                : "1. Check Android link\n2. Disconnect BLE\n3. Restart discovery\n\nRecovery:\nOpen FlipNet Android if the link does not return.";
        return new ServiceResult(fr ? "Reconnecter Android" : "Reconnect Android", body);
    }

    // Prépare une procédure protégée.
    ServiceResult playbookPrepare(String argument, String language) throws Exception {
        if (!argument.trim().equalsIgnoreCase("reconnect-phone")) {
            throw new UserError("Playbook introuvable");
        }
        pendingActionKind = "bridge";
        pendingActionOperation = "reconnect";
        pendingActionTarget = "ble";
        pendingActionExpiresMs = System.currentTimeMillis() + 30_000L;
        boolean fr = language != null && language.toLowerCase(Locale.ROOT).startsWith("fr");
        return new ServiceResult(fr ? "Playbook pret" : "Playbook ready",
                (fr ? "Reconnecter le lien Android" : "Reconnect Android link")
                        + "\nSteps: 3\nExpires: 30 s\nReturn and choose CONFIRM.");
    }

    // Prépare une action protégée.
    ServiceResult actionPrepare(String argument) throws Exception {
        String[] parts = argument.split("\\|", -1);
        if (parts.length != 3
                || !parts[0].trim().equalsIgnoreCase("bridge")
                || !parts[1].trim().equalsIgnoreCase("reconnect")
                || !parts[2].trim().equalsIgnoreCase("ble")) {
            throw new UserError("Android supporte: bridge|reconnect|ble");
        }
        pendingActionKind = "bridge";
        pendingActionOperation = "reconnect";
        pendingActionTarget = "ble";
        pendingActionExpiresMs = System.currentTimeMillis() + 30_000L;
        return new ServiceResult(
                "Action ready",
                "Type: bridge\nAction: reconnect\nTarget: ble\nExpires: 30 s\nReturn and choose CONFIRM.");
    }

    // Exige une action encore valide.
    private void requirePendingAction() throws UserError {
        if (pendingActionKind.isEmpty() || System.currentTimeMillis() >= pendingActionExpiresMs) {
            pendingActionKind = "";
            pendingActionOperation = "";
            pendingActionTarget = "";
            pendingActionExpiresMs = 0L;
            throw new UserError("Aucune action en attente");
        }
    }

    // Affiche l’action en attente.
    ServiceResult actionPending() throws Exception {
        requirePendingAction();
        long seconds = Math.max(0L, (pendingActionExpiresMs - System.currentTimeMillis()) / 1000L);
        return new ServiceResult(
                "Pending action",
                pendingActionOperation + " " + pendingActionKind + "\n"
                        + pendingActionTarget + "\nExpires: " + seconds + " s");
    }

    // Ajoute le journal des actions.
    private void appendActionLog(String status, String detail) {
        try {
            JSONObject state = store.load();
            JSONArray old = state.getJSONArray("action_log");
            JSONArray items = new JSONArray();
            JSONObject entry = new JSONObject();
            entry.put("status", status);
            entry.put("operation", pendingActionOperation);
            entry.put("target", pendingActionTarget);
            entry.put("detail", detail);
            items.put(entry);
            for (int index = 0; index < Math.min(23, old.length()); index++) items.put(old.get(index));
            state.put("action_log", items);
            store.save(state);
        } catch (Exception error) {
            Log.w(TAG, "Journal d'action indisponible", error);
        }
    }

    // Efface l’action en attente.
    private void clearPendingAction() {
        pendingActionKind = "";
        pendingActionOperation = "";
        pendingActionTarget = "";
        pendingActionExpiresMs = 0L;
    }

    // Confirme l’action en attente.
    ServiceResult actionCommit() throws Exception {
        requirePendingAction();
        appendActionLog("OK", "BLE reconnect requested");
        clearPendingAction();
        gateway.requestBleReconnect(800L);
        return new ServiceResult("Action complete", "reconnect bridge\nble\nStatus: OK");
    }

    // Annule l’action en attente.
    ServiceResult actionCancel() throws Exception {
        requirePendingAction();
        String target = pendingActionTarget;
        appendActionLog("CANCELLED", "Cancelled from Flipper");
        clearPendingAction();
        return new ServiceResult("Action cancelled", target);
    }

    // Liste le journal des actions.
    ServiceResult actionLog() throws Exception {
        JSONArray items = store.load().getJSONArray("action_log");
        if (items.length() == 0) return new ServiceResult("Action log", "No action recorded");
        StringBuilder body = new StringBuilder();
        for (int index = 0; index < Math.min(10, items.length()); index++) {
            JSONObject item = items.getJSONObject(index);
            if (index > 0) body.append('\n');
            body.append(index + 1).append(". [").append(item.optString("status", "?"))
                    .append("] ").append(item.optString("operation", "?"))
                    .append(' ').append(item.optString("target", "?"));
        }
        return new ServiceResult("Action log", body.toString());
    }

    // Lance le cycle planifié.
    void runScheduledMonitorCycle() throws Exception {
        runMonitorCycle(false);
    }

    // Exécute un cycle de surveillance.
    private int[] runMonitorCycle(boolean force) throws Exception {
        JSONObject state = store.load();
        JSONArray items = state.getJSONArray("monitors");
        long now = System.currentTimeMillis();
        int ok = 0;
        int failed = 0;
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.getJSONObject(index);
            if (!force && item.optLong("next_check_epoch", 0L) > now) continue;
            String previous = item.optString("last_status", "NEW");
            String status;
            String title;
            String body;
            try {
                String service = item.optString("service", "endpoint_check");
                ServiceResult result = gateway.dispatchFeature(service, item.optString("argument"));
                title = result.title;
                body = result.body;
                String normalized = body.toUpperCase(Locale.ROOT);
                boolean healthy = !(service.equals("endpoint_check")
                        && (normalized.contains("FERME/TIMEOUT") || normalized.contains("ECHEC")))
                        && !(service.equals("domain_audit") && normalized.contains("ECHEC"));
                status = healthy ? "OK" : "ERR";
                if (healthy) ok++;
                else failed++;
            } catch (Exception error) {
                status = "ERR";
                title = item.optString("name", "Monitor");
                body = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                failed++;
            }
            item.put("last_status", status);
            item.put("last_title", title);
            item.put("last_body", body.substring(0, Math.min(240, body.length())));
            item.put("next_check_epoch", now + item.optInt("interval_seconds", 60) * 1000L);
            if (!previous.equals(status) && !("NEW".equals(previous) && "OK".equals(status))) {
                String alertTitle = ("ERR".equals(status) ? "ALERTE: " : "RÉTABLI: ") + item.optString("name", title);
                JSONObject alert = new JSONObject();
                alert.put("timestamp", now);
                alert.put("title", alertTitle);
                alert.put("body", body);
                alert.put("acknowledged", false);
                JSONArray oldAlerts = state.getJSONArray("alerts");
                JSONArray alerts = new JSONArray();
                alerts.put(alert);
                for (int alertIndex = 0; alertIndex < Math.min(23, oldAlerts.length()); alertIndex++) alerts.put(oldAlerts.get(alertIndex));
                state.put("alerts", alerts);
                gateway.publishFeatureAlert(alertTitle, body);
            }
        }
        store.save(state);
        return new int[]{ok, failed};
    }
}
