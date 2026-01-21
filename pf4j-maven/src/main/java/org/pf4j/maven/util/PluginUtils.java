/*
 * Copyright (C) 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pf4j.maven.util;

import org.pf4j.PluginRuntimeException;
import org.pf4j.util.JarFileFilter;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Path;

/**
 * Utility methods for plugin file operations.
 */
public class PluginUtils {

    private PluginUtils() {
        // utility class
    }

    /**
     * Finds the first JAR file in the plugin directory.
     *
     * @param pluginPath plugin directory path
     * @return path to the plugin JAR
     * @throws PluginRuntimeException if no JAR found
     */
    public static Path getPluginJarPath(Path pluginPath) {
        FileFilter jarFilter = new JarFileFilter();
        File[] files = pluginPath.toFile().listFiles(jarFilter);
        if (files == null || files.length == 0) {
            throw new PluginRuntimeException("Cannot find JAR file in plugin path " + pluginPath);
        }

        return files[0].toPath();
    }

}
