package com.umbrellacorp.termuxsorter;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handles "Otevřít v... → Termux Sorter" (ACTION_SEND / ACTION_SEND_MULTIPLE / ACTION_VIEW).
 *
 * Sorts each incoming file into Downloads/<destination>/<filename> based on
 * the rules from {@link Config}, matched by file extension.
 */
public class SortActivity extends Activity {

    private static final String TAG = "TermuxSorterActivity";
    private static final int REQUEST_WRITE_STORAGE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (needsLegacyStoragePermission() && !hasLegacyStoragePermission()) {
            ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_WRITE_STORAGE
            );
            return; // pokračuje se v onRequestPermissionsResult
        }

        processIntent();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_WRITE_STORAGE) {
            boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;

            if (granted) {
                processIntent();
            } else {
                Toast.makeText(this,
                    "Bez oprávnění nelze soubory přesunout",
                    Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private boolean needsLegacyStoragePermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
    }

    private boolean hasLegacyStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED;
    }

    private void processIntent() {
        List<Uri> uris = extractUris(getIntent());

        if (uris.isEmpty()) {
            Toast.makeText(this, "Nenalezen žádný soubor k roztřídění", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Config.RuleSet ruleSet = Config.load(this);

        int ok = 0;
        int failed = 0;
        String lastDestination = null;

        for (Uri uri : uris) {
            SortResult result = sortSingleFile(uri, ruleSet);
            if (result != null) {
                ok++;
                lastDestination = result.destinationFolder;
            } else {
                failed++;
            }
        }

        String message;
        if (ok > 0 && failed == 0) {
            message = ok == 1
                ? "Přesunuto do Download/" + lastDestination
                : "Přesunuto " + ok + " souborů";
        } else if (ok > 0) {
            message = "Přesunuto " + ok + ", selhalo " + failed;
        } else {
            message = "Přesun selhal";
        }

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    private static class SortResult {
        final String destinationFolder;
        SortResult(String destinationFolder) {
            this.destinationFolder = destinationFolder;
        }
    }

    private List<Uri> extractUris(Intent intent) {
        List<Uri> uris = new ArrayList<>();
        if (intent == null) return uris;

        String action = intent.getAction();

        if (Intent.ACTION_SEND.equals(action)) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) uris.add(uri);

        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (list != null) uris.addAll(list);

        } else if (Intent.ACTION_VIEW.equals(action)) {
            Uri uri = intent.getData();
            if (uri != null) uris.add(uri);
        }

        return uris;
    }

    private SortResult sortSingleFile(Uri sourceUri, Config.RuleSet ruleSet) {
        try {
            String fileName = resolveFileName(sourceUri);
            String extension = extractExtension(fileName, sourceUri);
            String destinationFolder = matchDestination(extension, ruleSet);

            copyToDownloads(sourceUri, destinationFolder, fileName);

            return new SortResult(destinationFolder);

        } catch (Exception e) {
            Log.e(TAG, "Failed to sort " + sourceUri, e);
            return null;
        }
    }

    private String resolveFileName(Uri uri) {
        String result = null;

        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        result = cursor.getString(idx);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not query display name", e);
            }
        }

        if (result == null) {
            String path = uri.getPath();
            result = (path != null) ? new File(path).getName() : "soubor_" + System.currentTimeMillis();
        }

        return result;
    }

    private String extractExtension(String fileName, Uri uri) {
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        }

        // Fallback: derive from MIME type reported by the source app.
        String mime = getContentResolver().getType(uri);
        if (mime != null) {
            String fromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if (fromMime != null) return fromMime.toLowerCase(Locale.ROOT);
        }

        return "";
    }

    private String matchDestination(String extension, Config.RuleSet ruleSet) {
        for (Config.Rule rule : ruleSet.rules) {
            for (String ruleExt : rule.extensions) {
                if (ruleExt.trim().toLowerCase(Locale.ROOT).equals(extension)) {
                    return rule.destination;
                }
            }
        }
        return ruleSet.defaultDestination;
    }

    private void copyToDownloads(Uri sourceUri, String destinationFolder, String fileName)
            throws IOException {

        ContentResolver resolver = getContentResolver();

        try (InputStream input = resolver.openInputStream(sourceUri)) {
            if (input == null) throw new IOException("Cannot open input stream for " + sourceUri);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                copyViaMediaStore(input, destinationFolder, fileName);
            } else {
                copyViaLegacyFile(input, destinationFolder, fileName);
            }
        }
    }

    private void copyViaMediaStore(InputStream input, String destinationFolder, String fileName)
            throws IOException {

        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.RELATIVE_PATH,
            Environment.DIRECTORY_DOWNLOADS + "/" + destinationFolder);

        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        Uri targetUri = getContentResolver().insert(collection, values);

        if (targetUri == null) {
            throw new IOException("MediaStore refused to create entry for " + fileName);
        }

        try (OutputStream output = getContentResolver().openOutputStream(targetUri)) {
            if (output == null) throw new IOException("Cannot open output stream for " + targetUri);
            streamCopy(input, output);
        }
    }

    private void copyViaLegacyFile(InputStream input, String destinationFolder, String fileName)
            throws IOException {

        File downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS);
        File targetDir = new File(downloadsDir, destinationFolder);

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Cannot create directory " + targetDir);
        }

        File targetFile = new File(targetDir, fileName);

        try (OutputStream output = new FileOutputStream(targetFile)) {
            streamCopy(input, output);
        }
    }

    private void streamCopy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        output.flush();
    }
}
