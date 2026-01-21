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

public class MavenUtils {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(MavenUtils.class);

    public static final DependencyVisitor DUMPER_SOUT = new DependencyGraphDumper(System.out::println);
    public static final DependencyVisitor DUMPER_LOG = new DependencyGraphDumper(log::debug);

    private MavenUtils() {
        // utility class
    }

    public static RepositorySystem getRepositorySystem() {
        return new RepositorySystemSupplier().get();
    }

    public static RepositorySystemSession.SessionBuilder getRepositorySystemSession(RepositorySystem system) {
        Path m2Repo = Paths.get(System.getProperty("user.home"), ".m2", "repository");
        return new SessionBuilderSupplier(system)
                .get()
                .withLocalRepositoryBaseDirectories(m2Repo, Paths.get("local-repo"))
                .setRepositoryListener(new ConsoleRepositoryListener())
                .setTransferListener(new ConsoleTransferListener());
    }

    public static ArtifactResult resolvePlugin(String plugin, RepositorySystem system, RepositorySystemSession.CloseableSession session) throws ArtifactResolutionException {
        return resolveArtifact(new DefaultArtifact(plugin), system, session);
    }

    public static ArtifactResult resolveArtifact(Artifact artifact, RepositorySystem system, RepositorySystemSession.CloseableSession session) throws ArtifactResolutionException {
        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(artifact);
        artifactRequest.setRepositories(getRepositories());

        return system.resolveArtifact(session, artifactRequest);
    }

    public static CollectResult collectDependencies(String plugin, RepositorySystem system, RepositorySystemSession.CloseableSession session) throws ArtifactDescriptorException, DependencyCollectionException {
        Artifact artifact = new DefaultArtifact(plugin);

        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
        descriptorRequest.setArtifact(artifact);
        descriptorRequest.setRepositories(getRepositories());
        ArtifactDescriptorResult descriptorResult = system.readArtifactDescriptor(session, descriptorRequest);

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRootArtifact(descriptorResult.getArtifact());
        collectRequest.setDependencies(descriptorResult.getDependencies());
        collectRequest.setManagedDependencies(descriptorResult.getManagedDependencies());
        collectRequest.setRepositories(descriptorRequest.getRepositories());

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
