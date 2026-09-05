package com.umbrellacorp.termuxsorter;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        setContentView(R.layout.activity_main);

        TextView rulesView = findViewById(R.id.rulesView);

		rulesView.setText(
		    "📦 Minecraft\n" +
		    "   mcworld\n" +
		    "   mcaddon\n" +
		    "   mcpack\n\n" +

		    "📱 APKS\n" +
		    "   apk\n" +
		    "   apks\n" +
		    "   xapk\n\n" +

		    "📁 Archives\n" +
    		"   zip\n" +
    		"   7z\n" +
    		"   tar.gz\n"
		);

        Button add = findViewById(R.id.addRule);
        Button save = findViewById(R.id.save);

        add.setOnClickListener(v -> showRuleDialog());

        save.setOnClickListener(v ->
            Toast.makeText(
                this,
                "Konfigurace uložena",
                Toast.LENGTH_SHORT
            ).show()
        );
    }


    private void showRuleDialog() {

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        EditText ext = new EditText(this);
        ext.setHint("Přípona např. mcworld");

        EditText folder = new EditText(this);
        folder.setHint("Složka např. Minecraft");

        box.addView(ext);
        box.addView(folder);


        new AlertDialog.Builder(this)
            .setTitle("Nové pravidlo")
            .setView(box)
            .setPositiveButton(
                "Přidat",
                (dialog, which) -> {

                    String e = ext.getText().toString();
                    String f = folder.getText().toString();

                    Toast.makeText(
                        this,
                        e + " → " + f,
                        Toast.LENGTH_LONG
                    ).show();

                })
            .setNegativeButton(
                "Zrušit",
                null
            )
            .show();
    }
}
