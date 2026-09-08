package com.flipnet.bridge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Construit le catalogue FNCAP2.
final class CapabilityProtocol {
    private static final int MAX_BYTES = 1000;

    // Décrit une application publiée dans le catalogue.
    static final class App {
        final String id;
        final String service;
        final String mode;
        final String title;
        final String titleFr;
        final String prompt;
        final String promptFr;
        final String initial;
        final String origin;
        final String extensionGroup;

        // Initialise une application publiée.
        App(String id, String service, String mode, String title, String titleFr,
                String prompt, String promptFr, String initial, String origin,
                String extensionGroup) {
            this.id = id;
            this.service = service;
            this.mode = mode;
            this.title = title;
            this.titleFr = titleFr;
            this.prompt = prompt;
            this.promptFr = promptFr;
            this.initial = initial;
            this.origin = origin;
            this.extensionGroup = extensionGroup;
        }
    }

    // Bloque la création de la classe utilitaire.
    private CapabilityProtocol() {
    }

    // Retourne la page demandée.
    static String payload(String platform, String argument,
            List<GeneratedFeatureCatalog.Feature> features, List<App> apps) {
        String raw = argument == null ? "" : argument.trim();
        if (!raw.startsWith("2|")) throw new IllegalArgumentException("Invalid FNCAP2 request");
        int page;
        try {
            page = Integer.parseInt(raw.substring(2));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid FNCAP2 page", error);
        }
        if (page < 0 || page > 255) throw new IllegalArgumentException("Invalid FNCAP2 page");
        List<String> pages = pages(platform, features, apps);
        if (page >= pages.size()) throw new IllegalArgumentException("Missing FNCAP2 page");
        return pages.get(page);
    }


    // Construit toutes les pages FNCAP2.
    private static List<String> pages(String platform,
            List<GeneratedFeatureCatalog.Feature> features, List<App> apps) {
        ArrayList<String> records = new ArrayList<>();
        for (GeneratedFeatureCatalog.Feature feature : features)
            records.add(featureLine(feature));
        for (App app : apps) records.add(appLine(app));
        String revision = revision(records);
        ArrayList<List<String>> groups = new ArrayList<>();
        groups.add(new ArrayList<>());
        for (String record : records) {
            List<String> current = groups.get(groups.size() - 1);
            ArrayList<String> trial = new ArrayList<>(current);
            trial.add(record);
            String header = "FNCAP2\n" + platform + "|0|255|" + revision;
            String candidate = header + "\n" + String.join("\n", trial);
            if (candidate.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES
                    && !current.isEmpty()) {
                ArrayList<String> next = new ArrayList<>();
                next.add(record);
                groups.add(next);
            } else {
                current.add(record);
            }
        }
        ArrayList<String> result = new ArrayList<>();
        for (int index = 0; index < groups.size(); index++) {
            String header = "FNCAP2\n" + platform + "|" + index + "|"
                    + groups.size() + "|" + revision;
            String body = String.join("\n", groups.get(index));
            String page = body.isEmpty() ? header : header + "\n" + body;
            if (page.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
                throw new IllegalArgumentException("FNCAP2 page too large");
            result.add(page);
        }
        return result;
    }

    // Construit une ligne de fonctionnalité.
    private static String featureLine(GeneratedFeatureCatalog.Feature feature) {
        return line("F", feature.id, feature.domain, feature.group, feature.kind,
                feature.category, feature.inputMode, feature.title,
                feature.titleFr.isEmpty() ? feature.title : feature.titleFr,
                feature.promptEn, feature.promptFr.isEmpty() ? feature.promptEn : feature.promptFr,
                feature.initial, String.join(",", feature.permissions),
                String.join(",", feature.platforms), feature.origin, feature.extensionGroup, feature.visible ? "1" : "0",
                feature.nextService);
    }

    // Construit une ligne d’application.
    private static String appLine(App app) {
        return line("A", app.id, app.service, app.mode, app.title,
                app.titleFr.isEmpty() ? app.title : app.titleFr, app.prompt,
                app.promptFr.isEmpty() ? app.prompt : app.promptFr, app.initial,
                app.origin, app.extensionGroup, "1");
    }

    // Assemble une ligne du catalogue.
    private static String line(String... fields) {
        ArrayList<String> values = new ArrayList<>();
        for (String field : fields) values.add(encode(field));
        return String.join("|", values);
    }

    // Encode un champ réservé.
    private static String encode(String value) {
        return (value == null ? "" : value)
                .replace("%", "%25")
                .replace("|", "%7C")
                .replace(",", "%2C")
                .replace("\r", "%0D")
                .replace("\n", "%0A");
    }

    // Calcule la révision du catalogue.
    private static String revision(List<String> records) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = digest.digest(String.join("\n", records)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < 6; index++)
                result.append(String.format(Locale.ROOT, "%02X", value[index]));
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
