package com.flipnet.bridge;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Affiche et pilote l’application Android.
public final class MainActivity extends Activity {
    private static final int PERMISSIONS_REQUEST = 42;
    private static final int PHONE_LOCATION_REQUEST = 43;

    private static final int TAB_HOME = 0;
    private static final int TAB_COMPANION = 1;
    private static final int TAB_FILES = 2;
    private static final int TAB_CONSOLE = 3;
    private static final int TAB_SETTINGS = 4;
    private static final int TAB_COUNT = 5;

    private static final int COLOR_BACKGROUND = Color.rgb(5, 5, 5);
    private static final int COLOR_SURFACE = Color.rgb(10, 10, 10);
    private static final int COLOR_CARD = Color.rgb(15, 15, 15);
    private static final int COLOR_CARD_RAISED = Color.rgb(20, 20, 20);
    private static final int COLOR_ORANGE = Color.rgb(255, 122, 24);
    private static final int COLOR_ORANGE_DARK = Color.rgb(58, 28, 9);
    private static final int COLOR_TEXT = Color.rgb(246, 246, 246);
    private static final int COLOR_MUTED = Color.rgb(153, 153, 153);
    private static final int COLOR_BORDER = Color.rgb(39, 39, 39);
    private static final int COLOR_SUCCESS = Color.rgb(92, 214, 138);
    private static final int COLOR_DANGER = Color.rgb(255, 107, 107);

    private static final long SPLASH_DELAY_MS = 1200L;
    private static final long UI_REFRESH_MS = 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final View[] pages = new View[TAB_COUNT];
    private final TextView[] navItems = new TextView[TAB_COUNT];

    private TextView pageTitle;
    private TextView transportChip;
    private TextView overviewIndicator;
    private TextView overviewTitle;
    private TextView overviewText;
    private ProgressBar overviewLoading;
    private TextView homeStatusValue;
    private TextView homeRequestsValue;
    private TextView homeErrorsValue;
    private TextView bluetoothState;
    private TextView serviceState;
    private TextView flipperState;
    private TextView gatewayStatusValue;
    private TextView gatewayTransportValue;
    private TextView gatewayLatencyValue;
    private TextView gatewayRssiValue;
    private TextView gatewayRequestsValue;
    private TextView gatewayErrorsValue;
    private TextView gatewayUptimeValue;
    private TextView gatewayMessageValue;
    private TextView phoneNotificationValue;
    private TextView phoneSelectedAppsValue;
    private TextView phoneLocationValue;
    private TextView phoneClipboardValue;
    private TextView phoneTrustedValue;
    private TextView consoleView;
    private ScrollView consoleScroll;
    private String lastConsoleText = "";
    private boolean consoleFollowLatest = true;
    private boolean consoleScrollAdjustment;
    private TextView helperText;
    private Switch serviceSwitch;
    private Switch bootSwitch;
    private boolean switchLock;
    private int currentTab = TAB_HOME;
    private FlipperFilesController filesController;

    private final Runnable uiRefresher = new Runnable() {
        // Actualise l’état affiché.
        @Override
        public void run() {
            refreshUiState();
            handler.postDelayed(this, UI_REFRESH_MS);
        }
    };

