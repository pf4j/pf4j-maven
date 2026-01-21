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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for plugin dependency resolution scenarios.
 *
 * Investigates how MavenPluginManager handles:
 * 1. Plugin A with Maven dependency on library X -> X goes to lib/
 * 2. Plugin A with Maven dependency on Plugin B (another PF4J plugin) -> where does B go?
 * 3. Plugin A with Plugin-Dependencies: plugin-b in MANIFEST -> PF4J standard behavior
 */
class PluginDependencyResolutionTest {

    @TempDir
    Path tempDir;

    private Path pluginsDir;

    @BeforeEach
    void setUp() throws IOException {
        pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
    }

    /**
     * Scenario: Plugin A depends on a regular library (not a plugin).
     * Expected: Library goes to plugins/plugin-a/lib/
     */
    @Test
    void scenario1_pluginDependsOnLibrary() throws IOException {
        // This is the current behavior - libraries go to lib/
        // Just documenting the expected behavior

        // Given: Plugin A with pom.xml declaring dependency on commons-lang
        Path pluginAJar = createPluginJarWithPom(
            pluginsDir.resolve("plugin-a.jar"),
            "plugin-a", "1.0.0",
            "org.example", "plugin-a", "1.0.0",
            "    <dependency>\n" +
            "      <groupId>commons-lang</groupId>\n" +
            "      <artifactId>commons-lang</artifactId>\n" +
            "      <version>2.6</version>\n" +
            "    </dependency>\n"
        );

        assertTrue(Files.exists(pluginAJar));
        // When MavenPluginManager.loadPlugins() is called:
        // - plugin-a.jar moves to plugins/plugin-a/plugin-a.jar
        // - commons-lang-2.6.jar is downloaded to plugins/plugin-a/lib/
    }

    /**
     * Scenario: Plugin A has Maven dependency on Plugin B (which is also a PF4J plugin).
     * Current behavior: Plugin B goes to plugins/plugin-a/lib/ (treated as library)
     * Expected behavior: Plugin B should be installed as plugins/plugin-b/ (separate plugin)
     *
     * This test documents the current problematic behavior.
     */
    @Test
    @Disabled("Documents current behavior - Plugin B ends up in lib/ instead of as separate plugin")
    void scenario2_pluginDependsOnAnotherPlugin_currentBehavior() throws IOException {
        // Given: Plugin A with Maven dependency on Plugin B
        // Plugin B is also a PF4J plugin (has Plugin-Id in MANIFEST)

        // Current behavior:
        // - Plugin B JAR goes to plugins/plugin-a/lib/plugin-b.jar
        // - Plugin B is NOT loaded as a plugin (no lifecycle, no extension discovery)
        // - Plugin B's extensions are NOT available

        // This is problematic when:
        // 1. Plugin B has its own extensions that should be discovered
        // 2. Plugin B should have its own lifecycle (start/stop)
        // 3. Multiple plugins depend on B - each gets a copy in their lib/
    }

    /**
     * Scenario: Plugin A declares Plugin-Dependencies: plugin-b in MANIFEST.
     * This is standard PF4J behavior - Plugin B must be present as separate plugin.
     */
    @Test
    @Disabled("Requires both plugins to be installed separately")
    void scenario3_pluginDeclaresPluginDependency_standardPf4j() throws IOException {
        // Given: Plugin A with Plugin-Dependencies: plugin-b in MANIFEST
        Path pluginAJar = createPluginJarWithDependency(
            pluginsDir.resolve("plugin-a").resolve("plugin-a.jar"),
            "plugin-a", "1.0.0", "plugin-b"
        );

        // And: Plugin B installed separately
        Path pluginBJar = createSimplePluginJar(
            pluginsDir.resolve("plugin-b").resolve("plugin-b.jar"),
            "plugin-b", "1.0.0"
        );

        // When: PluginManager loads plugins
        // Then: Both plugins are loaded, A can use classes from B
        // This is standard PF4J behavior
    }

    /**
     * Scenario: What we WANT to achieve with MavenPluginManager.
     * Plugin A has Maven dependency on Plugin B.
     * MavenPluginManager should:
     * 1. Detect that Plugin B is a PF4J plugin (has Plugin-Id)
     * 2. Install Plugin B as plugins/plugin-b/ (not in lib/)
     *
     * NOTE: This behavior is now implemented! When resolving dependencies,
     * MavenPluginManager checks if the JAR has Plugin-Id in MANIFEST.
     * If yes, it's installed as a separate plugin instead of going to lib/.
     */
    @Test
    @Disabled("Requires actual Maven resolution - use integration tests")
    void scenario4_desiredBehavior_detectPluginDependencies() throws IOException {
        // This behavior is now implemented in MavenPluginManager.processDependencyNode()
        // The method readPluginIdFromJar() checks if a dependency JAR is a PF4J plugin
        // If yes, installPluginDependency() installs it as a separate plugin

        // Given: Plugin A in plugins.txt with Maven dependency on Plugin B
        // And: Plugin B artifact has Plugin-Id in MANIFEST (is a PF4J plugin)

        // When: MavenPluginManager.loadPlugins()

        // Then:
        // - Plugin B is detected as a plugin (not just a library)
        // - Plugin B is installed at plugins/plugin-b/
        // - Plugin A is installed at plugins/plugin-a/
        // - Both are loaded as separate plugins
        // - Plugin A's classloader can access Plugin B's classes
    }

    // Helper methods

    private Path createSimplePluginJar(Path jarPath, String pluginId, String version) throws IOException {
        Files.createDirectories(jarPath.getParent());

        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.putValue("Manifest-Version", "1.0");
        attrs.putValue("Plugin-Id", pluginId);
        attrs.putValue("Plugin-Version", version);

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()), manifest)) {
            // Empty plugin JAR
        }
        return jarPath;
    }

    private Path createPluginJarWithDependency(Path jarPath, String pluginId, String version,
            String dependsOn) throws IOException {
        Files.createDirectories(jarPath.getParent());

        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.putValue("Manifest-Version", "1.0");
        attrs.putValue("Plugin-Id", pluginId);
        attrs.putValue("Plugin-Version", version);
        attrs.putValue("Plugin-Dependencies", dependsOn);

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()), manifest)) {
            // Empty plugin JAR
        }
        return jarPath;
    }

    private Path createPluginJarWithPom(Path jarPath, String pluginId, String version,
            String groupId, String artifactId, String pomVersion,
            String dependencies) throws IOException {

        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.putValue("Manifest-Version", "1.0");
        attrs.putValue("Plugin-Id", pluginId);
        attrs.putValue("Plugin-Version", version);

        String pomXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n" +
            "  <modelVersion>4.0.0</modelVersion>\n" +
            "  <groupId>" + groupId + "</groupId>\n" +
            "  <artifactId>" + artifactId + "</artifactId>\n" +
            "  <version>" + pomVersion + "</version>\n" +
            "  <dependencies>\n" +
            dependencies +
            "  </dependencies>\n" +
            "</project>\n";

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()), manifest)) {
            String pomPath = "META-INF/maven/" + groupId + "/" + artifactId + "/pom.xml";
            JarEntry entry = new JarEntry(pomPath);
            jos.putNextEntry(entry);
            jos.write(pomXml.getBytes());
            jos.closeEntry();
        }
        return jarPath;
    }

}
