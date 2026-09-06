package com.umbrellacorp.filesorter;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

/**
 * Executes the optional per-rule action after a file has been sorted.
 *
 * Supported {@link Config.Rule#actionType} values:
 *   "APP"        - launch the app whose package name is in actionValue
 *   "TERMUX_RUN" - run the script/executable in actionValue against the
 *                  sorted file, in the background (no visible Termux switch)
 *
 * Requires com.termux.permission.RUN_COMMAND for "TERMUX_RUN" - the caller
 * (SortActivity) is responsible for requesting it before calling this.
 */
public class ActionRunner {

    private static final String TAG = "FileSorterAction";

    /**
     * @param absoluteFilePath the full path of the file in its new destination
     */
    public static void run(Context context, Config.Rule rule, String absoluteFilePath) {
        if (rule == null || rule.actionType == null) {
            return;
        }

        switch (rule.actionType) {
            case "APP":
                runApp(context, rule.actionValue);
                break;

            case "TERMUX_RUN":
                runTermuxScript(context, rule.actionValue, absoluteFilePath);
                break;

            case "NONE":
                // Explicitly do nothing.
                break;

            default:
                Log.w(TAG, "Unknown actionType: " + rule.actionType);
        }
    }

    private static void runApp(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            Log.w(TAG, "APP action with no package name configured");
            return;
        }

        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            context.startActivity(launchIntent);
        } else {
            Log.w(TAG, "App not installed: " + packageName);
            Toast.makeText(context, "Aplikace " + packageName + " není nainstalovaná", Toast.LENGTH_LONG).show();
        }
    }

    private static void runTermuxScript(Context context, String scriptPath, String absoluteFilePath) {
        if (scriptPath == null || scriptPath.isEmpty()) {
            Log.w(TAG, "TERMUX_RUN action with no script path configured");
            return;
        }

        try {
            Intent intent = new Intent("com.termux.RUN_COMMAND");
            intent.setClassName("com.termux", "com.termux.app.RunCommandService");
            intent.putExtra("com.termux.RUN_COMMAND_PATH", scriptPath);
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{absoluteFilePath});
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);

            context.startService(intent);

        } catch (Exception e) {
            Log.w(TAG, "Nelze spustit Termux skript " + scriptPath
                + " (chybí RUN_COMMAND oprávnění nebo allow-external-apps v Termuxu?)", e);
        }
    }
}
