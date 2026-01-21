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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.CompoundPluginDescriptorFinder;
import org.pf4j.CompoundPluginLoader;
import org.pf4j.PluginDescriptorFinder;
import org.pf4j.PluginLoader;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

class MavenPluginManagerTest {

    @TempDir
    Path tempDir;

    private Path pluginsDir;
    private TestMavenPluginManager pluginManager;

    @BeforeEach
    void setUp() throws IOException {
        pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);

        pluginManager = new TestMavenPluginManager(pluginsDir);
    }

    @Test
    void createPluginLoader_returnsCompoundPluginLoader() {
        PluginLoader loader = pluginManager.testCreatePluginLoader();

        assertNotNull(loader);
        assertInstanceOf(CompoundPluginLoader.class, loader);
    }

    @Test
    void createPluginDescriptorFinder_returnsCompoundFinder() {
        PluginDescriptorFinder finder = pluginManager.testCreatePluginDescriptorFinder();

        assertNotNull(finder);
        assertInstanceOf(CompoundPluginDescriptorFinder.class, finder);
    }

    @Test
    void readPlugins_noPluginsTxt_returnsEmptyList() {
        List<String> plugins = pluginManager.testReadPlugins();

        assertNotNull(plugins);
        assertTrue(plugins.isEmpty());
    }

    @Test
    void readPlugins_withPluginsTxt_returnsCoordinates() throws IOException {
        Path pluginsTxt = Paths.get("plugins.txt");
        Files.write(pluginsTxt, Arrays.asList(
            "org.example:plugin-a:1.0.0",
            "org.example:plugin-b:2.0.0"
        ));

        try {
            List<String> plugins = pluginManager.testReadPlugins();

            assertEquals(2, plugins.size());
            assertEquals("org.example:plugin-a:1.0.0", plugins.get(0));
            assertEquals("org.example:plugin-b:2.0.0", plugins.get(1));
        } finally {
            Files.deleteIfExists(pluginsTxt);
        }
    }

    @Test
    void loadPlugins_emptyPluginsDir_noExceptions() {
        // Should not throw even with empty plugins directory
        assertDoesNotThrow(() -> pluginManager.loadPlugins());
    }

    @Test
    void loadPlugins_withLooseJarWithoutPom_skipsPlugin() throws IOException {
        // Create a JAR without embedded pom.xml
        createSimpleJar(pluginsDir.resolve("no-pom-plugin.jar"), "test-plugin");

        assertDoesNotThrow(() -> pluginManager.loadPlugins());

        // JAR should still be there (not processed)
        assertTrue(Files.exists(pluginsDir.resolve("no-pom-plugin.jar")));
    }

    @Test
    void loadPlugins_withLooseJarWithPomNoDeps_skipsPlugin() throws IOException {
        // Create a JAR with embedded pom.xml but no dependencies
        // MavenPluginManager skips plugins with empty dependencies
        createJarWithPom(pluginsDir.resolve("with-pom-plugin.jar"), "test-plugin",
            "org.example", "test-plugin", "1.0.0", Collections.emptyList());

        pluginManager.loadPlugins();

        // JAR should remain in plugins root (not processed because no dependencies)
        assertTrue(Files.exists(pluginsDir.resolve("with-pom-plugin.jar")));
    }

    @Test
    void loadPlugins_existingLibDir_skipsDependencyResolution() throws IOException {
        // Create plugin directory with existing lib
        Path pluginDir = pluginsDir.resolve("existing-plugin");
        Path libDir = pluginDir.resolve("lib");
        Files.createDirectories(libDir);
        Files.createFile(libDir.resolve("existing-dep.jar"));

        // Create simple JAR in plugin directory
        createSimpleJar(pluginDir.resolve("plugin.jar"), "existing-plugin");

        assertDoesNotThrow(() -> pluginManager.loadPlugins());

        // lib should still only have the existing dependency
        assertEquals(1, Files.list(libDir).count());
    }

    private void createSimpleJar(Path jarPath, String pluginId) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.putValue("Manifest-Version", "1.0");
        attrs.putValue("Plugin-Id", pluginId);
        attrs.putValue("Plugin-Version", "1.0.0");

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()), manifest)) {
            // Empty JAR
        }
    }

    private void createJarWithPom(Path jarPath, String pluginId, String groupId, String artifactId,
                                   String version, List<String> dependencies) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.putValue("Manifest-Version", "1.0");
        attrs.putValue("Plugin-Id", pluginId);
        attrs.putValue("Plugin-Version", version);

        String pomXml = createPomXml(groupId, artifactId, version, dependencies);

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()), manifest)) {
            String pomPath = "META-INF/maven/" + groupId + "/" + artifactId + "/pom.xml";
            JarEntry entry = new JarEntry(pomPath);
            jos.putNextEntry(entry);
            jos.write(pomXml.getBytes());
            jos.closeEntry();
        }
    }

    private String createPomXml(String groupId, String artifactId, String version, List<String> dependencies) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n");
        sb.append("  <modelVersion>4.0.0</modelVersion>\n");
        sb.append("  <groupId>").append(groupId).append("</groupId>\n");
        sb.append("  <artifactId>").append(artifactId).append("</artifactId>\n");
        sb.append("  <version>").append(version).append("</version>\n");

        if (!dependencies.isEmpty()) {
            sb.append("  <dependencies>\n");
            for (String dep : dependencies) {
                String[] parts = dep.split(":");
                sb.append("    <dependency>\n");
                sb.append("      <groupId>").append(parts[0]).append("</groupId>\n");
                sb.append("      <artifactId>").append(parts[1]).append("</artifactId>\n");
                sb.append("      <version>").append(parts[2]).append("</version>\n");
                sb.append("    </dependency>\n");
            }
            sb.append("  </dependencies>\n");
        }

        sb.append("</project>\n");
        return sb.toString();
    }

    /**
     * Test subclass to expose protected methods.
     */
    static class TestMavenPluginManager extends MavenPluginManager {

        private final Path pluginsRoot;

        TestMavenPluginManager(Path pluginsRoot) {
            this.pluginsRoot = pluginsRoot;
        }

        @Override
        public Path getPluginsRoot() {
            return pluginsRoot;
        }

        PluginLoader testCreatePluginLoader() {
            return createPluginLoader();
        }

        PluginDescriptorFinder testCreatePluginDescriptorFinder() {
            return createPluginDescriptorFinder();
        }

        List<String> testReadPlugins() {
            return readPlugins();
        }
    }

}