    // Initialise le composant Android.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            currentTab = savedInstanceState.getInt("selected_tab", TAB_HOME);
        }

        filesController = new FlipperFilesController(this);

        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(COLOR_BACKGROUND);

        View content = buildContent();
        View splash = buildSplash(content);
        screen.addView(content);
        screen.addView(splash);
        setContentView(screen);

        selectTab(currentTab);
        refreshUiState();
        requestNeededPermissions();
        handleUsbIntent(getIntent());
    }

    // Mémorise l’onglet affiché.
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("selected_tab", currentTab);
        super.onSaveInstanceState(outState);
    }

    // Traite un nouveau branchement USB.
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleUsbIntent(intent);
    }

    // Démarre la passerelle après un branchement USB.
    private void handleUsbIntent(Intent intent) {
        if (intent == null
                || !UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) return;
        startBridge();
    }

    // Actualise l’écran actif.
    @Override
    protected void onResume() {
        super.onResume();
        PhoneLink.captureClipboard(this);
        PhoneLink.updateLocationSnapshot(this);
        refreshUiState();
        if (filesController != null) filesController.onResume();
        handler.removeCallbacks(uiRefresher);
        handler.post(uiRefresher);
    }

    // Suspend l’actualisation périodique.
    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(uiRefresher);
        if (filesController != null) filesController.onPause();
    }

    // Construit la coque principale avec navigation basse.
    private View buildContent() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(COLOR_BACKGROUND);
        shell.setAlpha(0f);

        shell.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout pageHost = new FrameLayout(this);
        pageHost.setBackgroundColor(COLOR_BACKGROUND);
        shell.addView(pageHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));

        pages[TAB_HOME] = buildHomePage();
        pages[TAB_COMPANION] = buildCompanionPage();
        pages[TAB_FILES] = filesController.buildPage();
        pages[TAB_CONSOLE] = buildConsolePage();
        pages[TAB_SETTINGS] = buildSettingsPage();
        for (View page : pages) {
            pageHost.addView(page, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        View separator = new View(this);
        separator.setBackgroundColor(COLOR_BORDER);
        shell.addView(separator, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)));
        shell.addView(buildBottomNavigation(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(72)));
        return shell;
    }

    // Construit l’en-tête compact.
    private View buildTopBar() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(18), dp(20), dp(14));
        header.setBackgroundColor(COLOR_BACKGROUND);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        header.addView(titles, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        TextView title = text("FlipNet", 25f, COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titles.addView(title, matchParams());

        pageTitle = text("Home", 13f, COLOR_MUTED);
        pageTitle.setPadding(0, dp(2), 0, 0);
        titles.addView(pageTitle, matchParams());

        transportChip = chip();
        header.addView(transportChip, wrapParams());
        return header;
    }

    // Construit l’accueil allégé.
    private View buildHomePage() {
        LinearLayout root = pageRoot();

        LinearLayout overview = card(true);
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        overview.addView(statusRow, matchParams());

        overviewIndicator = text("●", 17f, COLOR_MUTED);
        statusRow.addView(overviewIndicator, wrapParams());

        LinearLayout statusText = new LinearLayout(this);
        statusText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams statusTextParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        statusTextParams.leftMargin = dp(12);
        statusRow.addView(statusText, statusTextParams);

        overviewTitle = text("Loading…", 18f, COLOR_TEXT);
        overviewTitle.setTypeface(Typeface.DEFAULT_BOLD);
        statusText.addView(overviewTitle, matchParams());

        overviewText = text("Preparing FlipNet.", 13f, COLOR_MUTED);
        overviewText.setPadding(0, dp(5), 0, 0);
        statusText.addView(overviewText, matchParams());

        overviewLoading = new ProgressBar(this);
        overviewLoading.setIndeterminate(true);
        statusRow.addView(overviewLoading, new LinearLayout.LayoutParams(dp(22), dp(22)));
        root.addView(overview, matchCardParams());

        root.addView(sectionTitle("At a glance"), matchParams());
        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        homeStatusValue = metricCell(metrics, "Link", true);
        homeRequestsValue = metricCell(metrics, "Requests", false);
        homeErrorsValue = metricCell(metrics, "Errors", false);
        root.addView(metrics, matchParams());

        root.addView(sectionTitle("Connection"), matchParams());
        LinearLayout states = card(false);
        bluetoothState = statusRow("Android Bluetooth");
        states.addView(bluetoothState);
        states.addView(divider());
        serviceState = statusRow("FlipNet service");
        states.addView(serviceState);
        states.addView(divider());
        flipperState = statusRow("Flipper");
        states.addView(flipperState);
        root.addView(states, matchCardParams());

        TextView hint = text(
                "Detailed telemetry and Phone Link controls are available in Companion.",
                12f,
                COLOR_MUTED);
        hint.setPadding(dp(2), dp(14), dp(2), 0);
        root.addView(hint, matchParams());
        return pageScroll(root);
    }

    // Regroupe la surveillance de la liaison et les fonctions Phone Link.
    private View buildCompanionPage() {
        LinearLayout root = pageRoot();

        TextView intro = text(
                "Live link health, bridge activity, and Android data shared with the Flipper.",
                13f,
                COLOR_MUTED);
        root.addView(intro, matchParams());

        root.addView(sectionTitle("Gateway"), matchParams());
        LinearLayout gatewayCard = card(false);
        gatewayStatusValue = monitorRow(gatewayCard, "Status");
        gatewayTransportValue = monitorRow(gatewayCard, "Transport");
        gatewayLatencyValue = monitorRow(gatewayCard, "Latency");
        gatewayRssiValue = monitorRow(gatewayCard, "RSSI");
        gatewayUptimeValue = monitorRow(gatewayCard, "Uptime");
        root.addView(gatewayCard, matchCardParams());

        root.addView(sectionTitle("Traffic"), matchParams());
        LinearLayout trafficCard = card(false);
        gatewayRequestsValue = monitorRow(trafficCard, "Requests");
        gatewayErrorsValue = monitorRow(trafficCard, "Errors");
        root.addView(trafficCard, matchCardParams());

        root.addView(sectionTitle("Bridge message"), matchParams());
        LinearLayout messageCard = card(false);
        gatewayMessageValue = text("Ready.", 13f, COLOR_MUTED);
        gatewayMessageValue.setTextIsSelectable(true);
        messageCard.addView(gatewayMessageValue, matchParams());
        root.addView(messageCard, matchCardParams());

        root.addView(sectionTitle("Phone Link"), matchParams());
        LinearLayout phoneStatusCard = card(false);
        phoneNotificationValue = monitorRow(phoneStatusCard, "Notification access");
        phoneSelectedAppsValue = monitorRow(phoneStatusCard, "Selected apps");
        phoneLocationValue = monitorRow(phoneStatusCard, "Location");
        phoneClipboardValue = monitorRow(phoneStatusCard, "Clipboard");
        phoneTrustedValue = monitorRow(phoneStatusCard, "Trusted Flipper");
        root.addView(phoneStatusCard, matchCardParams());

        root.addView(sectionTitle("Permissions and data"), matchParams());
        LinearLayout phoneActions = card(false);
        phoneActions.addView(phoneActionRow(
                "Notification access",
                "Allow selected notifications to appear on the Flipper.",
                "Open",
                this::openNotificationAccess));
        phoneActions.addView(divider());
        phoneActions.addView(phoneActionRow(
                "Notification apps",
                "Choose exactly which applications FlipNet stores.",
                "Choose",
                this::chooseNotificationApps));
        phoneActions.addView(divider());
        phoneActions.addView(phoneActionRow(
                "Phone location",
                "Refresh the position shared only on request.",
                "Allow",
                this::requestPhoneLocation));
        phoneActions.addView(divider());
        phoneActions.addView(phoneActionRow(
                "Clipboard snapshot",
                "Capture the current clipboard for the mini-app.",
                "Sync",
                this::syncPhoneClipboard));
        phoneActions.addView(divider());
        phoneActions.addView(phoneActionRow(
                "Trusted Flipper",
                "Forget the BLE device saved after pairing.",
                "Reset",
                this::resetTrustedFlipper));
        root.addView(phoneActions, matchCardParams());
        return pageScroll(root);
    }

    // Construit la console sur un écran dédié.
    private View buildConsolePage() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(10), dp(16), dp(14));
        root.setBackgroundColor(COLOR_BACKGROUND);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(titleRow, matchParams());

        TextView title = text("Live console", 16f, COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));
        titleRow.addView(smallChip("LIVE"), wrapParams());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionRowParams = matchParams();
        actionRowParams.topMargin = dp(10);
        actionRowParams.bottomMargin = dp(10);
        root.addView(actions, actionRowParams);
        addConsoleAction(actions, "Latest", this::scrollConsoleLatest);
        addConsoleAction(actions, "Copy", this::copyConsole);
        addConsoleAction(actions, "Share", this::exportConsole);
        addConsoleAction(actions, "Clear", this::clearConsole);

        LinearLayout console = consoleCard();
        LinearLayout.LayoutParams consoleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f);
        root.addView(console, consoleParams);

        consoleScroll = new ScrollView(this);
        consoleScroll.setFillViewport(true);
        consoleScroll.setVerticalScrollBarEnabled(true);
        consoleScroll.setScrollbarFadingEnabled(false);
        consoleScroll.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (consoleScrollAdjustment) return;
            consoleFollowLatest = isConsoleAtBottom();
        });

        consoleView = text("Waiting for traffic…", 12f, COLOR_ORANGE);
        consoleView.setTypeface(Typeface.MONOSPACE);
        consoleView.setTextIsSelectable(false);
        consoleView.setGravity(Gravity.TOP | Gravity.START);
        consoleView.setPadding(dp(16), dp(16), dp(16), dp(16));
        consoleView.setLineSpacing(dp(2), 1f);
        consoleScroll.addView(consoleView, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        console.addView(consoleScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView hint = text(
                "Scroll freely. New lines follow automatically only while you stay at the bottom.",
                11f,
                COLOR_MUTED);
        hint.setPadding(dp(2), dp(9), dp(2), 0);
        root.addView(hint, matchParams());
        return root;
    }

    // Construit les réglages sur leur propre écran.
    private View buildSettingsPage() {
        LinearLayout root = pageRoot();

        root.addView(sectionTitle("Bridge"), matchParams());
        LinearLayout toggles = card(false);
        toggles.addView(settingRow(
                "Run in background",
                "Automatically find and reconnect to the Flipper.",
                true));
        toggles.addView(divider());
        toggles.addView(settingRow(
                "Start on boot",
                "Start FlipNet after Android boots.",
                false));
        root.addView(toggles, matchCardParams());

        helperText = text("", 12f, COLOR_MUTED);
        helperText.setPadding(dp(2), dp(12), dp(2), 0);
        root.addView(helperText, matchParams());

        root.addView(sectionTitle("About"), matchParams());
        LinearLayout about = card(false);
        monitorRowWithValue(about, "Version", BuildConfig.VERSION_NAME);
        monitorRowWithValue(about, "Preferred link", "USB OTG");
        monitorRowWithValue(about, "Wireless fallback", "Bluetooth LE");
        root.addView(about, matchCardParams());
        return pageScroll(root);
    }

    // Construit la navigation principale en bas de l’écran.
    private View buildBottomNavigation() {
        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(dp(6), dp(6), dp(6), dp(8));
        navigation.setBackgroundColor(COLOR_SURFACE);

        navItems[TAB_HOME] = navigationItem("Home", R.drawable.ic_nav_home, TAB_HOME);
        navItems[TAB_COMPANION] = navigationItem(
                "Companion", R.drawable.ic_nav_monitor, TAB_COMPANION);
        navItems[TAB_FILES] = navigationItem(
                "Files", R.drawable.ic_nav_files, TAB_FILES);
        navItems[TAB_CONSOLE] = navigationItem(
                "Console", R.drawable.ic_nav_console, TAB_CONSOLE);
        navItems[TAB_SETTINGS] = navigationItem(
                "Settings", R.drawable.ic_nav_settings, TAB_SETTINGS);
        for (TextView item : navItems) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f);
            params.leftMargin = dp(2);
            params.rightMargin = dp(2);
            navigation.addView(item, params);
        }
        return navigation;
    }

    // Construit un bouton de navigation basse.
    private TextView navigationItem(String label, int icon, int tab) {
        TextView item = text(label, 10f, COLOR_MUTED);
        item.setGravity(Gravity.CENTER);
        item.setTypeface(Typeface.DEFAULT_BOLD);
        item.setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0);
        item.setCompoundDrawablePadding(dp(4));
        item.setPadding(dp(2), dp(5), dp(2), dp(4));
        item.setOnClickListener(view -> selectTab(tab));
        return item;
    }

    // Affiche un onglet et actualise la barre de navigation.
    private void selectTab(int tab) {
        if (tab < 0 || tab >= TAB_COUNT) tab = TAB_HOME;
        currentTab = tab;
        String[] titles = {"Home", "Companion", "Files", "Live console", "Settings"};
        if (pageTitle != null) pageTitle.setText(titles[tab]);

        for (int index = 0; index < TAB_COUNT; index++) {
            if (pages[index] != null) {
                pages[index].setVisibility(index == tab ? View.VISIBLE : View.GONE);
            }
            TextView item = navItems[index];
            if (item == null) continue;
            boolean selected = index == tab;
            item.setTextColor(selected ? COLOR_ORANGE : COLOR_MUTED);
            item.setCompoundDrawableTintList(ColorStateList.valueOf(
                    selected ? COLOR_ORANGE : COLOR_MUTED));
            item.setBackground(selected ? roundedBackground(
                    COLOR_ORANGE_DARK,
                    COLOR_ORANGE_DARK,
                    14) : null);
        }
        if (tab == TAB_CONSOLE && consoleFollowLatest && consoleScroll != null) {
            consoleScroll.post(() -> consoleScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    // Construit l’écran de démarrage.
    private View buildSplash(View content) {
        LinearLayout splash = new LinearLayout(this);
        splash.setOrientation(LinearLayout.VERTICAL);
        splash.setGravity(Gravity.CENTER);
        splash.setBackgroundColor(COLOR_BACKGROUND);
        splash.setPadding(dp(32), dp(32), dp(32), dp(32));

        ImageView logo = logoView(224, 224);
        splash.addView(logo, wrapParams());

        TextView title = text("FlipNet", 32f, COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(18), 0, 0);
        splash.addView(title, wrapParams());

        TextView subtitle = text("Connecting your Flipper", 13f, COLOR_MUTED);
        subtitle.setPadding(0, dp(7), 0, 0);
        splash.addView(subtitle, wrapParams());

        ProgressBar progress = new ProgressBar(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        progressParams.topMargin = dp(18);
        splash.addView(progress, progressParams);

        handler.postDelayed(() -> {
            splash.animate()
                    .alpha(0f)
                    .setDuration(220)
                    .withEndAction(() -> splash.setVisibility(View.GONE))
                    .start();
            content.animate().alpha(1f).setDuration(220).start();
        }, SPLASH_DELAY_MS);
        return splash;
    }

    // Ajoute une action compacte à la console.
    private void addConsoleAction(
            LinearLayout parent,
            String label,
            View.OnClickListener listener) {
        TextView action = text(label, 11f, COLOR_ORANGE);
        action.setTypeface(Typeface.DEFAULT_BOLD);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(5), dp(9), dp(5), dp(9));
        action.setBackground(roundedBackground(COLOR_CARD_RAISED, COLOR_BORDER, 12));
        action.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        params.leftMargin = dp(3);
        params.rightMargin = dp(3);
        parent.addView(action, params);
    }

    // Indique si la console est déjà proche de sa dernière ligne.
    private boolean isConsoleAtBottom() {
        if (consoleScroll == null) return true;
        View child = consoleScroll.getChildAt(0);
        if (child == null) return true;
        int viewportHeight = consoleScroll.getHeight()
                - consoleScroll.getPaddingTop()
                - consoleScroll.getPaddingBottom();
        return ConsoleScrollPolicy.isAtBottom(
                consoleScroll.getScrollY(),
                child.getHeight(),
                viewportHeight,
                dp(28));
    }

    // Met à jour le journal sans déplacer une lecture en cours.
    private void updateConsoleText(String displayedLog) {
        if (displayedLog.equals(lastConsoleText)) return;
        boolean followLatest = consoleFollowLatest || isConsoleAtBottom();
        int savedScrollY = consoleScroll == null ? 0 : consoleScroll.getScrollY();
        lastConsoleText = displayedLog;
        consoleScrollAdjustment = true;
        consoleView.clearFocus();
        consoleView.setText(displayedLog);
        consoleScroll.post(() -> {
            View child = consoleScroll.getChildAt(0);
            if (followLatest) {
                consoleScroll.fullScroll(View.FOCUS_DOWN);
                consoleFollowLatest = true;
            } else if (child != null) {
                int viewportHeight = consoleScroll.getHeight()
                        - consoleScroll.getPaddingTop()
                        - consoleScroll.getPaddingBottom();
                int restoredScrollY = ConsoleScrollPolicy.restoredScrollY(
                        savedScrollY,
                        child.getHeight(),
                        viewportHeight);
                consoleScroll.scrollTo(0, restoredScrollY);
                consoleFollowLatest = false;
            }
            consoleScrollAdjustment = false;
        });
    }

    // Replace la console sur la dernière entrée.
    private void scrollConsoleLatest(View ignored) {
        consoleFollowLatest = true;
        consoleScrollAdjustment = true;
        consoleScroll.post(() -> {
            consoleScroll.fullScroll(View.FOCUS_DOWN);
            consoleScrollAdjustment = false;
        });
    }

    // Efface la console.
    private void clearConsole(View ignored) {
        getSharedPreferences(FlipNetService.PREFERENCES, MODE_PRIVATE)
                .edit()
                .remove(FlipNetService.KEY_TRAFFIC_LOG)
                .apply();
        lastConsoleText = "";
        consoleFollowLatest = true;
        refreshUiState();
    }

    // Copie le journal de la console.
    private void copyConsole(View ignored) {
        String log = currentConsoleLog();
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("FlipNet console", log));
        Toast.makeText(this, "Console copied", Toast.LENGTH_SHORT).show();
    }

    // Partage le journal de la console.
    private void exportConsole(View ignored) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "FlipNet live console");
        share.putExtra(Intent.EXTRA_TEXT, currentConsoleLog());
        startActivity(Intent.createChooser(share, "Share console"));
    }

    // Retourne le journal de la console.
    private String currentConsoleLog() {
        String log = getSharedPreferences(FlipNetService.PREFERENCES, MODE_PRIVATE)
                .getString(FlipNetService.KEY_TRAFFIC_LOG, "");
        return log == null || log.isEmpty() ? "No console entries." : log;
    }

    // Construit une racine verticale avec marges cohérentes.
    private LinearLayout pageRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(28));
        root.setBackgroundColor(COLOR_BACKGROUND);
        return root;
    }

    // Place une page verticale dans son propre défilement.
    private ScrollView pageScroll(View content) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BACKGROUND);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    // Construit un titre de section.
    private TextView sectionTitle(String value) {
        TextView view = text(value, 13f, COLOR_MUTED);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(24), 0, dp(9));
        return view;
    }

    // Construit une ligne d’état.
    private TextView statusRow(String title) {
        TextView view = text(title, 14f, COLOR_TEXT);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(0, dp(4), 0, dp(4));
        return view;
    }

    // Ajoute une métrique compacte à l’accueil.
    private TextView metricCell(LinearLayout parent, String label, boolean first) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(12), dp(14), dp(12), dp(14));
        cell.setBackground(roundedBackground(COLOR_CARD, COLOR_BORDER, 16));

        TextView value = text("—", 16f, COLOR_TEXT);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        cell.addView(value, matchParams());

        TextView name = text(label, 11f, COLOR_MUTED);
        name.setPadding(0, dp(5), 0, 0);
        cell.addView(name, matchParams());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        if (!first) params.leftMargin = dp(7);
        parent.addView(cell, params);
        return value;
    }

    // Construit une ligne de surveillance.
    private TextView monitorRow(LinearLayout card, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));

        TextView name = text(label, 13f, COLOR_MUTED);
        row.addView(name, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        TextView value = text("—", 13f, COLOR_TEXT);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setGravity(Gravity.END);
        row.addView(value, wrapParams());
        card.addView(row, matchParams());
        return value;
    }

    // Ajoute une valeur fixe à une carte.
    private void monitorRowWithValue(LinearLayout card, String label, String value) {
        TextView target = monitorRow(card, label);
        target.setText(value);
    }

    // Construit une ligne de paramètre.
    private View settingRow(String title, String subtitle, boolean serviceRow) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        LinearLayout textWrap = new LinearLayout(this);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        row.addView(textWrap, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        TextView titleView = text(title, 14f, COLOR_TEXT);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        textWrap.addView(titleView, matchParams());

        TextView subtitleView = text(subtitle, 12f, COLOR_MUTED);
        subtitleView.setPadding(0, dp(5), dp(10), 0);
        textWrap.addView(subtitleView, matchParams());

        Switch toggle = new Switch(this);
        toggle.setText("");
        applySwitchColors(toggle);
        if (serviceRow) {
            serviceSwitch = toggle;
            toggle.setOnCheckedChangeListener(this::onServiceToggled);
        } else {
            bootSwitch = toggle;
            toggle.setOnCheckedChangeListener(this::onBootToggled);
        }
        row.addView(toggle, wrapParams());
        return row;
    }

    // Construit une action Phone Link.
    private View phoneActionRow(
            String title,
            String subtitle,
            String actionLabel,
            View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textWrap = new LinearLayout(this);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        row.addView(textWrap, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        TextView titleView = text(title, 13f, COLOR_TEXT);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        textWrap.addView(titleView, matchParams());

        TextView subtitleView = text(subtitle, 11f, COLOR_MUTED);
        subtitleView.setPadding(0, dp(5), dp(10), 0);
        textWrap.addView(subtitleView, matchParams());

        TextView action = text(actionLabel, 11f, COLOR_ORANGE);
        action.setTypeface(Typeface.DEFAULT_BOLD);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(11), dp(8), dp(11), dp(8));
        action.setBackground(roundedBackground(COLOR_ORANGE_DARK, COLOR_ORANGE_DARK, 12));
        action.setOnClickListener(listener);
        row.addView(action, wrapParams());
        return row;
    }

    // Ouvre l’accès aux notifications.
    private void openNotificationAccess(View ignored) {
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    // Choisit les applications dont les notifications sont relayées.
    private void chooseNotificationApps(View ignored) {
        PackageManager manager = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> activities = queryLauncherActivities(manager, launcher);
        activities.sort(Comparator.comparing(
                (ResolveInfo item) -> String.valueOf(item.loadLabel(manager)),
                String.CASE_INSENSITIVE_ORDER));

        Map<String, String> applications = new LinkedHashMap<>();
        for (ResolveInfo item : activities) {
            if (item.activityInfo == null) continue;
            String packageName = item.activityInfo.packageName;
            if (getPackageName().equals(packageName) || applications.containsKey(packageName)) {
                continue;
            }
            CharSequence label = item.loadLabel(manager);
            applications.put(packageName, label == null ? packageName : label.toString());
        }
        if (applications.isEmpty()) {
            Toast.makeText(this, "No launcher applications found", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] packages = applications.keySet().toArray(new String[0]);
        String[] labels = applications.values().toArray(new String[0]);
        Set<String> selected = new HashSet<>(PhoneLink.selectedNotificationPackages(this));
        boolean[] checked = new boolean[packages.length];
        for (int index = 0; index < packages.length; index++) {
            checked[index] = selected.contains(packages[index]);
        }

        new AlertDialog.Builder(this)
                .setTitle("Notification apps")
                .setMultiChoiceItems(labels, checked, (dialog, which, enabled) -> {
                    if (enabled) selected.add(packages[which]);
                    else selected.remove(packages[which]);
                })
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    PhoneLink.setSelectedNotificationPackages(this, selected);
                    refreshUiState();
                })
                .show();
    }

    // Demande ou actualise la position du téléphone.
    private void requestPhoneLocation(View ignored) {
        if (PhoneLink.hasLocationPermission(this)) {
            PhoneLink.updateLocationSnapshot(this);
            Toast.makeText(this, "Location refreshed", Toast.LENGTH_SHORT).show();
            refreshUiState();
            return;
        }
        requestPermissions(
                new String[] {
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                PHONE_LOCATION_REQUEST);
    }

    // Synchronise le presse-papiers du téléphone.
    private void syncPhoneClipboard(View ignored) {
        boolean captured = PhoneLink.captureClipboard(this);
        Toast.makeText(
                this,
                captured ? "Clipboard synced" : "Clipboard is empty",
                Toast.LENGTH_SHORT).show();
        refreshUiState();
    }

    // Retire le Flipper approuvé.
    private void resetTrustedFlipper(View ignored) {
        PhoneLink.clearTrustedDevice(this);
        Toast.makeText(this, "Trusted Flipper reset", Toast.LENGTH_SHORT).show();
        refreshUiState();
    }

    // Liste les applications lançables.
    private List<ResolveInfo> queryLauncherActivities(
            PackageManager manager,
            Intent launcher) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return manager.queryIntentActivities(
                    launcher,
                    PackageManager.ResolveInfoFlags.of(0L));
        }
        return queryLauncherActivitiesLegacy(manager, launcher);
    }

    // Liste les applications sur un ancien Android.
    @SuppressWarnings("deprecation")
    private List<ResolveInfo> queryLauncherActivitiesLegacy(
            PackageManager manager,
            Intent launcher) {
        return manager.queryIntentActivities(launcher, 0);
    }

    // Construit un séparateur.
    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(COLOR_BORDER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1));
        params.topMargin = dp(10);
        params.bottomMargin = dp(10);
        line.setLayoutParams(params);
        return line;
    }

    // Active ou arrête le service.
    private void onServiceToggled(CompoundButton button, boolean checked) {
        if (switchLock) return;
        if (checked) startBridge();
        else stopBridge();
    }

    // Active ou retire le démarrage automatique.
    private void onBootToggled(CompoundButton button, boolean checked) {
        if (switchLock) return;
        getSharedPreferences(FlipNetService.PREFERENCES, MODE_PRIVATE)
                .edit()
                .putBoolean(FlipNetService.KEY_BOOT_ENABLED, checked)
                .apply();
    }

    // Actualise tous les écrans déjà construits.
    private void refreshUiState() {
        SharedPreferences preferences =
                getSharedPreferences(FlipNetService.PREFERENCES, MODE_PRIVATE);
        boolean bluetoothGranted = bluetoothPermissionsGranted();
        boolean bluetoothOn = isBluetoothEnabled();
        boolean enabled = preferences.getBoolean(FlipNetService.KEY_ENABLED, false);
        boolean bootEnabled = preferences.getBoolean(FlipNetService.KEY_BOOT_ENABLED, false);
        String status = preferences.getString(FlipNetService.KEY_STATUS_TEXT, "Ready.");
        String transport = preferences.getString(FlipNetService.KEY_TRANSPORT, "");
        boolean loading = preferences.getBoolean(FlipNetService.KEY_LOADING, false);
        boolean connected = preferences.getBoolean(FlipNetService.KEY_CONNECTED, false);
        long requests = preferences.getLong(FlipNetService.KEY_REQUEST_COUNT, 0L);
        long errors = preferences.getLong(FlipNetService.KEY_ERROR_COUNT, 0L);
        long latency = preferences.getLong(FlipNetService.KEY_LAST_LATENCY_MS, 0L);
        int rssi = preferences.getInt(FlipNetService.KEY_RSSI, 0);
        long startedAt = preferences.getLong(FlipNetService.KEY_STARTED_AT, 0L);

        if (!enabled) {
            overviewIndicator.setTextColor(COLOR_MUTED);
            overviewTitle.setText("Service disabled");
            overviewText.setText("Enable it from Settings to accept USB OTG or BLE links.");
        } else if (loading) {
            overviewIndicator.setTextColor(COLOR_ORANGE);
            overviewTitle.setText("Connecting");
            overviewText.setText("Searching for the Flipper…");
        } else if (connected) {
            overviewIndicator.setTextColor(COLOR_SUCCESS);
            overviewTitle.setText("Connected to Flipper");
            overviewText.setText("FlipNet tools are ready.");
        } else {
            overviewIndicator.setTextColor(COLOR_DANGER);
            overviewTitle.setText("Flipper not connected");
            overviewText.setText("Open the FlipNet FAP, then connect USB OTG or BLE.");
        }
        overviewLoading.setVisibility(loading ? View.VISIBLE : View.GONE);

        String linkStatus = connected ? "Online" : (enabled ? "Waiting" : "Offline");
        homeStatusValue.setText(linkStatus);
        homeStatusValue.setTextColor(connected ? COLOR_SUCCESS : COLOR_TEXT);
        homeRequestsValue.setText(Long.toString(requests));
        homeErrorsValue.setText(Long.toString(errors));
        homeErrorsValue.setTextColor(errors > 0L ? COLOR_DANGER : COLOR_TEXT);

        bluetoothState.setText("Android Bluetooth  ·  " + (bluetoothOn ? "On" : "Off"));
        serviceState.setText("FlipNet service  ·  " + (enabled ? "On" : "Off"));
        if (connected) {
            flipperState.setText("Flipper  ·  Connected");
        } else if (enabled && loading) {
            flipperState.setText("Flipper  ·  Searching…");
        } else {
            flipperState.setText("Flipper  ·  Disconnected");
        }

        gatewayStatusValue.setText(linkStatus);
        gatewayStatusValue.setTextColor(connected ? COLOR_SUCCESS : COLOR_TEXT);
        gatewayTransportValue.setText(connected && transport != null && !transport.isEmpty()
                ? transport : "—");
        gatewayLatencyValue.setText(latency > 0L ? latency + " ms" : "—");
        gatewayRssiValue.setText(rssi != 0 ? rssi + " dBm" : "—");
        gatewayRequestsValue.setText(Long.toString(requests));
        gatewayErrorsValue.setText(Long.toString(errors));
        gatewayErrorsValue.setTextColor(errors > 0L ? COLOR_DANGER : COLOR_TEXT);
        gatewayUptimeValue.setText(formatUptime(enabled && startedAt > 0L
                ? System.currentTimeMillis() - startedAt : 0L));
        gatewayMessageValue.setText(status == null || status.isEmpty() ? "Ready." : status);

        phoneNotificationValue.setText(PhoneLink.hasNotificationAccess(this)
                ? "Enabled" : "Off");
        int selectedApps = PhoneLink.selectedNotificationPackages(this).size();
        phoneSelectedAppsValue.setText(selectedApps + (selectedApps == 1 ? " app" : " apps"));
        phoneLocationValue.setText(PhoneLink.locationSummary(this));
        phoneClipboardValue.setText(PhoneLink.clipboardSummary(this));
        String trustedDevice = PhoneLink.trustedDevice(this);
        phoneTrustedValue.setText(trustedDevice.isEmpty() ? "First connection" : "Saved");

        String trafficLog = preferences.getString(FlipNetService.KEY_TRAFFIC_LOG, "");
        String displayedLog = trafficLog == null || trafficLog.isEmpty()
                ? "Waiting for traffic…"
                : trafficLog;
        updateConsoleText(displayedLog);

        if (transport == null || transport.isEmpty()) {
            transportChip.setVisibility(View.GONE);
        } else {
            transportChip.setVisibility(View.VISIBLE);
            transportChip.setText(transport.toUpperCase(java.util.Locale.ROOT));
        }

        switchLock = true;
        serviceSwitch.setChecked(enabled);
        bootSwitch.setChecked(bootEnabled);
        switchLock = false;

        serviceSwitch.setEnabled(true);
        bootSwitch.setEnabled(true);
        serviceSwitch.setAlpha(1f);
        bootSwitch.setAlpha(1f);

        if (!bluetoothGranted) {
            helperText.setText("USB OTG remains available. Allow Bluetooth for wireless fallback.");
        } else if (!bluetoothOn) {
            helperText.setText("USB OTG is ready. Enable Bluetooth only when wireless fallback is needed.");
        } else {
            helperText.setText("USB OTG is preferred automatically; Bluetooth LE is the fallback.");
        }
    }

    // Formate la durée de fonctionnement.
    private String formatUptime(long elapsedMs) {
        if (elapsedMs <= 0L) return "—";
        long totalSeconds = elapsedMs / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(java.util.Locale.ROOT, "%dh %02dm", hours, minutes);
        }
        return String.format(java.util.Locale.ROOT, "%02dm %02ds", minutes, seconds);
    }

    // Traite les sélecteurs Android utilisés par le gestionnaire de fichiers.
    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (filesController != null
                && filesController.onActivityResult(requestCode, resultCode, data)) return;
        super.onActivityResult(requestCode, resultCode, data);
    }

    // Traite la réponse aux permissions Android.
    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PHONE_LOCATION_REQUEST) {
            PhoneLink.updateLocationSnapshot(this);
        }
        refreshUiState();
    }

    // Indique si le Bluetooth est actif.
    private boolean isBluetoothEnabled() {
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) return false;
        try {
            return adapter.isEnabled();
        } catch (SecurityException exception) {
            return false;
        }
    }

    // Construit le logo.
    private ImageView logoView(int widthDp, int heightDp) {
        ImageView view = new ImageView(this);
        view.setImageResource(R.drawable.flipnet_logo);
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        view.setAdjustViewBounds(true);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(heightDp)));
        return view;
    }

    // Construit une pastille de transport.
    private TextView chip() {
        TextView view = text("", 11f, COLOR_ORANGE);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(11), dp(7), dp(11), dp(7));
        view.setBackground(roundedBackground(COLOR_ORANGE_DARK, COLOR_ORANGE_DARK, 14));
        view.setVisibility(View.GONE);
        return view;
    }

    // Construit une petite pastille.
    private TextView smallChip(String value) {
        TextView view = text(value, 10f, COLOR_ORANGE);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(9), dp(5), dp(9), dp(5));
        view.setBackground(roundedBackground(COLOR_ORANGE_DARK, COLOR_ORANGE_DARK, 12));
        return view;
    }

    // Construit un texte Android.
    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    // Applique les couleurs d’un interrupteur.
    private void applySwitchColors(Switch toggle) {
        int[][] states = new int[][] {
                new int[] {android.R.attr.state_checked},
                new int[] {}
        };
        toggle.setThumbTintList(new ColorStateList(
                states,
                new int[] {COLOR_ORANGE, Color.rgb(116, 116, 116)}));
        toggle.setTrackTintList(new ColorStateList(
                states,
                new int[] {Color.rgb(153, 68, 12), Color.rgb(48, 48, 48)}));
    }

    // Construit la carte noire de console.
    private LinearLayout consoleCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedBackground(Color.BLACK, COLOR_BORDER, 18));
        return card;
    }

    // Construit une carte Android sombre.
    private LinearLayout card(boolean raised) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(17), dp(17), dp(17));
        card.setBackground(roundedBackground(
                raised ? COLOR_CARD_RAISED : COLOR_CARD,
                COLOR_BORDER,
                18));
        return card;
    }

    // Construit un fond arrondi.
    private GradientDrawable roundedBackground(int fill, int stroke, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(dp(radiusDp));
        if (stroke != Color.TRANSPARENT) background.setStroke(dp(1), stroke);
        return background;
    }

    // Crée des dimensions adaptées.
    private LinearLayout.LayoutParams matchParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    // Crée les dimensions d’une carte.
    private LinearLayout.LayoutParams matchCardParams() {
        LinearLayout.LayoutParams params = matchParams();
        params.topMargin = dp(7);
        return params;
    }

    // Crée des dimensions compactes.
    private LinearLayout.LayoutParams wrapParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    // Convertit des dp en pixels.
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // Demande les permissions manquantes.
    private void requestNeededPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), PERMISSIONS_REQUEST);
        }
    }

    // Indique si les permissions Bluetooth sont accordées.
    private boolean bluetoothPermissionsGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                            == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                            == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    // Arrête la passerelle Android.
    private void stopBridge() {
        getSharedPreferences(FlipNetService.PREFERENCES, MODE_PRIVATE)
                .edit()
                .putBoolean(FlipNetService.KEY_ENABLED, false)
                .apply();
        stopService(new Intent(this, FlipNetService.class));
        refreshUiState();
    }

    // Démarre la passerelle Android.
    private void startBridge() {
        getSharedPreferences(FlipNetService.PREFERENCES, MODE_PRIVATE)
                .edit()
                .putBoolean(FlipNetService.KEY_ENABLED, true)
                .apply();
        Intent intent = new Intent(this, FlipNetService.class);
        intent.setAction(FlipNetService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        refreshUiState();
    }
}
