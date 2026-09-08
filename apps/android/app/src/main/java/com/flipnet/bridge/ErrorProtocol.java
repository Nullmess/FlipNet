package com.flipnet.bridge;

import java.util.ArrayList;

// Construit les erreurs structurées FNERR1.
final class ErrorProtocol {
    // Bloque la création de la classe utilitaire.
    private ErrorProtocol() {
    }

    // Construit une erreur transportable.
    static String payload(String code, String category, String service,
            boolean recoverable, String messageEn, String messageFr) {
        if (code == null || !code.matches("[A-Z0-9_]{1,40}"))
            throw new IllegalArgumentException("Invalid error code");
        if (category == null || !category.matches("[a-z0-9_]{1,24}"))
            throw new IllegalArgumentException("Invalid error category");
        String safeService = service == null ? "" : service;
        if (!safeService.matches("[a-z0-9_]{0,32}"))
            throw new IllegalArgumentException("Invalid error service");
        ArrayList<String> fields = new ArrayList<>();
        fields.add("FNERR1");
        fields.add(code);
        fields.add(category);
        fields.add(safeService);
        fields.add(recoverable ? "1" : "0");
        fields.add(encode(messageEn));
        fields.add(encode(messageFr));
        return String.join("|", fields);
    }

    // Encode un champ réservé.
    private static String encode(String value) {
        return (value == null ? "" : value)
                .replace("%", "%25")
                .replace("|", "%7C")
                .replace("\r", "%0D")
                .replace("\n", "%0A");
    }
}
