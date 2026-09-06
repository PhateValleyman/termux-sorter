package com.umbrellacorp.termuxsorter;

import java.io.File;

public class FileSorter {
    public static File move(File source, String destination) {
        File dir = new File(destination);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("Cannot create directory: " + dir);
        }

        File target = new File(dir, source.getName());
        int i = 1;
        while (target.exists()) {
            target = new File(dir, i + "_" + source.getName());
            i++;
        }

        if (!source.renameTo(target)) {
            throw new RuntimeException("Move failed: " + source);
        }
        return target;
    }
}
