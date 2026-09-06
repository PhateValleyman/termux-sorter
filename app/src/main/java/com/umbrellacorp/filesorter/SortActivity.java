package com.umbrellacorp.filesorter;

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
 * For each incoming file:
 *  1. Sorts it into Downloads/<destination>/<filename> based on the rules
 *     from {@link Config}, matched by file extension.
 *  2. Runs the matched rule's action:
 *       - no actionType  -> opens a new Termux session cd'd into the destination
 *       - "APP"/"TERMUX_RUN" -> delegated to {@link ActionRunner}
 *     (only for the last processed file, to avoid opening several
 *     Termux sessions / apps when sharing multiple files at once)
 */
public class SortActivity extends Activity {

    private static final String TAG = "FileSorterActivity";

    private static final int REQUEST_WRITE_STORAGE = 1001;
    private static final int REQUEST_RUN_COMMAND = 1002;

    private static final String TERMUX_PACKAGE = "com.termux";
    private static final String TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService";
    private static final String TERMUX_RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND";
    private static final String TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND";
    private static final String TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash";

    // Stav poslední úspěšně zpracované položky - použije se po případném
    // schválení RUN_COMMAND oprávnění.
    private Config.Rule pendingRule;
    private String pendingAbsolutePath;
    private int pendingOk;
    private int pendingFailed;

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

