package com.flipnet.bridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

// Explorateur de fichiers epure pour le stockage du Flipper.
final class FlipperFilesController {
    static final int REQUEST_UPLOAD_FILES = 5201;
    static final int REQUEST_DOWNLOAD = 5202;
    static final int REQUEST_UPLOAD_FOLDER = 5203;

    private static final int COLOR_BACKGROUND = Color.rgb(5, 5, 5);
    private static final int COLOR_SURFACE = Color.rgb(14, 14, 14);
    private static final int COLOR_SURFACE_2 = Color.rgb(22, 22, 22);
    private static final int COLOR_ORANGE = Color.rgb(255, 122, 24);
    private static final int COLOR_TEXT = Color.rgb(246, 246, 246);
    private static final int COLOR_MUTED = Color.rgb(150, 150, 150);
    private static final int COLOR_BORDER = Color.rgb(38, 38, 38);
    private static final int COLOR_DANGER = Color.rgb(255, 96, 96);
    private static final int COLOR_OK = Color.rgb(116, 214, 139);

    private final Activity activity;
    private final List<Entry> entries = new ArrayList<>();
    private final List<AndroidUploadPlanner.Item> uploadQueue = new ArrayList<>();

    private LinearLayout listContainer;
    private LinearLayout breadcrumbContainer;
    private LinearLayout transferPanel;
    private TextView sdButton;
    private TextView internalButton;
    private TextView storageSummary;
    private TextView connectionBadge;
    private TextView statusView;
    private TextView uploadButton;
    private TextView folderButton;
    private TextView upButton;
    private ProgressBar progress;
    private TextView progressLabel;
    private TextView cancelButton;

