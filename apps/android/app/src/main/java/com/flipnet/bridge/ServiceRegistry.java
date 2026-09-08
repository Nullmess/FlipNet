package com.flipnet.bridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Associe les services Android à leurs gestionnaires.
final class ServiceRegistry {
    // Définit le contrat d’un gestionnaire Android.
    interface Handler {
        ServiceResult handle(String argument, String language) throws Exception;
    }

    // Associe une description à son gestionnaire.
    private static final class Entry {
        final GeneratedFeatureCatalog.Feature feature;
        final Handler handler;

        // Initialise l’entrée du registre.
        Entry(GeneratedFeatureCatalog.Feature feature, Handler handler) {
            this.feature = feature;
            this.handler = handler;
        }
    }

    private final String platform;
    private final Map<String, Entry> services = new LinkedHashMap<>();

    // Initialise le registre des services.
    ServiceRegistry(String platform) {
        if (platform == null || !platform.matches("[a-z0-9_-]{1,15}"))
            throw new IllegalArgumentException("Invalid Android platform");
        this.platform = platform;
    }

    // Enregistre un service généré.
    void register(String id, Handler handler) {
        GeneratedFeatureCatalog.Feature feature =
                GeneratedFeatureCatalog.require(id, platform);
        String canonical = feature.id;
        if (!canonical.matches("[a-z0-9_]{1,32}")
                || services.containsKey(canonical))
            throw new IllegalArgumentException("Invalid or duplicate Android service: " + id);
        services.put(canonical, new Entry(feature, handler));
    }

    // Distribue la requête au bon service.
    ServiceResult dispatch(String id, String argument, String language) throws Exception {
        Entry entry = id == null ? null : services.get(id);
        return entry == null ? null : entry.handler.handle(argument, language);
    }

    // Liste les services enregistrés.
    List<GeneratedFeatureCatalog.Feature> descriptors() {
        List<GeneratedFeatureCatalog.Feature> result = new ArrayList<>();
        for (Entry entry : services.values()) result.add(entry.feature);
        return result;
    }

    // Construit le catalogue des capacités.
    ServiceResult capabilitiesPayload(String argument, List<CapabilityProtocol.App> apps) {
        String body = CapabilityProtocol.payload(platform, argument, descriptors(), apps);
        return new ServiceResult("Capabilities", body);
    }

}
