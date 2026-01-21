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

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.pf4j.CompoundPluginDescriptorFinder;
import org.pf4j.CompoundPluginLoader;
import org.pf4j.DefaultPluginManager;
import org.pf4j.DevelopmentPluginLoader;
import org.pf4j.PluginDescriptorFinder;
import org.pf4j.PluginLoader;
import org.pf4j.PluginRuntimeException;
import org.pf4j.maven.util.MavenUtils;
import org.pf4j.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class MavenPluginManager extends DefaultPluginManager {

    private static final Logger log = LoggerFactory.getLogger(MavenPluginManager.class);

    @Override
    protected PluginLoader createPluginLoader() {
        return new CompoundPluginLoader()
                .add(new DevelopmentPluginLoader(this), this::isDevelopment)
                .add(new MavenPluginLoader(this), this::isNotDevelopment);
    }

    @Override
    protected PluginDescriptorFinder createPluginDescriptorFinder() {
        return new CompoundPluginDescriptorFinder()
                .add(new MavenPropertiesPluginDescriptorFinder())
                .add(new MavenManifestPluginDescriptorFinder());
    }

    @Override
    public void loadPlugins() {
        List<String> plugins = readPlugins();

        try (RepositorySystem system = MavenUtils.getRepositorySystem()) {
            try (RepositorySystemSession.CloseableSession session = MavenUtils.getRepositorySystemSession(system).build()) {
                plugins.forEach(plugin -> {
                    log.info("Resolving plugin '{}'", plugin);

                    try {
                        ArtifactResult result = MavenUtils.resolvePlugin(plugin, system, session);
                        Artifact artifact = result.getArtifact();
                        log.info("'{}' resolved to  '{}'", artifact, artifact.getPath());

                        CollectResult collectResult = MavenUtils.collectDependencies(plugin, system, session);
                        collectResult.getRoot().accept(MavenUtils.DUMPER_LOG);

                        // create plugin directory and copy the plugin artifact
                        Path pluginPath = getPluginsRoot().resolve(artifact.getArtifactId());
                        createPluginDirectory(pluginPath);
                        copyPluginArtifact(artifact, pluginPath);

                        // create 'lib' directory
                        Path libPath = pluginPath.resolve("lib");
                        createPluginLibDirectory(libPath);

                        // copy dependencies to plugin 'lib' directory
                        copyDependencies(collectResult, system, session, libPath);
                    } catch (ArtifactResolutionException | ArtifactDescriptorException | DependencyCollectionException e) {
                        log.error("Cannot resolve plugin '{}'", plugin, e);
                    }
                });
            }
        }

        super.loadPlugins();
    }

    protected List<String> readPlugins() {
        try {
            return FileUtils.readLines(Paths.get("plugins.txt"), true);
        } catch (IOException e) {
            throw new PluginRuntimeException("Cannot read plugins.txt", e);
        }
    }

    private static void createPluginDirectory(Path pluginPath) {
        try {
            Files.createDirectories(pluginPath);
            log.info("Plugin directory created '{}'", pluginPath);
        } catch (IOException e) {
            throw new PluginRuntimeException("Cannot create plugin directory", e);
        }
    }

    private static void createPluginLibDirectory(Path libPath) {
        try {
            Files.createDirectories(libPath);
            log.info("Plugin 'lib' directory created '{}'", libPath);
        } catch (IOException e) {
            throw new PluginRuntimeException("Cannot create plugin 'lib' directory", e);
        }
    }

    private static void copyPluginArtifact(Artifact artifact, Path pluginPath) {
        try {
            Path artifactPath = artifact.getPath();
            Files.copy(artifactPath, pluginPath.resolve(artifactPath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            log.info("Plugin artifact copied to '{}'", pluginPath);
        } catch (IOException e) {
            throw new PluginRuntimeException("Cannot copy plugin artifact", e);
        }
    }

    private static void copyDependencies(CollectResult collectResult, RepositorySystem system, RepositorySystemSession.CloseableSession session, Path libPath) {
        processDependencyNode(collectResult.getRoot(), system, session, libPath);
    }

    private static void processDependencyNode(DependencyNode node, RepositorySystem system, RepositorySystemSession.CloseableSession session, Path libPath) {
        for (DependencyNode child : node.getChildren()) {
            Dependency dependency = child.getDependency();
            if ("provided".equals(dependency.getScope()) || "test".equals(dependency.getScope())) {
                continue;
            }

            Artifact depArtifact = dependency.getArtifact();

            // resolve dependency artifact
            try {
                ArtifactResult depResult = MavenUtils.resolveArtifact(depArtifact, system, session);
                depArtifact = depResult.getArtifact();
                log.info("Dependency '{}' resolved to  '{}'", depArtifact, depArtifact.getPath());
            } catch (ArtifactResolutionException e) {
                log.error(e.getMessage(), e);
                continue;
            }

            copyPluginDependency(libPath, depArtifact);

            // process transitive dependencies recursively
            processDependencyNode(child, system, session, libPath);
        }
    }

    private static void copyPluginDependency(Path libPath, Artifact depArtifact) {
        Path depArtifactPath = depArtifact.getPath();
        Path depPluginPath = libPath.resolve(depArtifactPath.getFileName());
        try {
            Files.copy(depArtifactPath, depPluginPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Dependency '{}' copied to '{}'", depArtifact, depPluginPath);
        } catch (IOException e) {
            throw new PluginRuntimeException("Cannot copy plugin dependency artifact", e);
        }
    }

}
