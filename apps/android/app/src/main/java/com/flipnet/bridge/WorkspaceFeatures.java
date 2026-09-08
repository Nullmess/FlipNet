package com.flipnet.bridge;

import org.json.JSONArray;
import org.json.JSONObject;

// Gère l espace de travail Android.
final class WorkspaceFeatures {
    private final FlipNetService gateway;
    private final AndroidStateStore store;

    // Initialise l espace de travail Android.
    WorkspaceFeatures(FlipNetService gateway, AndroidStateStore store) {
        this.gateway = gateway;
        this.store = store;
    }

    // Liste les favoris.
    ServiceResult favoritesList() throws Exception {
        JSONArray items = store.load().getJSONArray("favorites");
        if (items.length() == 0) return new ServiceResult("Favorites", "Aucun favori.\nUtilise Save last result.");
        StringBuilder body = new StringBuilder();
        for (int index = 0; index < Math.min(12, items.length()); index++) {
            if (index > 0) body.append('\n');
            body.append(index + 1).append(". ").append(items.getJSONObject(index).optString("label", "?"));
        }
        return new ServiceResult("Favorites", body.toString());
    }

    // Retourne le dernier favori.
    ServiceResult favoriteLast() throws Exception {
        JSONObject state = store.load();
        JSONArray history = state.getJSONArray("history");
        if (history.length() == 0) throw new UserError("Aucun résultat récent");
        JSONObject last = history.getJSONObject(0);
        JSONArray old = state.getJSONArray("favorites");
        for (int index = 0; index < old.length(); index++) {
            JSONObject item = old.getJSONObject(index);
            if (item.optString("service").equals(last.optString("service"))
                    && item.optString("argument").equals(last.optString("argument"))) {
                return new ServiceResult("Favorite saved", item.optString("label", "Résultat"));
            }
        }
        JSONObject favorite = new JSONObject();
        favorite.put("label", last.optString("title", last.optString("argument", last.optString("service"))));
        favorite.put("service", last.optString("service"));
        favorite.put("argument", last.optString("argument"));
        JSONArray items = new JSONArray();
        items.put(favorite);
        for (int index = 0; index < Math.min(15, old.length()); index++) items.put(old.get(index));
        state.put("favorites", items);
        store.save(state);
        return new ServiceResult("Favorite saved", favorite.optString("label"));
    }

    // Relance un favori.
    ServiceResult favoriteRun(String argument) throws Exception {
        int index;
        try {
            index = Integer.parseInt(argument.trim());
        } catch (Exception error) {
            throw new UserError("Favori introuvable");
        }
        JSONArray items = store.load().getJSONArray("favorites");
        if (index < 1 || index > items.length()) throw new UserError("Favori introuvable");
        JSONObject item = items.getJSONObject(index - 1);
        String service = item.optString("service");
        String target = item.optString("argument");
        ServiceResult result = gateway.dispatchFeature(service, target);
        store.recordHistory(service, target, "OK", result.title, result.body);
        return result;
    }

    // Retourne la dernière requête.
    ServiceResult historyLast() throws Exception {
        JSONArray items = store.load().getJSONArray("history");
        if (items.length() == 0) throw new UserError("Aucun résultat récent");
        JSONObject item = items.getJSONObject(0);
        return new ServiceResult(
                item.optString("title", item.optString("service", "Résultat")),
                item.optString("body", ""));
    }

    // Liste l’historique.
    ServiceResult historyList() throws Exception {
        JSONArray items = store.load().getJSONArray("history");
        if (items.length() == 0) return new ServiceResult("History", "Aucun résultat récent");
        StringBuilder body = new StringBuilder();
        for (int index = 0; index < Math.min(10, items.length()); index++) {
            JSONObject item = items.getJSONObject(index);
            if (index > 0) body.append('\n');
            body.append(index + 1).append(". [").append(item.optString("status", "?")).append("] ")
                    .append(item.optString("title", item.optString("service", "Résultat")));
        }
        return new ServiceResult("History", body.toString());
    }

    // Efface l’historique.
    ServiceResult historyClear() throws Exception {
        JSONObject state = store.load();
        state.put("history", new JSONArray());
        store.save(state);
        return new ServiceResult("History", "Historique effacé");
    }

}