        boolean granted = grantResults.length > 0
            && grantResults[0] == PackageManager.PERMISSION_GRANTED;

        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (granted) {
                processIntent();
            } else {
                Toast.makeText(this, "Bez oprávnění nelze soubory přesunout", Toast.LENGTH_LONG).show();
                finish();
            }

        } else if (requestCode == REQUEST_RUN_COMMAND) {
            if (granted) {
                runPendingAction();
            } else {
                showSummaryToast();
            }
            finish();
        }
    }

    private boolean needsLegacyStoragePermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
    }

    private boolean hasLegacyStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasRunCommandPermission() {
        return ContextCompat.checkSelfPermission(this, TERMUX_RUN_COMMAND_PERMISSION)
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

        pendingOk = 0;
        pendingFailed = 0;
        pendingRule = null;
        pendingAbsolutePath = null;

        for (Uri uri : uris) {
            SortOutcome outcome = sortSingleFile(uri, ruleSet);
            if (outcome != null) {
                pendingOk++;
                pendingRule = outcome.matchedRule;
                pendingAbsolutePath = outcome.absolutePath;
            } else {
                pendingFailed++;
            }
        }

        if (pendingOk == 0) {
            Toast.makeText(this, "Přesun selhal", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Akce (otevření Termuxu / spuštění appky/skriptu) se řeší jen pro
        // poslední úspěšně zpracovaný soubor.
        boolean needsRunCommand = pendingRule == null
            || pendingRule.actionType == null
            || "TERMUX_RUN".equals(pendingRule.actionType);

        if (needsRunCommand && !hasRunCommandPermission()) {
            ActivityCompat.requestPermissions(
                this,
                new String[]{TERMUX_RUN_COMMAND_PERMISSION},
                REQUEST_RUN_COMMAND
            );
            return; // pokračuje se v onRequestPermissionsResult
        }

        runPendingAction();
        finish();
    }

    private void runPendingAction() {
        if (pendingRule != null && pendingRule.actionType != null) {
            // Pravidlo má vlastní akci (APP / TERMUX_RUN / NONE).
            ActionRunner.run(this, pendingRule, pendingAbsolutePath);
            if (pendingFailed > 0) showSummaryToast();
        } else {
            // Výchozí chování: otevřít Termux přímo ve složce, kam se soubor uložil.
            String folder = new File(pendingAbsolutePath).getParent();
            openTermuxAt(folder != null ? folder : pendingAbsolutePath);
        }
    }

    private void showSummaryToast() {
        String message = (pendingFailed > 0)
            ? "Přesunuto " + pendingOk + ", selhalo " + pendingFailed
            : "Přesunuto do " + pendingAbsolutePath;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /**
     * Otevře nový Termux session s pracovním adresářem nastaveným na absolutePath.
     * Vyžaduje, aby měl Termux v ~/.termux/termux.properties nastaveno
     * "allow-external-apps = true" - jinak Termux požadavek tiše odmítne.
     */
    private void openTermuxAt(String absolutePath) {
        try {
            Intent intent = new Intent();
            intent.setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE);
            intent.setAction(TERMUX_RUN_COMMAND_ACTION);
            intent.putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_BASH);
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{});
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", absolutePath);
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
            // "0" = otevřít novou session a rovnou na ni přepnout do popředí.
            intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }

        } catch (Exception e) {
            Log.w(TAG, "Nelze otevřít Termux (není nainstalovaný / RUN_COMMAND zakázán?)", e);
            Toast.makeText(this, "Přesunuto do " + absolutePath, Toast.LENGTH_LONG).show();
        }
    }

    private static class SortOutcome {
        final Config.Rule matchedRule; // null = shodovalo se jen výchozí pravidlo
        final String absolutePath;

        SortOutcome(Config.Rule matchedRule, String absolutePath) {
            this.matchedRule = matchedRule;
            this.absolutePath = absolutePath;
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

    private SortOutcome sortSingleFile(Uri sourceUri, Config.RuleSet ruleSet) {
        try {
            String fileName = resolveFileName(sourceUri);
            String extension = extractExtension(fileName, sourceUri);
            Config.Rule matchedRule = matchRule(extension, ruleSet);
            String destinationFolder = (matchedRule != null) ? matchedRule.destination : ruleSet.defaultDestination;

            String finalFileName = copyToDownloads(sourceUri, destinationFolder, fileName);
            String absolutePath = new File(downloadsDir(destinationFolder), finalFileName).getAbsolutePath();

            return new SortOutcome(matchedRule, absolutePath);

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

        String mime = getContentResolver().getType(uri);
        if (mime != null) {
            String fromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if (fromMime != null) return fromMime.toLowerCase(Locale.ROOT);
        }

        return "";
    }

    /** @return the matched rule, or null if only the default destination applies */
    private Config.Rule matchRule(String extension, Config.RuleSet ruleSet) {
        for (Config.Rule rule : ruleSet.rules) {
            for (String ruleExt : rule.extensions) {
                if (ruleExt.trim().toLowerCase(Locale.ROOT).equals(extension)) {
                    return rule;
                }
            }
        }
        return null;
    }

    private File downloadsDir(String destinationFolder) {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS);
        return new File(downloadsDir, destinationFolder);
    }

    /** @return the actual file name used (may differ from requested on collision) */
    private String copyToDownloads(Uri sourceUri, String destinationFolder, String fileName)
            throws IOException {

        ContentResolver resolver = getContentResolver();

        try (InputStream input = resolver.openInputStream(sourceUri)) {
            if (input == null) throw new IOException("Cannot open input stream for " + sourceUri);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return copyViaMediaStore(input, destinationFolder, fileName);
            } else {
                return copyViaLegacyFile(input, destinationFolder, fileName);
            }
        }
    }

    private String copyViaMediaStore(InputStream input, String destinationFolder, String fileName)
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

        // MediaStore si samo přejmenuje soubor při kolizi (přidá " (1)" apod.),
        // ale finální jméno raději ověříme přes DISPLAY_NAME, ať path sedí.
        try (Cursor c = getContentResolver().query(
                targetUri, new String[]{MediaStore.Downloads.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            }
        }
        return fileName;
    }

    private String copyViaLegacyFile(InputStream input, String destinationFolder, String fileName)
            throws IOException {

        File targetDir = downloadsDir(destinationFolder);

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Cannot create directory " + targetDir);
        }

        // Ochrana proti přepsání existujícího souboru se stejným jménem.
        String finalName = fileName;
        File targetFile = new File(targetDir, finalName);
        int suffix = 1;
        String baseName = fileName;
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            baseName = fileName.substring(0, dot);
            ext = fileName.substring(dot);
        }
        while (targetFile.exists()) {
            finalName = baseName + "_" + suffix + ext;
            targetFile = new File(targetDir, finalName);
            suffix++;
        }

        try (OutputStream output = new FileOutputStream(targetFile)) {
            streamCopy(input, output);
        }

        return finalName;
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
