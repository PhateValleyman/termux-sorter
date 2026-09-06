package com.umbrellacorp.filesorter;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Loads and persists the sorting rules.
 *
 * Rules live in the app's internal storage (writable) and are seeded once
 * from assets/default.json (read-only, bundled in the APK) on first run.
 */
public class Config {

    private static final String TAG = "FileSorterConfig";
    private static final String CONFIG_FILE = "rules.json";

    public static class Rule {
        public final List<String> extensions;
        public final String destination;

        public Rule(List<String> extensions, String destination) {
            this.extensions = extensions;
            this.destination = destination;
        }
    }

    public static class RuleSet {
        public final List<Rule> rules;
        public final String defaultDestination;

        public RuleSet(List<Rule> rules, String defaultDestination) {
            this.rules = rules;
            this.defaultDestination = defaultDestination;
        }
    }

    /** Loads the current rule set, seeding it from assets on first run. */
    public static RuleSet load(Context context) {
        File configFile = new File(context.getFilesDir(), CONFIG_FILE);

        try {
            if (!configFile.exists()) {
                seedFromAssets(context, configFile);
            }
            String json = readFile(configFile);
            return parse(json);

        } catch (Exception e) {
            Log.e(TAG, "Failed to load config, falling back to bundled default", e);
            try {
                String json = readAsset(context, "default.json");
                return parse(json);
            } catch (Exception fallbackError) {
                Log.e(TAG, "Bundled default.json is also unreadable", fallbackError);
                return new RuleSet(new ArrayList<>(), "Other");
            }
        }
    }

    /** Persists the given rule set to internal storage. */
    public static void save(Context context, RuleSet ruleSet) {
        try {
            JSONObject root = new JSONObject();
            JSONArray rulesArray = new JSONArray();

            for (Rule rule : ruleSet.rules) {
                JSONObject ruleObj = new JSONObject();
                ruleObj.put("extensions", new JSONArray(rule.extensions));
                ruleObj.put("destination", rule.destination);
                rulesArray.put(ruleObj);
            }

            root.put("rules", rulesArray);
            root.put("default", ruleSet.defaultDestination);

            File configFile = new File(context.getFilesDir(), CONFIG_FILE);
            try (FileOutputStream out = new FileOutputStream(configFile)) {
                out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to save config", e);
        }
    }

    /** Human-readable rendering used by the rules TextView. */
    public static String render(RuleSet ruleSet) {
        StringBuilder sb = new StringBuilder();

        for (Rule rule : ruleSet.rules) {
            sb.append(String.join(", ", rule.extensions))
              .append(" → ")
              .append(rule.destination)
              .append("\n");
        }

        sb.append("(výchozí) → ").append(ruleSet.defaultDestination);
        return sb.toString();
    }

    private static void seedFromAssets(Context context, File target) throws Exception {
        String json = readAsset(context, "default.json");
        try (FileOutputStream out = new FileOutputStream(target)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readAsset(Context context, String name) throws Exception {
        try (InputStream input = context.getAssets().open(name)) {
            return readStream(input);
        }
    }

    private static String readFile(File file) throws Exception {
        try (InputStream input = new FileInputStream(file)) {
            return readStream(input);
        }
    }

    private static String readStream(InputStream input) {
        try (Scanner scanner = new Scanner(input, "UTF-8").useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    private static RuleSet parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray rulesArray = root.optJSONArray("rules");
        List<Rule> rules = new ArrayList<>();

        if (rulesArray != null) {
            for (int i = 0; i < rulesArray.length(); i++) {
                JSONObject ruleObj = rulesArray.getJSONObject(i);

                JSONArray extArray = ruleObj.getJSONArray("extensions");
                List<String> extensions = new ArrayList<>();
                for (int j = 0; j < extArray.length(); j++) {
                    extensions.add(extArray.getString(j));
                }

                rules.add(new Rule(extensions, ruleObj.getString("destination")));
            }
        }

        String defaultDestination = root.optString("default", "Other");
        return new RuleSet(rules, defaultDestination);
    }
}
