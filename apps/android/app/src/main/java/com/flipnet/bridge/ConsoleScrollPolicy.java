package com.flipnet.bridge;

// Calcule le suivi et la restauration du défilement de la console.
final class ConsoleScrollPolicy {
    // Empêche la création de cette classe utilitaire.
    private ConsoleScrollPolicy() {
    }

    // Indique si la position est suffisamment proche de la fin.
    static boolean isAtBottom(
            int scrollY,
            int contentHeight,
            int viewportHeight,
            int threshold) {
        int maximum = Math.max(0, contentHeight - Math.max(0, viewportHeight));
        return maximum - Math.max(0, scrollY) <= Math.max(0, threshold);
    }

    // Restaure une position valide après la modification du contenu.
    static int restoredScrollY(int savedScrollY, int contentHeight, int viewportHeight) {
        int maximum = Math.max(0, contentHeight - Math.max(0, viewportHeight));
        return Math.min(Math.max(0, savedScrollY), maximum);
    }
}
