/*
 * Copyright (C) 2026-present the original author or authors.
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
package org.pf4j.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.PluginRuntimeException;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

class MavenPropertiesPluginDescriptorFinderTest {

    @TempDir
    Path tempDir;

    @Test
    void isApplicable_withJarAndProperties_returnsTrue() throws IOException {
        Path pluginDir = tempDir.resolve("plugin-with-props");
        Files.createDirectories(pluginDir);

        createJarWithProperties(pluginDir.resolve("plugin.jar"),
            "test-plugin", "1.0.0", "org.example.TestPlugin");

        MavenPropertiesPluginDescriptorFinder finder = new MavenPropertiesPluginDescriptorFinder();

        assertTrue(finder.isApplicable(pluginDir));
    }

    @Test
    void isApplicable_directoryExists_returnsTrue() throws IOException {
        // PropertiesPluginDescriptorFinder.isApplicable checks if path is directory
        Path pluginDir = tempDir.resolve("no-jar");
        Files.createDirectories(pluginDir);

        MavenPropertiesPluginDescriptorFinder finder = new MavenPropertiesPluginDescriptorFinder();

        assertTrue(finder.isApplicable(pluginDir));
    }

    @Test
    void find_noJar_throwsException() {
        Path emptyDir = tempDir.resolve("empty");

        MavenPropertiesPluginDescriptorFinder finder = new MavenPropertiesPluginDescriptorFinder();

        assertThrows(PluginRuntimeException.class, () -> finder.find(emptyDir));
    }

    private void createJarWithProperties(Path jarPath, String pluginId, String version, String pluginClass)
            throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");

        String properties = "plugin.id=" + pluginId + "\n" +
                           "plugin.version=" + version + "\n" +
                           "plugin.class=" + pluginClass + "\n";

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()), manifest)) {
            JarEntry entry = new JarEntry("plugin.properties");
            jos.putNextEntry(entry);
            jos.write(properties.getBytes());
            jos.closeEntry();
        }
    }

}
