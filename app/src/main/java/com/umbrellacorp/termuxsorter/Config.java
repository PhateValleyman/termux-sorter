package com.umbrellacorp.termuxsorter;

import android.content.Context;
import java.io.InputStream;
import java.util.Scanner;

public class Config {

    public static String loadDefault(Context context) {

        try {

            InputStream input =
                context.getAssets().open("default.json");

            Scanner scanner =
                new Scanner(input).useDelimiter("\\A");

            String json =
                scanner.hasNext() ? scanner.next() : "";

            scanner.close();

            return format(json);

        } catch(Exception e) {

            return "Chyba načítání konfigurace";

        }
    }


    private static String format(String json) {

        return json
            .replace("{", "")
            .replace("}", "")
            .replace("\"", "")
            .replace(",", "")
            .replace("[", "")
            .replace("]", "")
            .replace(":", " → ");

    }
}
