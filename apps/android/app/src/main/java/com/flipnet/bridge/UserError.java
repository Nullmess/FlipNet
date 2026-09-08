package com.flipnet.bridge;

// Signale une erreur destinée à l’utilisateur.
final class UserError extends Exception {
    private static final long serialVersionUID = 1L;
    final String code;
    final String category;
    final boolean recoverable;

    // Initialise une erreur utilisateur générique.
    UserError(String message) {
        this("SERVICE_ERROR", "service", true, message);
    }

    // Construit une erreur de catalogue périmé.
    static UserError serviceNotFound() {
        return new UserError("SERVICE_NOT_FOUND", "service", true,
                "Service unavailable on this companion. Refresh features.");
    }

    // Initialise une erreur utilisateur structurée.
    UserError(String code, String category, boolean recoverable, String message) {
        super(message);
        this.code = code;
        this.category = category;
        this.recoverable = recoverable;
    }
}
