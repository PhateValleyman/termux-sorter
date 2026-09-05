package com.valleyman.termuxsorter;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        setContentView(R.layout.activity_main);

        Button add = findViewById(R.id.addRule);
        Button save = findViewById(R.id.save);

        add.setOnClickListener(v -> {
            // TODO add rule editor
        });

        save.setOnClickListener(v -> {
            // TODO save config
        });
    }
}
