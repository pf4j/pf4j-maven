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
package org.pf4j.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginRuntimeException;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

class MavenManifestPluginDescriptorFinderTest {

    @TempDir
    Path tempDir;

    @Test
    void find_withValidManifest_returnsDescriptor() throws IOException {
        // Create plugin directory with JAR containing manifest
        Path pluginDir = tempDir.resolve("test-plugin");
        Files.createDirectories(pluginDir);

        createPluginJar(pluginDir.resolve("test-plugin.jar"),
            "test-plugin", "1.0.0", "org.example.TestPlugin");

        MavenManifestPluginDescriptorFinder finder = new MavenManifestPluginDescriptorFinder();

        assertTrue(finder.isApplicable(pluginDir));

        PluginDescriptor descriptor = finder.find(pluginDir);

        assertNotNull(descriptor);
        assertEquals("test-plugin", descriptor.getPluginId());
        assertEquals("1.0.0", descriptor.getVersion());
        assertEquals("org.example.TestPlugin", descriptor.getPluginClass());
    }

    @Test
    void find_noJar_throwsException() {
        Path emptyDir = tempDir.resolve("empty");

        MavenManifestPluginDescriptorFinder finder = new MavenManifestPluginDescriptorFinder();

        assertThrows(PluginRuntimeException.class, () -> finder.find(emptyDir));
    }

    @Test
    void isApplicable_withJar_returnsTrue() throws IOException {
        Path pluginDir = tempDir.resolve("plugin-with-jar");
        Files.createDirectories(pluginDir);
        createPluginJar(pluginDir.resolve("plugin.jar"), "test", "1.0", "Test");

        MavenManifestPluginDescriptorFinder finder = new MavenManifestPluginDescriptorFinder();

        assertTrue(finder.isApplicable(pluginDir));
    }

    @Test
    void isApplicable_directoryExists_returnsTrue() throws IOException {
        // ManifestPluginDescriptorFinder.isApplicable checks if path is directory
        Path pluginDir = tempDir.resolve("no-jar");
        Files.createDirectories(pluginDir);

        MavenManifestPluginDescriptorFinder finder = new MavenManifestPluginDescriptorFinder();

        assertTrue(finder.isApplicable(pluginDir));
    }

    private void createPluginJar(Path jarPath, String pluginId, String version, String pluginClass)
            throws IOException {
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.putValue("Manifest-Version", "1.0");
        attrs.putValue("Plugin-Id", pluginId);
        attrs.putValue("Plugin-Version", version);
        attrs.putValue("Plugin-Class", pluginClass);

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()), manifest)) {
            // Empty JAR with manifest
        }
    }

}
