package com.umbrellacorp.termuxsorter;

import android.content.Context;
import android.content.Intent;

public class ActionRunner {


    public static void run(
        Context context,
        Rule rule,
        String file
    ) {


        if (rule.actionType == null)
            return;


        if (rule.actionType.equals("APP")) {

            Intent i =
                context.getPackageManager()
                .getLaunchIntentForPackage(
                    rule.actionValue
                );

            if (i != null)
                context.startActivity(i);

        }


        if (rule.actionType.equals("TERMUX_RUN")) {


            Intent intent =
                new Intent(
                    "com.termux.RUN_COMMAND"
                );


            intent.setClassName(
                "com.termux",
                "com.termux.app.RunCommandService"
            );


            intent.putExtra(
                "com.termux.RUN_COMMAND_PATH",
                rule.actionValue
            );


            intent.putExtra(
                "com.termux.RUN_COMMAND_ARGUMENTS",
                new String[]{
                    file
                }
            );


            intent.putExtra(
                "com.termux.RUN_COMMAND_BACKGROUND",
                true
            );


            context.startService(intent);

        }

    }

}
