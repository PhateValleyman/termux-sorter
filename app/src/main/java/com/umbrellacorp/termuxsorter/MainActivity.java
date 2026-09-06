package com.umbrellacorp.termuxsorter;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends Activity {

    private Config.RuleSet ruleSet;
    private TextView rulesView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        rulesView = findViewById(R.id.rulesView);

        ruleSet = Config.load(this);
        refreshRulesView();

        Button add = findViewById(R.id.addRule);
        Button save = findViewById(R.id.save);

        add.setOnClickListener(v -> showRuleDialog());

        save.setOnClickListener(v -> {
            Config.save(this, ruleSet);
            Toast.makeText(this, "Konfigurace uložena", Toast.LENGTH_SHORT).show();
        });
    }

    private void refreshRulesView() {
        rulesView.setText(Config.render(ruleSet));
    }

    private void showRuleDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        EditText ext = new EditText(this);
        ext.setHint("Přípony oddělené čárkou, např. mcworld,mcaddon");

        EditText folder = new EditText(this);
        folder.setHint("Složka např. Minecraft");

        box.addView(ext);
        box.addView(folder);

        new AlertDialog.Builder(this)
            .setTitle("Nové pravidlo")
            .setView(box)
            .setPositiveButton("Přidat", (dialog, which) -> {
                String extText = ext.getText().toString().trim();
                String folderText = folder.getText().toString().trim();

                if (TextUtils.isEmpty(extText) || TextUtils.isEmpty(folderText)) {
                    Toast.makeText(this, "Vyplň obě pole", Toast.LENGTH_SHORT).show();
                    return;
                }

                ArrayList<String> extensions = new ArrayList<>(
                    Arrays.asList(extText.split("\\s*,\\s*"))
                );

                ruleSet.rules.add(new Config.Rule(extensions, folderText));
                Config.save(this, ruleSet);
                refreshRulesView();

                Toast.makeText(
                    this,
                    extText + " → " + folderText,
                    Toast.LENGTH_LONG
                ).show();
            })
            .setNegativeButton("Zrušit", null)
            .show();
    }
}