    private String currentPath = "/ext";
    private String pendingDownloadPath = "";
    private boolean receiverRegistered;
    private long extTotal;
    private long extFree;
    private long intTotal;
    private long intFree;
    private AndroidUploadPlanner.Item activeUploadItem;
    private boolean uploadBatchActive;
    private boolean uploadBatchCancelRequested;
    private int uploadBatchCompletedItems;
    private int uploadBatchTotalItems;
    private int uploadBatchFiles;
    private int uploadBatchDirectories;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!FlipperFileTransferManager.ACTION_EVENT.equals(intent.getAction())) return;
            handleEvent(intent);
        }
    };

    FlipperFilesController(Activity activity) {
        this.activity = activity;
    }

    View buildPage() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BACKGROUND);

        root.addView(buildHeader(), match());
        root.addView(buildStorageBar(), match());
        root.addView(buildNavigationBar(), match());
        root.addView(divider(), dividerParams());

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        listContainer = new LinearLayout(activity);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(dp(12), dp(4), dp(12), dp(12));
        scroll.addView(listContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        statusView = text("", 12f, COLOR_MUTED);
        statusView.setPadding(dp(16), dp(7), dp(16), dp(7));
        statusView.setVisibility(View.GONE);
        root.addView(statusView, match());

        root.addView(buildTransferPanel(), match());
        root.addView(buildBottomActions(), match());

        renderEmpty("Connect the Flipper to browse its files.");
        updateStorageButtons();
        updateConnectionBadge();
        updateBreadcrumbs();
        return root;
    }

    void onResume() {
        registerReceiver();
        requestSpace();
        refresh();
    }

    void onPause() {
        unregisterReceiver();
    }

    @SuppressWarnings("deprecation")
    boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_UPLOAD_FILES && requestCode != REQUEST_UPLOAD_FOLDER
                && requestCode != REQUEST_DOWNLOAD) return false;
        if (resultCode != Activity.RESULT_OK || data == null) {
            if (requestCode == REQUEST_DOWNLOAD) pendingDownloadPath = "";
            return true;
        }

        if (requestCode == REQUEST_DOWNLOAD) {
            Uri uri = data.getData();
            String source = pendingDownloadPath;
            pendingDownloadPath = "";
            if (uri != null && !source.isEmpty()) {
                send(FlipperFileTransferManager.COMMAND_DOWNLOAD, source, "", uri.toString());
            }
            return true;
        }

        if (uploadBatchActive) {
            toast("An upload is already running");
            return true;
        }
        final String destination = currentPath;
        if (requestCode == REQUEST_UPLOAD_FILES) {
            List<Uri> uris = selectedUris(data);
            if (!uris.isEmpty()) prepareFileUpload(uris, destination);
            return true;
        }

        List<Uri> treeUris = selectedUris(data);
        if (!treeUris.isEmpty()) prepareFolderUpload(treeUris, destination);
        return true;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(14), dp(18), dp(8));

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Files", 23f, COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titles.addView(title, match());
        TextView subtitle = text("Flipper storage", 12f, COLOR_MUTED);
        subtitle.setPadding(0, dp(2), 0, 0);
        titles.addView(subtitle, match());
        header.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        connectionBadge = text("Offline", 11f, COLOR_MUTED);
        connectionBadge.setTypeface(Typeface.DEFAULT_BOLD);
        connectionBadge.setGravity(Gravity.CENTER);
        connectionBadge.setPadding(dp(10), dp(6), dp(10), dp(6));
        header.addView(connectionBadge, wrap());
        return header;
    }

    private View buildStorageBar() {
        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(14), dp(6), dp(14), dp(8));

        LinearLayout switcher = new LinearLayout(activity);
        switcher.setOrientation(LinearLayout.HORIZONTAL);
        sdButton = storageButton("SD card", view -> switchStorage("/ext"));
        internalButton = storageButton("Internal", view -> switchStorage("/int"));
        switcher.addView(sdButton, weighted());
        LinearLayout.LayoutParams second = weighted();
        second.leftMargin = dp(8);
        switcher.addView(internalButton, second);
        block.addView(switcher, match());

        storageSummary = text("Storage information unavailable", 11f, COLOR_MUTED);
        storageSummary.setPadding(dp(4), dp(7), dp(4), 0);
        block.addView(storageSummary, match());
        return block;
    }

    private View buildNavigationBar() {
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(5), dp(12), dp(8));

        upButton = squareAction("‹", view -> goUp());
        bar.addView(upButton, new LinearLayout.LayoutParams(dp(42), dp(42)));

        HorizontalScrollView breadcrumbScroll = new HorizontalScrollView(activity);
        breadcrumbScroll.setHorizontalScrollBarEnabled(false);
        breadcrumbScroll.setFillViewport(true);
        breadcrumbContainer = new LinearLayout(activity);
        breadcrumbContainer.setOrientation(LinearLayout.HORIZONTAL);
        breadcrumbContainer.setGravity(Gravity.CENTER_VERTICAL);
        breadcrumbScroll.addView(breadcrumbContainer, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams breadcrumbParams = new LinearLayout.LayoutParams(
                0, dp(42), 1f);
        breadcrumbParams.leftMargin = dp(7);
        breadcrumbParams.rightMargin = dp(7);
        bar.addView(breadcrumbScroll, breadcrumbParams);

        TextView refresh = squareAction("↻", view -> refresh());
        bar.addView(refresh, new LinearLayout.LayoutParams(dp(42), dp(42)));
        return bar;
    }

    private View buildTransferPanel() {
        transferPanel = new LinearLayout(activity);
        transferPanel.setOrientation(LinearLayout.VERTICAL);
        transferPanel.setPadding(dp(16), dp(10), dp(16), dp(10));
        transferPanel.setBackgroundColor(COLOR_SURFACE);
        transferPanel.setVisibility(View.GONE);

        LinearLayout line = new LinearLayout(activity);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        progressLabel = text("Transfer", 12f, COLOR_TEXT);
        progressLabel.setTypeface(Typeface.DEFAULT_BOLD);
        line.addView(progressLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cancelButton = text("Cancel", 11f, COLOR_ORANGE);
        cancelButton.setTypeface(Typeface.DEFAULT_BOLD);
        cancelButton.setPadding(dp(10), dp(4), dp(2), dp(4));
        cancelButton.setOnClickListener(view -> cancelCurrentOperation());
        line.addView(cancelButton, wrap());
        transferPanel.addView(line, match());

        progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgressTintList(ColorStateList.valueOf(COLOR_ORANGE));
        LinearLayout.LayoutParams progressParams = match();
        progressParams.topMargin = dp(8);
        transferPanel.addView(progress, progressParams);
        return transferPanel;
    }

    private View buildBottomActions() {
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(12), dp(10), dp(12), dp(12));
        actions.setBackgroundColor(COLOR_SURFACE);

        folderButton = primaryAction("New folder", false, view -> createFolderDialog());
        uploadButton = primaryAction("Upload", true, view -> chooseUpload());
        actions.addView(folderButton, weighted());
        LinearLayout.LayoutParams uploadParams = weighted();
        uploadParams.leftMargin = dp(8);
        actions.addView(uploadButton, uploadParams);
        return actions;
    }

    private void handleEvent(Intent intent) {
        String kind = safe(intent.getStringExtra(FlipperFileTransferManager.EXTRA_KIND));
        String path = safe(intent.getStringExtra(FlipperFileTransferManager.EXTRA_PATH));
        String data = safe(intent.getStringExtra(FlipperFileTransferManager.EXTRA_DATA));
        String message = safe(intent.getStringExtra(FlipperFileTransferManager.EXTRA_MESSAGE));
        long current = intent.getLongExtra(FlipperFileTransferManager.EXTRA_CURRENT, 0L);
        long total = intent.getLongExtra(FlipperFileTransferManager.EXTRA_TOTAL, 0L);

        switch (kind) {
            case FlipperFileTransferManager.EVENT_LIST:
                if (currentPath.equals(path)) {
                    parseEntries(data);
                    renderEntries();
                    showStatus(entries.size() + (entries.size() == 1 ? " item" : " items"), false);
                }
                break;
            case FlipperFileTransferManager.EVENT_SPACE:
                updateSpace(data);
                break;
            case FlipperFileTransferManager.EVENT_PROGRESS:
                if (uploadBatchActive && activeUploadItem != null
                        && activeUploadItem.path.equals(path)) {
                    handleUploadProgress(message, current, total);
                } else {
                    showProgress(message, current, total);
                }
                break;
            case FlipperFileTransferManager.EVENT_DONE:
                if (uploadBatchActive && activeUploadItem != null
                        && activeUploadItem.path.equals(path)) {
                    handleUploadItemDone();
                    break;
                }
                hideProgress();
                showStatus(message.isEmpty() ? "Done" : message, false);
                toast(message.isEmpty() ? "Done" : message);
                requestSpace();
                refresh();
                break;
            case FlipperFileTransferManager.EVENT_CANCELLED:
                if (uploadBatchActive) {
                    finishUploadBatch(false, message.isEmpty() ? "Upload cancelled" : message);
                    break;
                }
                hideProgress();
                showStatus(message.isEmpty() ? "Transfer cancelled" : message, false);
                break;
            case FlipperFileTransferManager.EVENT_ERROR:
                if (uploadBatchActive && activeUploadItem != null
                        && (path.isEmpty() || activeUploadItem.path.equals(path))) {
                    abortUploadBatch(message);
                    break;
                }
                hideProgress();
                showStatus(message.isEmpty() ? "File operation failed" : message, true);
                if (FlipperFileTransferManager.EVENT_LIST.equals(data)
                        && (path.isEmpty() || currentPath.equals(path))) {
                    renderEmpty("Unable to read this folder. Tap refresh to retry.");
                }
                break;
            default:
                break;
        }
    }

    private void refresh() {
        updateConnectionBadge();
        updateBreadcrumbs();
        updateStorageButtons();
        if (!connected()) {
            showStatus("Flipper disconnected", true);
            renderEmpty("Connect the Flipper, then open FlipNet on the device.");
            return;
        }
        showStatus("Loading…", false);
        send(FlipperFileTransferManager.COMMAND_LIST, currentPath, "", "");
    }

    private void requestSpace() {
        if (!connected()) return;
        send(FlipperFileTransferManager.COMMAND_SPACE, "", "", "");
    }

    private void switchStorage(String path) {
        if (path.equals(currentPath)) return;
        currentPath = path;
        entries.clear();
        renderEmpty("Loading…");
        updateStorageButtons();
        updateBreadcrumbs();
        refresh();
    }

    private void goUp() {
        if (isRoot(currentPath)) return;
        currentPath = parentPath(currentPath);
        refresh();
    }

    private void updateBreadcrumbs() {
        if (breadcrumbContainer == null) return;
        breadcrumbContainer.removeAllViews();
        boolean external = currentPath.startsWith("/ext");
        String rootPath = external ? "/ext" : "/int";
        addCrumb(external ? "SD card" : "Internal", rootPath, isRoot(currentPath));

        String relative = currentPath.length() > rootPath.length()
                ? currentPath.substring(rootPath.length() + 1) : "";
        if (!relative.isEmpty()) {
            String[] parts = relative.split("/");
            String path = rootPath;
            for (int index = 0; index < parts.length; index++) {
                TextView separator = text("›", 17f, COLOR_MUTED);
                separator.setGravity(Gravity.CENTER);
                separator.setPadding(dp(5), 0, dp(5), 0);
                breadcrumbContainer.addView(separator, wrap());
                path = path + "/" + parts[index];
                addCrumb(parts[index], path, index == parts.length - 1);
            }
        }
        if (upButton != null) {
            boolean enabled = !isRoot(currentPath);
            upButton.setEnabled(enabled);
            upButton.setAlpha(enabled ? 1f : 0.35f);
        }
    }

    private void addCrumb(String label, String path, boolean current) {
        TextView crumb = text(label, 12f, current ? COLOR_TEXT : COLOR_MUTED);
        crumb.setTypeface(current ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        crumb.setSingleLine(true);
        crumb.setPadding(dp(3), dp(7), dp(3), dp(7));
        if (!current) crumb.setOnClickListener(view -> {
            currentPath = path;
            refresh();
        });
        breadcrumbContainer.addView(crumb, wrap());
    }

    private void chooseUpload() {
        if (!isWritablePath(currentPath)) {
            toast("Internal storage is read-only");
            return;
        }
        if (uploadBatchActive) {
            toast("An upload is already running");
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("Upload")
                .setItems(new String[]{"Files", "Folder"}, (dialog, which) -> {
                    if (which == 0) chooseUploadFiles();
                    else chooseUploadFolder();
                })
                .show();
    }

    @SuppressWarnings("deprecation")
    private void chooseUploadFiles() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivityForResult(picker, REQUEST_UPLOAD_FILES);
    }

    @SuppressWarnings("deprecation")
    private void chooseUploadFolder() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        // Le picker Android standard ne propose qu'un dossier, mais certains fournisseurs
        // honorent EXTRA_ALLOW_MULTIPLE. Le resultat est gere dans les deux cas.
        picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        activity.startActivityForResult(picker, REQUEST_UPLOAD_FOLDER);
    }

    private List<Uri> selectedUris(Intent data) {
        List<Uri> result = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int index = 0; index < clip.getItemCount(); index++) {
                Uri uri = clip.getItemAt(index).getUri();
                if (uri != null && !containsUri(result, uri)) result.add(uri);
            }
        } else if (data.getData() != null) {
            result.add(data.getData());
        }
        return result;
    }

    private static boolean containsUri(List<Uri> uris, Uri candidate) {
        String value = candidate.toString();
        for (Uri uri : uris) {
            if (value.equals(uri.toString())) return true;
        }
        return false;
    }

    private void prepareFileUpload(List<Uri> uris, String destination) {
        showStatus("Preparing files…", false);
        new Thread(() -> {
            try {
                AndroidUploadPlanner.Plan plan = AndroidUploadPlanner.files(
                        activity.getContentResolver(), uris, destination);
                activity.runOnUiThread(() -> confirmAndStartUpload(plan, destination));
            } catch (Exception error) {
                activity.runOnUiThread(() -> uploadPreparationFailed(error));
            }
        }, "FlipNetUploadFiles").start();
    }

    private void prepareFolderUpload(List<Uri> treeUris, String destination) {
        showStatus(treeUris.size() > 1 ? "Reading folders…" : "Reading folder…", false);
        new Thread(() -> {
            try {
                List<AndroidUploadPlanner.Item> items = new ArrayList<>();
                List<String> roots = new ArrayList<>();
                int files = 0;
                int directories = 0;
                for (Uri treeUri : treeUris) {
                    AndroidUploadPlanner.Plan part = AndroidUploadPlanner.folder(
                            activity.getContentResolver(), treeUri, destination);
                    if (!part.items.isEmpty()) {
                        String root = part.items.get(0).path.toLowerCase(Locale.ROOT);
                        if (roots.contains(root)) {
                            throw new java.io.IOException("Two selected folders have the same name");
                        }
                        roots.add(root);
                    }
                    items.addAll(part.items);
                    files += part.fileCount;
                    directories += part.directoryCount;
                }
                AndroidUploadPlanner.Plan plan = new AndroidUploadPlanner.Plan(
                        items, files, directories);
                activity.runOnUiThread(() -> confirmAndStartUpload(plan, destination));
            } catch (Exception error) {
                activity.runOnUiThread(() -> uploadPreparationFailed(error));
            }
        }, "FlipNetUploadFolder").start();
    }

    private void uploadPreparationFailed(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isEmpty()) message = "Cannot prepare upload";
        showStatus(message, true);
        toast(message);
    }

    private void confirmAndStartUpload(AndroidUploadPlanner.Plan plan, String destination) {
        if (plan == null || plan.items.isEmpty()) {
            toast("Nothing to upload");
            return;
        }
        int conflicts = topLevelConflicts(plan, destination);
        if (conflicts <= 0) {
            startUploadPlan(plan);
            return;
        }
        String noun = conflicts == 1 ? "item" : "items";
        new AlertDialog.Builder(activity)
                .setTitle("Replace existing " + noun + "?")
                .setMessage(conflicts + " " + noun + " already exist in this folder. "
                        + "Files with the same name will be replaced and folders will be merged.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (dialog, which) -> startUploadPlan(plan))
                .show();
    }

    private int topLevelConflicts(AndroidUploadPlanner.Plan plan, String destination) {
        int conflicts = 0;
        String prefix = destination.endsWith("/") ? destination : destination + "/";
        for (AndroidUploadPlanner.Item item : plan.items) {
            if (!item.path.startsWith(prefix)) continue;
            String relative = item.path.substring(prefix.length());
            if (relative.indexOf('/') >= 0) continue;
            if (containsName(relative)) conflicts++;
        }
        return conflicts;
    }

    private void startUploadPlan(AndroidUploadPlanner.Plan plan) {
        uploadQueue.clear();
        uploadQueue.addAll(plan.items);
        uploadBatchActive = true;
        uploadBatchCancelRequested = false;
        uploadBatchCompletedItems = 0;
        uploadBatchTotalItems = plan.items.size();
        uploadBatchFiles = plan.fileCount;
        uploadBatchDirectories = plan.directoryCount;
        activeUploadItem = null;
        updateStorageButtons();
        startNextUploadItem();
    }

    private void startNextUploadItem() {
        if (!uploadBatchActive) return;
        if (uploadBatchCancelRequested) {
            finishUploadBatch(false, "Upload cancelled");
            return;
        }
        if (uploadQueue.isEmpty()) {
            finishUploadBatch(true, uploadSummary());
            return;
        }
        activeUploadItem = uploadQueue.remove(0);
        int number = uploadBatchCompletedItems + 1;
        if (activeUploadItem.directory) {
            showProgress("Creating " + number + "/" + uploadBatchTotalItems
                    + " · " + activeUploadItem.name(), 0L, 0L);
            send(FlipperFileTransferManager.COMMAND_MKDIR, activeUploadItem.path, "", "");
        } else {
            showProgress("Uploading " + number + "/" + uploadBatchTotalItems
                    + " · " + activeUploadItem.name(), 0L, activeUploadItem.size);
            send(FlipperFileTransferManager.COMMAND_UPLOAD, activeUploadItem.path, "",
                    activeUploadItem.uri.toString());
        }
    }

    private void handleUploadProgress(String message, long current, long total) {
        if (!uploadBatchActive || activeUploadItem == null) return;
        int number = uploadBatchCompletedItems + 1;
        String detail = message == null ? "" : message;
        if (detail.contains("Reconnect") || detail.contains("reconnect")
                || detail.contains("Connection") || detail.contains("Retry")
                || detail.contains("retry") || detail.contains("busy")) {
            showProgress(detail + " · " + activeUploadItem.name(), current, total);
            return;
        }
        showProgress("Uploading " + number + "/" + uploadBatchTotalItems
                + " · " + activeUploadItem.name(), current, total);
    }

    private void handleUploadItemDone() {
        if (!uploadBatchActive || activeUploadItem == null) return;
        uploadBatchCompletedItems++;
        activeUploadItem = null;
        startNextUploadItem();
    }

    private void abortUploadBatch(String message) {
        uploadQueue.clear();
        activeUploadItem = null;
        uploadBatchActive = false;
        uploadBatchCancelRequested = false;
        hideProgress();
        updateStorageButtons();
        String detail = message == null || message.isEmpty() ? "Upload failed" : message;
        showStatus(detail, true);
        toast(detail);
        requestSpace();
        refresh();
    }

    private void finishUploadBatch(boolean success, String message) {
        uploadQueue.clear();
        activeUploadItem = null;
        uploadBatchActive = false;
        uploadBatchCancelRequested = false;
        hideProgress();
        updateStorageButtons();
        showStatus(message, !success);
        toast(message);
        requestSpace();
        refresh();
    }

    private String uploadSummary() {
        if (uploadBatchDirectories == 0) {
            return uploadBatchFiles + (uploadBatchFiles == 1 ? " file uploaded" : " files uploaded");
        }
        String files = uploadBatchFiles + (uploadBatchFiles == 1 ? " file" : " files");
        String folders = uploadBatchDirectories
                + (uploadBatchDirectories == 1 ? " folder" : " folders");
        return "Upload complete · " + files + ", " + folders;
    }

    private void cancelCurrentOperation() {
        if (!uploadBatchActive) {
            send(FlipperFileTransferManager.COMMAND_CANCEL, "", "", "");
            return;
        }
        uploadQueue.clear();
        uploadBatchCancelRequested = true;
        if (activeUploadItem == null) {
            finishUploadBatch(false, "Upload cancelled");
        } else if (activeUploadItem.directory) {
            showStatus("Cancelling upload…", false);
        } else {
            send(FlipperFileTransferManager.COMMAND_CANCEL, "", "", "");
        }
    }

    @SuppressWarnings("deprecation")
    private void chooseDownload(Entry entry) {
        pendingDownloadPath = entry.path;
        Intent picker = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("application/octet-stream");
        picker.putExtra(Intent.EXTRA_TITLE, entry.name);
        picker.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        activity.startActivityForResult(picker, REQUEST_DOWNLOAD);
    }

    private void createFolderDialog() {
        if (uploadBatchActive) {
            toast("Upload in progress");
            return;
        }
        if (!isWritablePath(currentPath)) {
            toast("Internal storage is read-only");
            return;
        }
        EditText input = dialogInput("Folder name", "");
        new AlertDialog.Builder(activity)
                .setTitle("New folder")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!validName(name)) {
                        toast("Invalid folder name");
                        return;
                    }
                    send(FlipperFileTransferManager.COMMAND_MKDIR,
                            childPath(currentPath, name), "", "");
                })
                .show();
    }

    private void showEntryActions(Entry entry) {
        if (uploadBatchActive) {
            toast("Upload in progress");
            return;
        }
        List<String> options = new ArrayList<>();
        if (!entry.directory) options.add("Download");
        if (isWritablePath(entry.path)) {
            options.add("Rename");
            options.add("Delete");
        }
        if (options.isEmpty()) {
            toast("Internal storage is read-only");
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(entry.name)
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    String action = options.get(which);
                    if ("Download".equals(action)) chooseDownload(entry);
                    else if ("Rename".equals(action)) renameDialog(entry);
                    else if ("Delete".equals(action)) deleteDialog(entry);
                })
                .show();
    }

    private void renameDialog(Entry entry) {
        EditText input = dialogInput("Name", entry.name);
        input.setSelection(entry.name.length());
        new AlertDialog.Builder(activity)
                .setTitle("Rename")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Rename", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!validName(name)) {
                        toast("Invalid name");
                        return;
                    }
                    String destination = childPath(parentPath(entry.path), name);
                    send(FlipperFileTransferManager.COMMAND_RENAME,
                            entry.path, destination, "");
                })
                .show();
    }

    private void deleteDialog(Entry entry) {
        String message = entry.directory
                ? "Delete this folder and everything inside it?"
                : "Delete this file?";
        new AlertDialog.Builder(activity)
                .setTitle("Delete " + entry.name + "?")
                .setMessage(message + "\n\nThis cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> send(
                        FlipperFileTransferManager.COMMAND_DELETE,
                        entry.path, "", ""))
                .show();
    }

    private EditText dialogInput(String hint, String value) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(value);
        input.setTextColor(COLOR_TEXT);
        input.setHintTextColor(COLOR_MUTED);
        return input;
    }

    private void parseEntries(String payload) {
        entries.clear();
        if (payload == null || payload.isEmpty()) return;
        String[] lines = payload.split("\\n");
        for (String line : lines) {
            if (line.isEmpty()) continue;
            String[] fields = line.split("\\t", 3);
            if (fields.length != 3) continue;
            boolean directory = "D".equals(fields[0]);
            long size;
            try {
                size = Long.parseLong(fields[1]);
            } catch (NumberFormatException error) {
                size = 0L;
            }
            String name = fields[2];
            if (name.isEmpty() || ".".equals(name) || "..".equals(name)) continue;
            entries.add(new Entry(name, childPath(currentPath, name), directory, size));
        }
        entries.sort(Comparator
                .comparing((Entry entry) -> !entry.directory)
                .thenComparing(entry -> entry.name.toLowerCase(Locale.ROOT)));
    }

    private void renderEntries() {
        listContainer.removeAllViews();
        if (entries.isEmpty()) {
            renderEmpty("This folder is empty.");
            return;
        }
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            listContainer.addView(entryRow(entry), match());
            if (index < entries.size() - 1) listContainer.addView(divider(), dividerParams());
        }
    }

    private View entryRow(Entry entry) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(5), dp(7), dp(2), dp(7));
        row.setBackground(selectableSurface());

        TextView icon = text(entry.directory ? "D" : "F", 11f,
                entry.directory ? COLOR_ORANGE : COLOR_MUTED);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(background(
                entry.directory ? Color.rgb(49, 27, 12) : COLOR_SURFACE_2,
                entry.directory ? Color.rgb(96, 54, 20) : COLOR_BORDER,
                10));
        row.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, dp(8), 0);
        TextView name = text(entry.name, 13f, COLOR_TEXT);
        name.setSingleLine(true);
        name.setTypeface(entry.directory ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        labels.addView(name, match());
        TextView detail = text(entry.directory ? "Folder" : formatBytes(entry.size), 11f, COLOR_MUTED);
        detail.setPadding(0, dp(2), 0, 0);
        labels.addView(detail, match());
        row.addView(labels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView more = text("⋮", 20f, COLOR_MUTED);
        more.setGravity(Gravity.CENTER);
        more.setPadding(dp(8), dp(8), dp(8), dp(8));
        more.setOnClickListener(view -> showEntryActions(entry));
        row.addView(more, new LinearLayout.LayoutParams(dp(44), dp(44)));

        row.setOnClickListener(view -> {
            if (entry.directory) {
                currentPath = entry.path;
                refresh();
            } else {
                showEntryActions(entry);
            }
        });
        row.setOnLongClickListener(view -> {
            showEntryActions(entry);
            return true;
        });
        return row;
    }

    private void renderEmpty(String message) {
        if (listContainer == null) return;
        listContainer.removeAllViews();
        LinearLayout empty = new LinearLayout(activity);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(20), dp(54), dp(20), dp(54));
        TextView icon = text("—", 28f, COLOR_BORDER);
        icon.setGravity(Gravity.CENTER);
        empty.addView(icon, match());
        TextView label = text(message, 12f, COLOR_MUTED);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(8), 0, 0);
        empty.addView(label, match());
        listContainer.addView(empty, match());
    }

    private void updateSpace(String payload) {
        String[] values = payload.split(",", -1);
        if (values.length != 4) return;
        try {
            extTotal = Long.parseLong(values[0]);
            extFree = Long.parseLong(values[1]);
            intTotal = Long.parseLong(values[2]);
            intFree = Long.parseLong(values[3]);
            updateStorageSummary();
        } catch (NumberFormatException ignored) {
        }
    }

    private void updateStorageSummary() {
        if (storageSummary == null) return;
        boolean external = currentPath.startsWith("/ext");
        long total = external ? extTotal : intTotal;
        long free = external ? extFree : intFree;
        if (total <= 0L) {
            storageSummary.setText(external
                    ? "SD card · storage information unavailable"
                    : "Internal · read-only");
            return;
        }
        String suffix = external ? "" : " · read-only";
        storageSummary.setText(formatBytes(free) + " free of " + formatBytes(total) + suffix);
    }

    private void showProgress(String message, long current, long total) {
        transferPanel.setVisibility(View.VISIBLE);
        int value = total > 0L ? (int) Math.min(1000L, current * 1000L / total) : 0;
        progress.setIndeterminate(total <= 0L);
        progress.setProgress(value);
        String title = message.isEmpty() ? "Transfer" : message;
        progressLabel.setText(title + " · " + formatBytes(current)
                + (total > 0L ? " / " + formatBytes(total) : ""));
    }

    private void hideProgress() {
        transferPanel.setVisibility(View.GONE);
        progress.setIndeterminate(false);
    }

    private void showStatus(String message, boolean error) {
        if (statusView == null) return;
        if (message == null || message.isEmpty()) {
            statusView.setVisibility(View.GONE);
            return;
        }
        statusView.setText(message);
        statusView.setTextColor(error ? COLOR_DANGER : COLOR_MUTED);
        statusView.setVisibility(View.VISIBLE);
    }

    private void updateStorageButtons() {
        if (sdButton == null || internalButton == null) return;
        boolean external = currentPath.startsWith("/ext");
        styleSelected(sdButton, external);
        styleSelected(internalButton, !external);
        boolean writable = external && !uploadBatchActive;
        uploadButton.setEnabled(writable);
        folderButton.setEnabled(writable);
        uploadButton.setAlpha(writable ? 1f : 0.4f);
        folderButton.setAlpha(writable ? 1f : 0.4f);
        updateStorageSummary();
    }

    private void updateConnectionBadge() {
        if (connectionBadge == null) return;
        SharedPreferences prefs = activity.getSharedPreferences(
                FlipNetService.PREFERENCES, Context.MODE_PRIVATE);
        boolean isConnected = prefs.getBoolean(FlipNetService.KEY_CONNECTED, false);
        String transport = prefs.getString(FlipNetService.KEY_TRANSPORT, "");
        if (!isConnected) {
            connectionBadge.setText("Offline");
            connectionBadge.setTextColor(COLOR_MUTED);
            connectionBadge.setBackground(background(COLOR_SURFACE_2, COLOR_BORDER, 20));
            return;
        }
        String label = transport == null || transport.isEmpty() ? "Connected" : transport;
        connectionBadge.setText(label);
        connectionBadge.setTextColor(COLOR_OK);
        connectionBadge.setBackground(background(Color.rgb(16, 36, 22), Color.rgb(39, 76, 49), 20));
    }

    private void styleSelected(TextView view, boolean selected) {
        view.setTextColor(selected ? COLOR_TEXT : COLOR_MUTED);
        view.setBackground(background(
                selected ? COLOR_SURFACE_2 : COLOR_BACKGROUND,
                selected ? COLOR_ORANGE : COLOR_BORDER,
                12));
    }

    private void send(String command, String path, String argument, String uri) {
        SharedPreferences prefs = activity.getSharedPreferences(
                FlipNetService.PREFERENCES, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(FlipNetService.KEY_ENABLED, false)) {
            toast("Enable FlipNet in Settings first");
            return;
        }
        Intent request = new Intent(activity, FlipNetService.class);
        request.setAction(FlipperFileTransferManager.ACTION_COMMAND);
        request.putExtra(FlipperFileTransferManager.EXTRA_COMMAND, command);
        request.putExtra(FlipperFileTransferManager.EXTRA_PATH, safe(path));
        request.putExtra(FlipperFileTransferManager.EXTRA_ARGUMENT, safe(argument));
        request.putExtra(FlipperFileTransferManager.EXTRA_URI, safe(uri));
        activity.startService(request);
    }

    private boolean connected() {
        return activity.getSharedPreferences(FlipNetService.PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(FlipNetService.KEY_CONNECTED, false);
    }

    @SuppressWarnings("deprecation")
    private void registerReceiver() {
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter(FlipperFileTransferManager.ACTION_EVENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(receiver, filter);
        }
        receiverRegistered = true;
    }

    private void unregisterReceiver() {
        if (!receiverRegistered) return;
        try {
            activity.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
        }
        receiverRegistered = false;
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = activity.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getString(0);
            }
        } catch (RuntimeException ignored) {
        }
        String segment = uri.getLastPathSegment();
        return segment == null ? "upload.bin" : segment;
    }

    private boolean containsName(String name) {
        for (Entry entry : entries) {
            if (entry.name.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private static boolean validName(String name) {
        if (name == null || name.isEmpty() || ".".equals(name) || "..".equals(name)) return false;
        for (int index = 0; index < name.length(); index++) {
            char value = name.charAt(index);
            if (value == '/' || value == '\\' || value < 0x20) return false;
        }
        return true;
    }

    private static boolean isWritablePath(String path) {
        return path != null && ("/ext".equals(path) || path.startsWith("/ext/"));
    }

    private static boolean isRoot(String path) {
        return "/ext".equals(path) || "/int".equals(path);
    }

    private static String parentPath(String path) {
        if (isRoot(path)) return path;
        int separator = path.lastIndexOf('/');
        if (separator <= 4) return path.startsWith("/int") ? "/int" : "/ext";
        return path.substring(0, separator);
    }

    private static String childPath(String parent, String name) {
        return parent.endsWith("/") ? parent + name : parent + "/" + name;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double value = bytes / 1024.0;
        if (value < 1024.0) return String.format(Locale.ROOT, "%.1f KB", value);
        value /= 1024.0;
        if (value < 1024.0) return String.format(Locale.ROOT, "%.1f MB", value);
        value /= 1024.0;
        return String.format(Locale.ROOT, "%.2f GB", value);
    }

    private TextView storageButton(String label, View.OnClickListener listener) {
        TextView result = text(label, 12f, COLOR_MUTED);
        result.setTypeface(Typeface.DEFAULT_BOLD);
        result.setGravity(Gravity.CENTER);
        result.setPadding(dp(10), dp(10), dp(10), dp(10));
        result.setOnClickListener(listener);
        return result;
    }

    private TextView squareAction(String label, View.OnClickListener listener) {
        TextView result = text(label, 22f, COLOR_MUTED);
        result.setGravity(Gravity.CENTER);
        result.setBackground(background(COLOR_SURFACE, COLOR_BORDER, 12));
        result.setOnClickListener(listener);
        return result;
    }

    private TextView primaryAction(String label, boolean primary, View.OnClickListener listener) {
        TextView result = text(label, 12f, primary ? Color.BLACK : COLOR_TEXT);
        result.setTypeface(Typeface.DEFAULT_BOLD);
        result.setGravity(Gravity.CENTER);
        result.setPadding(dp(10), dp(11), dp(10), dp(11));
        result.setBackground(background(
                primary ? COLOR_ORANGE : COLOR_SURFACE_2,
                primary ? COLOR_ORANGE : COLOR_BORDER,
                12));
        result.setOnClickListener(listener);
        return result;
    }

    private GradientDrawable selectableSurface() {
        GradientDrawable drawable = background(COLOR_BACKGROUND, COLOR_BACKGROUND, 0);
        return drawable;
    }

    private View divider() {
        View line = new View(activity);
        line.setBackgroundColor(COLOR_BORDER);
        return line;
    }

    private TextView text(String value, float size, int color) {
        TextView result = new TextView(activity);
        result.setText(value);
        result.setTextSize(size);
        result.setTextColor(color);
        return result;
    }

    private GradientDrawable background(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (stroke != fill) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams dividerParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class Entry {
        final String name;
        final String path;
        final boolean directory;
        final long size;

        Entry(String name, String path, boolean directory, long size) {
            this.name = name;
            this.path = path;
            this.directory = directory;
            this.size = size;
        }
    }
}
