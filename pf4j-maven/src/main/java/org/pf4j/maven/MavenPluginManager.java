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
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.pf4j.CompoundPluginDescriptorFinder;
import org.pf4j.CompoundPluginLoader;
import org.pf4j.DefaultPluginLoader;
import org.pf4j.DefaultPluginManager;
import org.pf4j.DevelopmentPluginLoader;
import org.pf4j.JarPluginLoader;
import org.pf4j.ManifestPluginDescriptorFinder;
import org.pf4j.PluginDescriptorFinder;
import org.pf4j.PluginLoader;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PropertiesPluginDescriptorFinder;
import org.pf4j.ZipPluginManager;
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
        List<String> plugins;
        try {
            plugins = FileUtils.readLines(Paths.get("plugins.txt"), true);
        } catch (IOException e) {
            throw new PluginRuntimeException("Cannot read plugins.txt", e);
        }

        try (RepositorySystem system = MavenUtils.getRepositorySystem()) {
            try (RepositorySystemSession.CloseableSession session = MavenUtils.getRepositorySystemSession(system).build()) {
                plugins.forEach(plugin -> {
                    log.info("Resolving plugin '{}'", plugin);

                    try {
                        ArtifactResult result = MavenUtils.resolvePlugin(plugin, system, session);
                        Artifact artifact = result.getArtifact();
                        log.info("'{}' resolved to  '{}'", artifact, artifact.getPath());

                        CollectResult collectResult = MavenUtils.collectDependencies(plugin, system, session);
                        collectResult.getRoot().accept(MavenUtils.DUMPER_SOUT);

                        // create plugin directory and copy the artifact
                        Path pluginPath = getPluginsRoot().resolve(artifact.getArtifactId());
                        try {
                            Files.createDirectories(pluginPath);
                            log.info("Plugin directory created '{}'", pluginPath);
                        } catch (IOException e) {
                            throw new PluginRuntimeException("Cannot create plugin directory", e);
                        }

                        try {
                            Path artifactPath = artifact.getPath();
                            Files.copy(artifactPath, pluginPath.resolve(artifactPath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                            log.info("Plugin artifact copied to '{}'", pluginPath);
                        } catch (IOException e) {
                            throw new PluginRuntimeException("Cannot copy plugin artifact", e);
                        }

                        // create 'lib' directory
                        Path libPath = pluginPath.resolve("lib");
                        try {
                            Files.createDirectories(libPath);
                            log.info("Plugin 'lib' directory created '{}'", libPath);
                        } catch (IOException e) {
                            throw new PluginRuntimeException("Cannot create plugin 'lib' directory", e);
                        }

                        // copy dependencies to plugin 'lib' directory
                        collectResult.getRoot().getChildren().forEach(node -> {
                            Dependency dependency = node.getDependency();
                            if (dependency.getScope().equals("provided")) {
                                return;
                            }

                            Artifact depArtifact = dependency.getArtifact();

                            // resolve dependency artifact
                            try {
                                ArtifactResult depResult = MavenUtils.resolvePlugin(depArtifact.toString(), system, session);
                                depArtifact = depResult.getArtifact();
                                log.info("Dependency '{}' resolved to  '{}'", depArtifact, depArtifact.getPath());
                            } catch (ArtifactResolutionException e) {
                                log.error(e.getMessage(), e);
                                return;
                            }

                            Path depArtifactPath = depArtifact.getPath();
                            Path depPluginPath = libPath.resolve(depArtifactPath.getFileName());
                            try {
                                Files.copy(depArtifactPath, depPluginPath, StandardCopyOption.REPLACE_EXISTING);
                                log.info("Dependency '{}' copied to '{}'", depArtifact, depPluginPath);
                            } catch (IOException e) {
                                throw new PluginRuntimeException("Cannot copy plugin dependency artifact", e);
                            }
                        });
                    } catch (ArtifactResolutionException | ArtifactDescriptorException | DependencyCollectionException e) {
                        log.error(e.getMessage(), e);
                    }
                });
            }
        }

        super.loadPlugins();
    }

}
