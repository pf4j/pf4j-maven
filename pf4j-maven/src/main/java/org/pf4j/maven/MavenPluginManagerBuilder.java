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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builder for creating a configured {@link MavenPluginManager}.
 * <p>
 * This builder simplifies the setup of MavenPluginManager with custom
 * Maven repositories, local repository path, and plugin loading options.
 * <p>
 * Basic usage:
 * <pre>
 * PluginManager pluginManager = MavenPluginManagerBuilder.create()
 *     .pluginsRoot(Paths.get("my-plugins"))
 *     .build();
 * </pre>
 * <p>
 * With custom repositories:
 * <pre>
 * PluginManager pluginManager = MavenPluginManagerBuilder.create()
 *     .addRemoteRepository("jitpack", "https://jitpack.io")
 *     .addRemoteRepository("company-repo", "https://nexus.company.com/repository/maven-public/")
 *     .localRepository(Paths.get("/custom/maven/repo"))
 *     .build();
 * </pre>
 * <p>
 * Disable loose JAR processing:
 * <pre>
 * PluginManager pluginManager = MavenPluginManagerBuilder.create()
 *     .processLooseJars(false)  // only process plugins.txt
 *     .build();
 * </pre>
 *
 * @author Decebal Suiu
 */
public class MavenPluginManagerBuilder {

    private Path pluginsRoot;
    private Path localRepository;
    private Path pluginsTxtPath;
    private final List<RemoteRepository> remoteRepositories = new ArrayList<>();
    private boolean processLooseJars = true;
    private boolean skipExistingDependencies = true;
    private boolean includeMavenCentral = true;

    private MavenPluginManagerBuilder() {
    }

    /**
     * Creates a new builder instance.
     *
     * @return a new builder
     */
    public static MavenPluginManagerBuilder create() {
        return new MavenPluginManagerBuilder();
    }

    /**
     * Sets the plugins root directory.
     * <p>
     * If not set, the default location will be used (typically "./plugins").
     *
     * @param pluginsRoot the path to the plugins directory
     * @return this builder
     */
    public MavenPluginManagerBuilder pluginsRoot(Path pluginsRoot) {
        this.pluginsRoot = pluginsRoot;
        return this;
    }

    /**
     * Sets the local Maven repository path.
     * <p>
     * If not set, defaults to {@code ~/.m2/repository}.
     *
     * @param localRepository the path to local Maven repository
     * @return this builder
     */
    public MavenPluginManagerBuilder localRepository(Path localRepository) {
        this.localRepository = localRepository;
        return this;
    }

    /**
     * Sets the path to plugins.txt file.
     * <p>
     * If not set, defaults to {@code plugins.txt} in current directory.
     * Set to {@code null} to disable plugins.txt processing.
     *
     * @param pluginsTxtPath the path to plugins.txt, or null to disable
     * @return this builder
     */
    public MavenPluginManagerBuilder pluginsTxtPath(Path pluginsTxtPath) {
        this.pluginsTxtPath = pluginsTxtPath;
        return this;
    }

    /**
     * Adds a remote Maven repository.
     *
     * @param id the repository identifier
     * @param url the repository URL
     * @return this builder
     */
    public MavenPluginManagerBuilder addRemoteRepository(String id, String url) {
        Objects.requireNonNull(id, "Repository id cannot be null");
        Objects.requireNonNull(url, "Repository url cannot be null");
        remoteRepositories.add(new RemoteRepository.Builder(id, "default", url).build());
        return this;
    }

    /**
     * Adds a remote Maven repository.
     *
     * @param repository the remote repository
     * @return this builder
     */
    public MavenPluginManagerBuilder addRemoteRepository(RemoteRepository repository) {
        Objects.requireNonNull(repository, "Repository cannot be null");
        remoteRepositories.add(repository);
        return this;
    }

    /**
     * Whether to include Maven Central repository.
     * <p>
     * Default is {@code true}. Set to {@code false} if you only want
     * to use custom repositories.
     *
     * @param include true to include Maven Central
     * @return this builder
     */
    public MavenPluginManagerBuilder includeMavenCentral(boolean include) {
        this.includeMavenCentral = include;
        return this;
    }

    /**
     * Whether to process loose JAR files in the plugins directory.
     * <p>
     * When enabled (default), JAR files placed directly in the plugins
     * directory will be processed and their dependencies resolved from
     * the embedded pom.xml.
     *
     * @param process true to process loose JARs
     * @return this builder
     */
    public MavenPluginManagerBuilder processLooseJars(boolean process) {
        this.processLooseJars = process;
        return this;
    }

    /**
     * Whether to skip dependency resolution if lib/ directory already exists.
     * <p>
     * Default is {@code true}. When enabled, if a plugin's lib/ directory
     * exists and is non-empty, dependency resolution is skipped.
     *
     * @param skip true to skip existing dependencies
     * @return this builder
     */
    public MavenPluginManagerBuilder skipExistingDependencies(boolean skip) {
        this.skipExistingDependencies = skip;
        return this;
    }

    /**
     * Builds the configured {@link MavenPluginManager}.
     *
     * @return a new MavenPluginManager instance
     */
    public MavenPluginManager build() {
        return new MavenPluginManager(this);
    }

    // Package-private getters used by MavenPluginManager constructor

    Path getPluginsRoot() {
        return pluginsRoot != null ? pluginsRoot : Paths.get("plugins");
    }

    Path getLocalRepository() {
        return localRepository != null
            ? localRepository
            : Paths.get(System.getProperty("user.home"), ".m2", "repository");
    }

    List<RemoteRepository> getRemoteRepositories() {
        List<RemoteRepository> repos = new ArrayList<>();
        if (includeMavenCentral) {
            repos.add(new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build());
        }
        repos.addAll(remoteRepositories);
        return repos;
    }

    Path getPluginsTxtPath() {
        return pluginsTxtPath != null ? pluginsTxtPath : Paths.get("plugins.txt");
    }

    boolean isProcessLooseJars() {
        return processLooseJars;
    }

    boolean isSkipExistingDependencies() {
        return skipExistingDependencies;
    }

}
