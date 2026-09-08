package com.flipnet.bridge;

// Associe les fonctionnalités Android à leurs gestionnaires.
final class AndroidFeatureRegistry {
    // Bloque la création de la classe utilitaire.
    private AndroidFeatureRegistry() {
    }

    // Construit le registre Android.
    static ServiceRegistry create(AndroidFeatures features) {
        ServiceRegistry registry = new ServiceRegistry("android");
        registry.register(GeneratedFeatureCatalog.CAPABILITIES,
                (argument, language) -> registry.capabilitiesPayload(
                        argument, features.extensionApps.capabilityApps()));
        registry.register(GeneratedFeatureCatalog.DASHBOARD,
                (argument, language) -> features.operations.dashboard());
        registry.register(GeneratedFeatureCatalog.TIMELINE,
                (argument, language) -> features.operations.timelineList());
        registry.register(GeneratedFeatureCatalog.APP_SEARCH,
                (argument, language) -> features.extensionApps.appSearch(argument, language));
        registry.register(GeneratedFeatureCatalog.APP_RUN,
                (argument, language) -> features.extensionApps.appRun(argument, language));
        registry.register(GeneratedFeatureCatalog.FAVORITES,
                (argument, language) -> features.workspace.favoritesList());
        registry.register(GeneratedFeatureCatalog.FAVORITE_LAST,
                (argument, language) -> features.workspace.favoriteLast());
        registry.register(GeneratedFeatureCatalog.FAVORITE_RUN,
                (argument, language) -> features.workspace.favoriteRun(argument));
        registry.register(GeneratedFeatureCatalog.HISTORY,
                (argument, language) -> features.workspace.historyList());
        registry.register(GeneratedFeatureCatalog.HISTORY_LAST,
                (argument, language) -> features.workspace.historyLast());
        registry.register(GeneratedFeatureCatalog.HISTORY_CLEAR,
                (argument, language) -> features.workspace.historyClear());
        registry.register(GeneratedFeatureCatalog.MONITORS,
                (argument, language) -> features.operations.monitorsList());
        registry.register(GeneratedFeatureCatalog.MONITOR_ADD,
                (argument, language) -> features.operations.monitorAdd(argument));
        registry.register(GeneratedFeatureCatalog.MONITOR_REMOVE,
                (argument, language) -> features.operations.monitorRemove(argument));
        registry.register(GeneratedFeatureCatalog.MONITOR_RUN,
                (argument, language) -> features.operations.monitorRun());
        registry.register(GeneratedFeatureCatalog.ALERTS,
                (argument, language) -> features.operations.alertsList());
        registry.register(GeneratedFeatureCatalog.ALERTS_CLEAR,
                (argument, language) -> features.operations.alertsClear());
        registry.register(GeneratedFeatureCatalog.PLAYBOOK_LIST,
                (argument, language) -> features.operations.playbookList(language));
        registry.register(GeneratedFeatureCatalog.PLAYBOOK_PREVIEW,
                (argument, language) -> features.operations.playbookPreview(argument, language));
        registry.register(GeneratedFeatureCatalog.PLAYBOOK_PREPARE,
                (argument, language) -> features.operations.playbookPrepare(argument, language));
        registry.register(GeneratedFeatureCatalog.ACTION_PREPARE,
                (argument, language) -> features.operations.actionPrepare(argument));
        registry.register(GeneratedFeatureCatalog.ACTION_PENDING,
                (argument, language) -> features.operations.actionPending());
        registry.register(GeneratedFeatureCatalog.ACTION_COMMIT,
                (argument, language) -> features.operations.actionCommit());
        registry.register(GeneratedFeatureCatalog.ACTION_CANCEL,
                (argument, language) -> features.operations.actionCancel());
        registry.register(GeneratedFeatureCatalog.ACTION_LOG,
                (argument, language) -> features.operations.actionLog());
        registry.register(GeneratedFeatureCatalog.WEB_SEARCH,
                (argument, language) -> features.internetExtensions.webSearch(argument, language));
        registry.register(GeneratedFeatureCatalog.WEB_PAGE,
                (argument, language) -> features.internetExtensions.webPage(argument));
        registry.register(GeneratedFeatureCatalog.WEB_URL,
                (argument, language) -> features.internetExtensions.webUrl(argument));
        registry.register(GeneratedFeatureCatalog.WEATHER,
                (argument, language) -> features.internetExtensions.weather(argument, language));
        registry.register(GeneratedFeatureCatalog.WEATHER_SEARCH,
                (argument, language) -> features.internetExtensions.weatherSearch(argument, language));
        registry.register(GeneratedFeatureCatalog.WEATHER_LOCATION,
                (argument, language) -> features.internetExtensions.weatherLocation(argument, language));
        registry.register(GeneratedFeatureCatalog.WIKI,
                (argument, language) -> features.internetExtensions.wikipedia(argument, language));
        registry.register(GeneratedFeatureCatalog.WIKI_SEARCH,
                (argument, language) -> features.internetExtensions.wikipediaSearch(argument, language));
        registry.register(GeneratedFeatureCatalog.WIKI_PAGE,
                (argument, language) -> features.internetExtensions.wikipediaPage(argument, language));
        registry.register(GeneratedFeatureCatalog.HEADLINES,
                (argument, language) -> features.internetExtensions.headlines(argument));
        registry.register(GeneratedFeatureCatalog.FEED_DIGEST,
                (argument, language) -> features.internetExtensions.feedDigest(argument));
        registry.register(GeneratedFeatureCatalog.FEED_ARTICLE,
                (argument, language) -> features.internetExtensions.feedArticle(argument));
        registry.register(GeneratedFeatureCatalog.HEADERS,
                (argument, language) -> features.network.httpSecurity(argument));
        registry.register(GeneratedFeatureCatalog.TLS,
                (argument, language) -> features.network.tlsInspect(argument));
        registry.register(GeneratedFeatureCatalog.DNS,
                (argument, language) -> features.network.dnsLookup(argument));
        registry.register(GeneratedFeatureCatalog.DOMAIN_AUDIT,
                (argument, language) -> features.network.domainAudit(argument));
        registry.register(GeneratedFeatureCatalog.ENDPOINT_CHECK,
                (argument, language) -> features.network.endpointCheck(argument));
        registry.register(GeneratedFeatureCatalog.PORT_SCAN,
                (argument, language) -> features.network.portScan(argument));
        registry.register(GeneratedFeatureCatalog.HOST_DISCOVERY,
                (argument, language) -> features.network.hostDiscovery(argument));
        registry.register(GeneratedFeatureCatalog.INVENTORY_SCAN,
                (argument, language) -> features.network.inventoryScan(argument, language));
        registry.register(GeneratedFeatureCatalog.INVENTORY_SUMMARY,
                (argument, language) -> features.network.inventorySummary(language));
        registry.register(GeneratedFeatureCatalog.INVENTORY_CHANGES,
                (argument, language) -> features.network.inventoryChanges(language));
        registry.register(GeneratedFeatureCatalog.HOST_STATUS,
                (argument, language) -> features.systems.hostStatus());
        registry.register(GeneratedFeatureCatalog.SECURITY_STATUS,
                (argument, language) -> features.systems.securityStatus());
        registry.register(GeneratedFeatureCatalog.SERVICES_STATUS,
                (argument, language) -> features.systems.servicesStatus());
        registry.register(GeneratedFeatureCatalog.NETWORK_GUARD,
                (argument, language) -> features.systems.networkGuard());
        registry.register(GeneratedFeatureCatalog.WORKLOADS_STATUS,
                (argument, language) -> features.systems.workloadsStatus());
        registry.register(GeneratedFeatureCatalog.UPDATES_STATUS,
                (argument, language) -> features.systems.updatesStatus());
        return registry;
    }
}
