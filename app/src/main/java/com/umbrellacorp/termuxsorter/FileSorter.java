package com.umbrellacorp.termuxsorter;

import java.io.File;

public class FileSorter {


    public static File move(
        File source,
        String destination
    ) {


        File dir =
            new File(destination);


        dir.mkdirs();


        File target =
            new File(
                dir,
                source.getName()
            );


        source.renameTo(target);


        return target;

    }

}
