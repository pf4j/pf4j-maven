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

import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MavenPluginManagerBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void create_returnsNewBuilder() {
        MavenPluginManagerBuilder builder = MavenPluginManagerBuilder.create();
        assertNotNull(builder);
    }

    @Test
    void build_withDefaults_createsMavenPluginManager() {
        MavenPluginManager manager = MavenPluginManagerBuilder.create().build();
        assertNotNull(manager);
    }

    @Test
    void build_withCustomPluginsRoot_usesCustomPath() {
        Path customPath = tempDir.resolve("custom-plugins");

        MavenPluginManager manager = MavenPluginManagerBuilder.create()
            .pluginsRoot(customPath)
            .build();

        assertEquals(customPath, manager.getPluginsRoot());
    }

    @Test
    void build_withDefaultPluginsRoot_usesPluginsDirectory() {
        MavenPluginManager manager = MavenPluginManagerBuilder.create().build();

        assertEquals(Paths.get("plugins"), manager.getPluginsRoot());
    }

    @Test
    void getLocalRepository_withDefault_returnsM2Repository() {
        MavenPluginManagerBuilder builder = MavenPluginManagerBuilder.create();

        Path expected = Paths.get(System.getProperty("user.home"), ".m2", "repository");
        assertEquals(expected, builder.getLocalRepository());
    }

    @Test
    void getLocalRepository_withCustom_returnsCustomPath() {
        Path customRepo = tempDir.resolve("custom-repo");

        MavenPluginManagerBuilder builder = MavenPluginManagerBuilder.create()
            .localRepository(customRepo);

        assertEquals(customRepo, builder.getLocalRepository());
    }

    @Test
    void getRemoteRepositories_withDefault_includesMavenCentral() {
        MavenPluginManagerBuilder builder = MavenPluginManagerBuilder.create();

        List<RemoteRepository> repos = builder.getRemoteRepositories();

        assertEquals(1, repos.size());
        assertEquals("central", repos.get(0).getId());
        assertTrue(repos.get(0).getUrl().contains("maven.apache.org"));
    }

    @Test
    void getRemoteRepositories_withAdditionalRepo_includesBoth() {
        MavenPluginManagerBuilder builder = MavenPluginManagerBuilder.create()
            .addRemoteRepository("jitpack", "https://jitpack.io");

        List<RemoteRepository> repos = builder.getRemoteRepositories();

        assertEquals(2, repos.size());
        assertEquals("central", repos.get(0).getId());
        assertEquals("jitpack", repos.get(1).getId());
    }

    @Test
    void getRemoteRepositories_excludingMavenCentral_returnsOnlyCustom() {
        MavenPluginManagerBuilder builder = MavenPluginManagerBuilder.create()
            .includeMavenCentral(false)
            .addRemoteRepository("custom", "https://custom.repo.com");

        List<RemoteRepository> repos = builder.getRemoteRepositories();

        assertEquals(1, repos.size());
        assertEquals("custom", repos.get(0).getId());
    }

    @Test
    void getPluginsTxtPath_withDefault_returnsPluginsTxt() {
        MavenPluginManagerBuilder builder = MavenPluginManagerBuilder.create();

        assertEquals(Paths.get("plugins.txt"), builder.getPluginsTxtPath());
    }

    @Test
    void getPluginsTxtPath_withCustom_returnsCustomPath() {
        Path customPath = tempDir.resolve("custom-plugins.txt");

        MavenPluginManagerBuilder builder = MavenPluginManagerBuilder.create()
            .pluginsTxtPath(customPath);

        assertEquals(customPath, builder.getPluginsTxtPath());
    }

    @Test
    void isProcessLooseJars_default_returnsTrue() {
        MavenPluginManagerBuilder builder = MavenPluginManagerBuilder.create();
        assertTrue(builder.isProcessLooseJars());
    }

    @Test
    void isProcessLooseJars_whenDisabled_returnsFalse() {
        MavenPluginManagerBuilder builder = MavenPluginManagerBuilder.create()
            .processLooseJars(false);
        assertFalse(builder.isProcessLooseJars());
    }

    @Test
    void isSkipExistingDependencies_default_returnsTrue() {
        MavenPluginManagerBuilder builder = MavenPluginManagerBuilder.create();
        assertTrue(builder.isSkipExistingDependencies());
    }

    @Test
    void isSkipExistingDependencies_whenDisabled_returnsFalse() {
        MavenPluginManagerBuilder builder = MavenPluginManagerBuilder.create()
            .skipExistingDependencies(false);
        assertFalse(builder.isSkipExistingDependencies());
    }

    @Test
    void addRemoteRepository_withRemoteRepositoryObject_addsIt() {
        RemoteRepository repo = new RemoteRepository.Builder("test", "default", "https://test.com").build();

        MavenPluginManagerBuilder builder = MavenPluginManagerBuilder.create()
            .includeMavenCentral(false)
            .addRemoteRepository(repo);

        List<RemoteRepository> repos = builder.getRemoteRepositories();
        assertEquals(1, repos.size());
        assertEquals("test", repos.get(0).getId());
    }

    @Test
    void addRemoteRepository_withNullId_throwsException() {
        MavenPluginManagerBuilder builder = MavenPluginManagerBuilder.create();

        assertThrows(NullPointerException.class,
            () -> builder.addRemoteRepository(null, "https://test.com"));
    }

    @Test
    void addRemoteRepository_withNullUrl_throwsException() {
        MavenPluginManagerBuilder builder = MavenPluginManagerBuilder.create();

        assertThrows(NullPointerException.class,
            () -> builder.addRemoteRepository("test", null));
    }

    @Test
    void fluentApi_chainsCorrectly() {
        MavenPluginManager manager = MavenPluginManagerBuilder.create()
            .pluginsRoot(tempDir)
            .localRepository(tempDir.resolve("repo"))
            .pluginsTxtPath(tempDir.resolve("plugins.txt"))
            .addRemoteRepository("test", "https://test.com")
            .includeMavenCentral(true)
            .processLooseJars(false)
            .skipExistingDependencies(false)
            .build();

        assertNotNull(manager);
        assertEquals(tempDir, manager.getPluginsRoot());
    }

}
