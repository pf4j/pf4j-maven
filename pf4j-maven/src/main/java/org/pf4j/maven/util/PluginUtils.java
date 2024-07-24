package org.pf4j.maven.util;

import org.pf4j.PluginRuntimeException;
import org.pf4j.util.JarFileFilter;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Path;

public class PluginUtils {

    private PluginUtils() {
        // utility class
    }

    public static Path getPluginJarPath(Path pluginPath) {
        // get jar file from plugin path
        FileFilter jarFilter = new JarFileFilter();
        File[] files = pluginPath.toFile().listFiles(jarFilter);
        if (files == null || files.length == 0) {
            throw new PluginRuntimeException("Cannot find JAR file in plugin path " + pluginPath);
        }

        return files[0].toPath();
    }

}
