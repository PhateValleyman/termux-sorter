package com.umbrellacorp.filesorter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {

    /** Nabídka v akčním spinneru, v tomto pořadí. */
    private static final String[] ACTION_LABELS = {
        "Výchozí (otevřít Termux ve složce)",
        "Otevřít aplikaci",
        "Spustit Termux skript",
        "Bez akce"
    };

    private Config.RuleSet ruleSet;
    private TextView defaultDestinationView;
    private ListView rulesListView;
    private RuleAdapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        defaultDestinationView = findViewById(R.id.defaultDestinationView);
        rulesListView = findViewById(R.id.rulesListView);

        ruleSet = Config.load(this);

        adapter = new RuleAdapter(ruleSet.rules);
        rulesListView.setAdapter(adapter);
        rulesListView.setOnItemClickListener((parent, view, position, id) ->
            showRuleDialog(ruleSet.rules.get(position))
        );

        refreshRulesView();

        Button add = findViewById(R.id.addRule);
        Button save = findViewById(R.id.save);

        add.setOnClickListener(v -> showRuleDialog(null));

        save.setOnClickListener(v -> {
            Config.save(this, ruleSet);
            Toast.makeText(this, "Konfigurace uložena", Toast.LENGTH_SHORT).show();
        });
    }

    private void refreshRulesView() {
        defaultDestinationView.setText("Výchozí složka (bez shody): " + ruleSet.defaultDestination);
        adapter.notifyDataSetChanged();
    }

    // ---------------------------------------------------------------
    // Seznam pravidel
    // ---------------------------------------------------------------

    private class RuleAdapter extends ArrayAdapter<Config.Rule> {
        RuleAdapter(List<Config.Rule> rules) {
            super(MainActivity.this, 0, rules);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.list_item_rule, parent, false);
            }

            Config.Rule rule = getItem(position);

            TextView title = convertView.findViewById(R.id.ruleTitle);
            TextView subtitle = convertView.findViewById(R.id.ruleSubtitle);

            title.setText(TextUtils.join(", ", rule.extensions) + "  →  " + rule.destination);
            subtitle.setText(describeAction(rule));

            return convertView;
        }
    }

    private String describeAction(Config.Rule rule) {
        if (rule.actionType == null) {
            return "otevře Termux ve složce";
        }
        switch (rule.actionType) {
            case "APP":
                return "spustí aplikaci: " + resolveAppLabel(rule.actionValue);
            case "TERMUX_RUN":
                return "spustí skript: " + rule.actionValue;
            case "NONE":
                return "bez akce";
            default:
                return rule.actionType;
        }
    }

    private String resolveAppLabel(String packageName) {
        if (packageName == null) return "(nevybráno)";
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(packageName, 0);
            return getPackageManager().getApplicationLabel(ai).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName + " (nenainstalováno)";
        }
    }

    // ---------------------------------------------------------------
    // Přidání / editace pravidla
    // ---------------------------------------------------------------

    /** existingRule == null → přidání nového pravidla, jinak editace. */
    private void showRuleDialog(Config.Rule existingRule) {
        boolean isEdit = existingRule != null;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);

        EditText ext = new EditText(this);
        ext.setHint("Přípony oddělené čárkou, např. mcworld,mcaddon");
        if (isEdit) ext.setText(TextUtils.join(",", existingRule.extensions));

        EditText folder = new EditText(this);
        folder.setHint("Složka např. Minecraft");
        if (isEdit) folder.setText(existingRule.destination);

        TextView actionLabel = new TextView(this);
        actionLabel.setText("Po přesunu:");
        actionLabel.setPadding(0, pad, 0, 0);

        Spinner actionSpinner = new Spinner(this);
        actionSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ACTION_LABELS));

        Button appPickerButton = new Button(this);
        appPickerButton.setVisibility(View.GONE);

        EditText scriptPathEdit = new EditText(this);
        scriptPathEdit.setHint("/data/data/com.termux/files/home/bin/skript.sh");
        scriptPathEdit.setVisibility(View.GONE);

        box.addView(ext);
        box.addView(folder);
        box.addView(actionLabel);
        box.addView(actionSpinner);
        box.addView(appPickerButton);
        box.addView(scriptPathEdit);

        // Stav vybrané aplikace - pole kvůli mutaci uvnitř lambd.
        String[] selectedPackage = {
            (isEdit && "APP".equals(existingRule.actionType)) ? existingRule.actionValue : null
        };

        Runnable refreshAppButtonLabel = () -> appPickerButton.setText(
            selectedPackage[0] != null
                ? "Aplikace: " + resolveAppLabel(selectedPackage[0])
                : "Vybrat aplikaci..."
        );
        refreshAppButtonLabel.run();

        appPickerButton.setOnClickListener(v -> showAppPicker((packageName, label) -> {
            selectedPackage[0] = packageName;
            refreshAppButtonLabel.run();
        }));

        int initialSelection = 0;
        if (isEdit && existingRule.actionType != null) {
            switch (existingRule.actionType) {
                case "APP": initialSelection = 1; break;
                case "TERMUX_RUN": initialSelection = 2; scriptPathEdit.setText(existingRule.actionValue); break;
                case "NONE": initialSelection = 3; break;
            }
        }
        actionSpinner.setSelection(initialSelection);
        applyActionVisibility(initialSelection, appPickerButton, scriptPathEdit);

        actionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyActionVisibility(position, appPickerButton, scriptPathEdit);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle(isEdit ? "Upravit pravidlo" : "Nové pravidlo")
            .setView(box)
            .setPositiveButton(isEdit ? "Uložit" : "Přidat", (dialog, which) -> {
                String extText = ext.getText().toString().trim();
                String folderText = folder.getText().toString().trim();

                if (TextUtils.isEmpty(extText) || TextUtils.isEmpty(folderText)) {
                    Toast.makeText(this, "Vyplň obě pole", Toast.LENGTH_SHORT).show();
                    return;
                }

                int selection = actionSpinner.getSelectedItemPosition();
                String actionType;
                String actionValue;

                switch (selection) {
                    case 1: // Otevřít aplikaci
                        if (selectedPackage[0] == null) {
                            Toast.makeText(this, "Nejdřív vyber aplikaci", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        actionType = "APP";
                        actionValue = selectedPackage[0];
                        break;
                    case 2: // Spustit Termux skript
                        String scriptPath = scriptPathEdit.getText().toString().trim();
                        if (TextUtils.isEmpty(scriptPath)) {
                            Toast.makeText(this, "Zadej cestu ke skriptu", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        actionType = "TERMUX_RUN";
                        actionValue = scriptPath;
                        break;
                    case 3: // Bez akce
                        actionType = "NONE";
                        actionValue = null;
                        break;
                    default: // Výchozí
                        actionType = null;
                        actionValue = null;
                }

                ArrayList<String> extensions = new ArrayList<>(
                    Arrays.asList(extText.split("\\s*,\\s*"))
                );

                Config.Rule newRule = new Config.Rule(extensions, folderText, actionType, actionValue);

                if (isEdit) {
                    int index = ruleSet.rules.indexOf(existingRule);
                    if (index >= 0) {
                        ruleSet.rules.set(index, newRule);
                    } else {
                        ruleSet.rules.add(newRule);
                    }
                } else {
                    ruleSet.rules.add(newRule);
                }

                Config.save(this, ruleSet);
                refreshRulesView();

                Toast.makeText(this, extText + " → " + folderText, Toast.LENGTH_LONG).show();
            })
            .setNegativeButton("Zrušit", null);

        if (isEdit) {
            builder.setNeutralButton("Smazat", (dialog, which) -> {
                ruleSet.rules.remove(existingRule);
                Config.save(this, ruleSet);
                refreshRulesView();
                Toast.makeText(this, "Pravidlo smazáno", Toast.LENGTH_SHORT).show();
            });
        }

        builder.show();
    }

    private void applyActionVisibility(int selection, Button appPickerButton, EditText scriptPathEdit) {
        appPickerButton.setVisibility(selection == 1 ? View.VISIBLE : View.GONE);
        scriptPathEdit.setVisibility(selection == 2 ? View.VISIBLE : View.GONE);
    }

    // ---------------------------------------------------------------
    // Výběr nainstalované aplikace
    // ---------------------------------------------------------------

    private interface AppPickedListener {
        void onPicked(String packageName, String label);
    }

    private void showAppPicker(AppPickedListener listener) {
        PackageManager pm = getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolved = pm.queryIntentActivities(launcherIntent, 0);
        Collections.sort(resolved, (a, b) ->
            a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString())
        );

        String[] labels = new String[resolved.size()];
        String[] packages = new String[resolved.size()];

        for (int i = 0; i < resolved.size(); i++) {
            ResolveInfo info = resolved.get(i);
            labels[i] = info.loadLabel(pm).toString();
            packages[i] = info.activityInfo.packageName;
        }

        new AlertDialog.Builder(this)
            .setTitle("Vyber aplikaci")
            .setItems(labels, (dialog, which) -> listener.onPicked(packages[which], labels[which]))
            .show();
    }
}
