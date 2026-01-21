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
package org.pf4j.maven.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.PluginRuntimeException;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

class PluginUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void getPluginJarPath_returnsFirstJar() throws IOException {
        // Create a JAR file in temp directory
        Path jarPath = tempDir.resolve("test-plugin.jar");
        createEmptyJar(jarPath);

        Path result = PluginUtils.getPluginJarPath(tempDir);

        assertNotNull(result);
        assertEquals("test-plugin.jar", result.getFileName().toString());
    }

    @Test
    void getPluginJarPath_withMultipleJars_returnsFirst() throws IOException {
        // Create multiple JAR files
        createEmptyJar(tempDir.resolve("alpha-plugin.jar"));
        createEmptyJar(tempDir.resolve("beta-plugin.jar"));

        Path result = PluginUtils.getPluginJarPath(tempDir);

        assertNotNull(result);
        assertTrue(result.getFileName().toString().endsWith(".jar"));
    }

    @Test
    void getPluginJarPath_noJars_throwsException() {
        // Empty directory - no JARs

        PluginRuntimeException exception = assertThrows(
            PluginRuntimeException.class,
            () -> PluginUtils.getPluginJarPath(tempDir)
        );

        assertTrue(exception.getMessage().contains("Cannot find JAR file"));
    }

    @Test
    void getPluginJarPath_onlyNonJarFiles_throwsException() throws IOException {
        // Create non-JAR files
        Files.createFile(tempDir.resolve("readme.txt"));
        Files.createFile(tempDir.resolve("config.xml"));

        PluginRuntimeException exception = assertThrows(
            PluginRuntimeException.class,
            () -> PluginUtils.getPluginJarPath(tempDir)
        );

        assertTrue(exception.getMessage().contains("Cannot find JAR file"));
    }

    private void createEmptyJar(Path jarPath) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()), manifest)) {
            // Empty JAR with just manifest
        }
    }

}
