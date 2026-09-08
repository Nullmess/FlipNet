package com.flipnet.bridge;

// Regroupe les modules fonctionnels Android.
final class AndroidFeatures {
    final ExtensionAppsFeatures extensionApps;
    final WorkspaceFeatures workspace;
    final OperationsFeatures operations;
    final InternetExtensions internetExtensions;
    final NetworkFeatures network;
    final SystemsFeatures systems;

    // Initialise les modules fonctionnels Android.
    AndroidFeatures(FlipNetService host, AndroidStateStore state) {
        extensionApps = new ExtensionAppsFeatures(host);
        workspace = new WorkspaceFeatures(host, state);
        operations = new OperationsFeatures(host, state);
        internetExtensions = new InternetExtensions(state);
        network = new NetworkFeatures(state);
        systems = new SystemsFeatures(host);
    }
}
