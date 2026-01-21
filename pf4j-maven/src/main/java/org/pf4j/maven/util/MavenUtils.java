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

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.supplier.SessionBuilderSupplier;
import org.eclipse.aether.util.graph.visitor.DependencyGraphDumper;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility methods for Maven artifact resolution using Eclipse Aether.
 */
public class MavenUtils {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(MavenUtils.class);

    /** Dependency visitor that prints to stdout. */
    public static final DependencyVisitor DUMPER_SOUT = new DependencyGraphDumper(System.out::println);

    /** Dependency visitor that logs at debug level. */
    public static final DependencyVisitor DUMPER_LOG = new DependencyGraphDumper(log::debug);

    private MavenUtils() {
        // utility class
    }

    /**
     * Creates a new Maven repository system instance.
     */
    public static RepositorySystem getRepositorySystem() {
        return new RepositorySystemSupplier().get();
    }

    /**
     * Creates a session builder configured with the specified local repository
     * and console listeners for transfer/repository events.
     *
     * @param system the repository system
     * @param localRepository the local repository path
     * @return a configured session builder
     */
    public static RepositorySystemSession.SessionBuilder getRepositorySystemSession(RepositorySystem system, Path localRepository) {
        return new SessionBuilderSupplier(system)
                .get()
                .withLocalRepositoryBaseDirectories(localRepository)
                .setRepositoryListener(new ConsoleRepositoryListener())
                .setTransferListener(new ConsoleTransferListener());
    }

    /**
     * Resolves a plugin artifact from Maven coordinates.
     *
     * @param plugin Maven coordinates (e.g., "groupId:artifactId:version")
     * @param system the repository system
     * @param session the repository session
     * @param repositories the remote repositories to use
     * @return the resolved artifact result
     */
    public static ArtifactResult resolvePlugin(String plugin, RepositorySystem system,
            RepositorySystemSession.CloseableSession session, List<RemoteRepository> repositories)
            throws ArtifactResolutionException {
        return resolveArtifact(new DefaultArtifact(plugin), system, session, repositories);
    }

    /**
     * Resolves an artifact from local repository or remote repositories.
     *
     * @param artifact the artifact to resolve
     * @param system the repository system
     * @param session the repository session
     * @param repositories the remote repositories to use
     * @return the resolved artifact result
     */
    public static ArtifactResult resolveArtifact(Artifact artifact, RepositorySystem system,
            RepositorySystemSession.CloseableSession session, List<RemoteRepository> repositories)
            throws ArtifactResolutionException {
        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(artifact);
        artifactRequest.setRepositories(repositories);

        return system.resolveArtifact(session, artifactRequest);
    }

    /**
     * Collects transitive dependencies for a plugin artifact.
     *
     * @param plugin Maven coordinates (e.g., "groupId:artifactId:version")
     * @param system the repository system
     * @param session the repository session
     * @param repositories the remote repositories to use
     * @return dependency tree rooted at the plugin artifact
     */
    public static CollectResult collectDependencies(String plugin, RepositorySystem system,
            RepositorySystemSession.CloseableSession session, List<RemoteRepository> repositories)
            throws ArtifactDescriptorException, DependencyCollectionException {
        Artifact artifact = new DefaultArtifact(plugin);

        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
        descriptorRequest.setArtifact(artifact);
        descriptorRequest.setRepositories(repositories);
        ArtifactDescriptorResult descriptorResult = system.readArtifactDescriptor(session, descriptorRequest);

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRootArtifact(descriptorResult.getArtifact());
        collectRequest.setDependencies(descriptorResult.getDependencies());
        collectRequest.setManagedDependencies(descriptorResult.getManagedDependencies());
        collectRequest.setRepositories(repositories);

        return system.collectDependencies(session, collectRequest);
    }

    public static List<RemoteRepository> getRepositories() {
        List<RemoteRepository> repositories = new ArrayList<>();
        repositories.add(centralRepository());

        return repositories;
    }

    public static RemoteRepository centralRepository() {
        return new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build();
    }

}
