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

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
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
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * A PF4J plugin manager that resolves plugin dependencies from Maven repositories.
 * <p>
 * Supports two mechanisms for loading plugins:
 * <ul>
 *   <li><b>Loose JAR files</b> - Place plugin JARs directly in plugins directory.
 *       Dependencies are read from embedded pom.xml (META-INF/maven/.../pom.xml)</li>
 *   <li><b>plugins.txt</b> - List Maven coordinates in plugins.txt file.
 *       Plugin and dependencies are downloaded from Maven repositories</li>
 * </ul>
 * <p>
 * Resolved dependencies are copied to {@code plugins/<plugin-id>/lib/}.
 * If lib/ already exists and is non-empty, dependency resolution is skipped.
 *
 * @see <a href="https://github.com/pf4j/pf4j/issues/208">PF4J Issue #208</a>
 */
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
        try (RepositorySystem system = MavenUtils.getRepositorySystem()) {
            try (RepositorySystemSession.CloseableSession session = MavenUtils.getRepositorySystemSession(system).build()) {
                // 1. Process loose JAR files with Maven-Dependencies in MANIFEST
                processLoosePlugins(system, session);

                // 2. Process plugins.txt (if exists)
                processPluginsTxt(system, session);
            }
        }

        super.loadPlugins();
    }

    private void processLoosePlugins(RepositorySystem system, RepositorySystemSession.CloseableSession session) {
        List<Path> looseJars = findLooseJars();
        for (Path jarPath : looseJars) {
            Model pom = readPomFromJar(jarPath);
            if (pom == null) {
                log.debug("No pom.xml in '{}', skipping", jarPath);
                continue;
            }

            List<org.apache.maven.model.Dependency> dependencies = pom.getDependencies();
            if (dependencies.isEmpty()) {
                log.debug("No dependencies in '{}', skipping", jarPath);
                continue;
            }

            log.info("Processing loose plugin '{}'", jarPath.getFileName());

            String pluginId = readPluginId(jarPath);
            if (pluginId == null) {
                // fallback to artifactId from pom
                pluginId = pom.getArtifactId();
                if (pluginId == null) {
                    String filename = jarPath.getFileName().toString();
                    pluginId = filename.substring(0, filename.length() - 4); // remove .jar
                }
            }

            // Create plugin directory and move JAR
            Path pluginDir = getPluginsRoot().resolve(pluginId);
            createPluginDirectory(pluginDir);
            movePluginJar(jarPath, pluginDir);

            // Create lib directory and resolve dependencies
            Path libPath = pluginDir.resolve("lib");
            if (hasExistingDependencies(libPath)) {
                log.info("lib/ exists and non-empty for '{}', skipping dependency resolution", pluginId);
                continue;
            }

            createPluginLibDirectory(libPath);
            resolvePomDependencies(dependencies, system, session, libPath);
        }
    }

    private void processPluginsTxt(RepositorySystem system, RepositorySystemSession.CloseableSession session) {
        List<String> plugins = readPlugins();
        for (String plugin : plugins) {
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
        }
    }

    /**
     * Reads plugin Maven coordinates from plugins.txt file.
     * Override to customize plugin source (e.g., read from database or remote config).
     *
     * @return list of Maven coordinates (groupId:artifactId:version or full GAV)
     */
    protected List<String> readPlugins() {
        Path pluginsTxt = Paths.get("plugins.txt");
        if (!Files.exists(pluginsTxt)) {
            log.debug("plugins.txt not found, skipping");
            return Collections.emptyList();
        }
        try {
            return FileUtils.readLines(pluginsTxt, true);
        } catch (IOException e) {
            throw new PluginRuntimeException("Cannot read plugins.txt", e);
        }
    }

    private List<Path> findLooseJars() {
        List<Path> jars = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(getPluginsRoot(), "*.jar")) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    jars.add(path);
                }
            }
        } catch (IOException e) {
            log.warn("Cannot scan plugins directory for JAR files", e);
        }
        return jars;
    }

    private Model readPomFromJar(Path jarPath) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            // Find pom.xml in META-INF/maven/
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().startsWith("META-INF/maven/") && entry.getName().endsWith("/pom.xml")) {
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        MavenXpp3Reader reader = new MavenXpp3Reader();
                        return reader.read(is);
                    }
                }
            }
        } catch (IOException | XmlPullParserException e) {
            log.warn("Cannot read pom.xml from '{}'", jarPath, e);
        }
        return null;
    }

    private String readPluginId(Path jarPath) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) {
                return null;
            }
            return manifest.getMainAttributes().getValue("Plugin-Id");
        } catch (IOException e) {
            log.warn("Cannot read MANIFEST from '{}'", jarPath, e);
            return null;
        }
    }

    private void movePluginJar(Path jarPath, Path pluginDir) {
        Path targetPath = pluginDir.resolve(jarPath.getFileName());
        if (Files.exists(targetPath)) {
            log.debug("Plugin JAR already exists at '{}'", targetPath);
            return;
        }
        try {
            Files.move(jarPath, targetPath);
            log.info("Plugin JAR moved to '{}'", targetPath);
        } catch (IOException e) {
            throw new PluginRuntimeException("Cannot move plugin JAR", e);
        }
    }

    private boolean hasExistingDependencies(Path libPath) {
        if (!Files.exists(libPath)) {
            return false;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(libPath)) {
            return stream.iterator().hasNext();
        } catch (IOException e) {
            return false;
        }
    }

    private void resolvePomDependencies(List<org.apache.maven.model.Dependency> dependencies,
            RepositorySystem system, RepositorySystemSession.CloseableSession session, Path libPath) {
        for (org.apache.maven.model.Dependency dep : dependencies) {
            String scope = dep.getScope();
            if ("provided".equals(scope) || "test".equals(scope)) {
                continue;
            }

            String coord = dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion();
            try {
                Artifact artifact = new org.eclipse.aether.artifact.DefaultArtifact(coord);
                ArtifactResult result = MavenUtils.resolveArtifact(artifact, system, session);
                Artifact resolved = result.getArtifact();
                log.info("Dependency '{}' resolved to '{}'", coord, resolved.getPath());
                copyPluginDependency(libPath, resolved);
            } catch (ArtifactResolutionException e) {
                log.error("Cannot resolve dependency '{}'", coord, e);
            }
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
        Path artifactPath = artifact.getPath();
        Path targetPath = pluginPath.resolve(artifactPath.getFileName());
        if (Files.exists(targetPath)) {
            log.debug("Plugin artifact already exists at '{}'", targetPath);
            return;
        }
        try {
            Files.copy(artifactPath, targetPath);
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
        Path targetPath = libPath.resolve(depArtifactPath.getFileName());
        if (Files.exists(targetPath)) {
            log.debug("Dependency '{}' already exists at '{}'", depArtifact, targetPath);
            return;
        }
        try {
            Files.copy(depArtifactPath, targetPath);
            log.info("Dependency '{}' copied to '{}'", depArtifact, targetPath);
        } catch (IOException e) {
            throw new PluginRuntimeException("Cannot copy plugin dependency artifact", e);
        }
    }

}
