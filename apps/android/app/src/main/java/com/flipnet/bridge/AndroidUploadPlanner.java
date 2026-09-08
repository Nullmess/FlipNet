package com.flipnet.bridge;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Construit une file d'upload a partir du Storage Access Framework Android.
final class AndroidUploadPlanner {
    private static final int MAX_REMOTE_PATH_BYTES = 255;

    private AndroidUploadPlanner() {
    }

    static Plan files(ContentResolver resolver, List<Uri> uris, String destination) throws IOException {
        if (uris == null || uris.isEmpty()) throw new IOException("No file selected");
        List<Item> items = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Uri uri : uris) {
            DocumentInfo info = queryOpenDocument(resolver, uri);
            String key = info.name.toLowerCase(Locale.ROOT);
            if (!names.add(key)) {
                throw new IOException("Two selected files have the same name: " + info.name);
            }
            String remotePath = childPath(destination, info.name);
            validateRemotePath(remotePath);
            items.add(Item.file(remotePath, uri, info.size));
        }
        return new Plan(items, uris.size(), 0);
    }

    static Plan folder(ContentResolver resolver, Uri treeUri, String destination) throws IOException {
        if (treeUri == null) throw new IOException("No folder selected");
        final String rootId;
        try {
            rootId = DocumentsContract.getTreeDocumentId(treeUri);
        } catch (IllegalArgumentException error) {
            throw new IOException("Android did not return a browsable folder", error);
        }
        Uri rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId);
        DocumentInfo root = queryDocument(resolver, rootUri);
        if (!root.directory) throw new IOException("The selected item is not a folder");
        String rootPath = childPath(destination, root.name);
        validateRemotePath(rootPath);

        List<Item> items = new ArrayList<>();
        items.add(Item.directory(rootPath));
        Counter counter = new Counter();
        counter.directories = 1;
        Set<String> visited = new HashSet<>();
        visited.add(rootId);
        appendChildren(resolver, treeUri, rootId, rootPath, items, counter, visited);
        return new Plan(items, counter.files, counter.directories);
    }

    private static void appendChildren(
            ContentResolver resolver,
            Uri treeUri,
            String parentId,
            String remoteParent,
            List<Item> items,
            Counter counter,
            Set<String> visited) throws IOException {
        List<DocumentInfo> children = queryChildren(resolver, treeUri, parentId);
        children.sort(Comparator
                .comparing((DocumentInfo info) -> !info.directory)
                .thenComparing(info -> info.name.toLowerCase(Locale.ROOT)));
        for (DocumentInfo child : children) {
            String remotePath = childPath(remoteParent, child.name);
            validateRemotePath(remotePath);
            Uri childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.id);
            if (child.directory) {
                if (!visited.add(child.id)) continue;
                items.add(Item.directory(remotePath));
                counter.directories++;
                appendChildren(resolver, treeUri, child.id, remotePath, items, counter, visited);
            } else {
                items.add(Item.file(remotePath, childUri, child.size));
                counter.files++;
            }
        }
    }

    private static DocumentInfo queryOpenDocument(ContentResolver resolver, Uri uri) throws IOException {
        String name = null;
        long size = -1L;
        try (Cursor cursor = resolver.query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                if (!cursor.isNull(0)) name = cursor.getString(0);
                if (!cursor.isNull(1)) size = cursor.getLong(1);
            }
        } catch (RuntimeException error) {
            throw new IOException("Cannot read the selected file", error);
        }
        if (!validName(name)) {
            throw new IOException("Unsupported file name: " + displayNameForError(name));
        }
        return new DocumentInfo("", name, false, size);
    }

    private static DocumentInfo queryDocument(ContentResolver resolver, Uri uri) throws IOException {
        String[] columns = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
        };
        try (Cursor cursor = resolver.query(uri, columns, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) throw new IOException("Cannot read folder information");
            return fromCursor(cursor);
        } catch (RuntimeException error) {
            throw new IOException("Cannot read folder information", error);
        }
    }

    private static List<DocumentInfo> queryChildren(
            ContentResolver resolver, Uri treeUri, String parentId) throws IOException {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId);
        String[] columns = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
        };
        List<DocumentInfo> result = new ArrayList<>();
        try (Cursor cursor = resolver.query(childrenUri, columns, null, null, null)) {
            if (cursor == null) throw new IOException("Cannot read folder contents");
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        } catch (RuntimeException error) {
            throw new IOException("Cannot read folder contents", error);
        }
        return result;
    }

    private static DocumentInfo fromCursor(Cursor cursor) throws IOException {
        String id = cursor.isNull(0) ? "" : cursor.getString(0);
        String name = cursor.isNull(1) ? "" : cursor.getString(1);
        String mime = cursor.isNull(2) ? "" : cursor.getString(2);
        long size = cursor.isNull(3) ? -1L : cursor.getLong(3);
        if (id.isEmpty()) {
            throw new IOException("Android cannot access this item: " + displayNameForError(name));
        }
        if (!validName(name)) {
            throw new IOException("Unsupported item name: " + displayNameForError(name));
        }
        boolean directory = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
        return new DocumentInfo(id, name, directory, size);
    }

    private static void validateRemotePath(String path) throws IOException {
        if (path == null || path.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_REMOTE_PATH_BYTES) {
            throw new IOException("Path is too long for the Flipper: " + path);
        }
    }

    private static boolean validName(String name) {
        if (name == null || name.isEmpty() || ".".equals(name) || "..".equals(name)) return false;
        for (int index = 0; index < name.length(); index++) {
            char value = name.charAt(index);
            if (value == '/' || value == '\\' || value < 0x20) return false;
        }
        return true;
    }

    private static String displayNameForError(String name) {
        return name == null || name.isEmpty() ? "(unnamed item)" : "\"" + name + "\"";
    }

    private static String childPath(String parent, String name) {
        return parent.endsWith("/") ? parent + name : parent + "/" + name;
    }

    static final class Plan {
        final List<Item> items;
        final int fileCount;
        final int directoryCount;

        Plan(List<Item> items, int fileCount, int directoryCount) {
            this.items = items;
            this.fileCount = fileCount;
            this.directoryCount = directoryCount;
        }
    }

    static final class Item {
        final boolean directory;
        final String path;
        final Uri uri;
        final long size;

        private Item(boolean directory, String path, Uri uri, long size) {
            this.directory = directory;
            this.path = path;
            this.uri = uri;
            this.size = size;
        }

        static Item directory(String path) {
            return new Item(true, path, null, 0L);
        }

        static Item file(String path, Uri uri, long size) {
            return new Item(false, path, uri, size);
        }

        String name() {
            int separator = path.lastIndexOf('/');
            return separator < 0 ? path : path.substring(separator + 1);
        }
    }

    private static final class DocumentInfo {
        final String id;
        final String name;
        final boolean directory;
        final long size;

        DocumentInfo(String id, String name, boolean directory, long size) {
            this.id = id;
            this.name = name;
            this.directory = directory;
            this.size = size;
        }
    }

    private static final class Counter {
        int files;
        int directories;
    }
}
