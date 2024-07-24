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

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class MavenUtils {

    public static final DependencyVisitor DUMPER_SOUT = new DependencyGraphDumper(System.out::println);

    private MavenUtils() {
        // utility class
    }

    public static RepositorySystem getRepositorySystem() {
        return new RepositorySystemSupplier().get();
    }

    public static RepositorySystemSession.SessionBuilder getRepositorySystemSession(RepositorySystem system) {
        return new SessionBuilderSupplier(system)
                .get()
                .withLocalRepositoryBaseDirectories(Paths.get("local-repo"))
                .setRepositoryListener(new ConsoleRepositoryListener())
                .setTransferListener(new ConsoleTransferListener());
    }

    public static ArtifactResult resolvePlugin(String plugin, RepositorySystem system, RepositorySystemSession.CloseableSession session) throws ArtifactResolutionException {
        Artifact artifact = new DefaultArtifact(plugin);

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
        repositories.add(localRepository());
//        repositories.add(centralRepository());

        return repositories;
    }

    public static RemoteRepository localRepository() {
        String userHome = System.getProperty("user.home");
        String mavenRepoPath = "file://" + userHome + "/.m2/repository";

        return new RemoteRepository.Builder("local", "default", mavenRepoPath).build();
    }

    public static RemoteRepository centralRepository() {
        return new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build();
    }

}
