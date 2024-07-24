package org.pf4j.maven;

import org.pf4j.ManifestPluginDescriptorFinder;
import org.pf4j.maven.util.PluginUtils;

import java.nio.file.Path;
import java.util.jar.Manifest;

public class MavenManifestPluginDescriptorFinder extends ManifestPluginDescriptorFinder {

    @Override
    protected Manifest readManifest(Path pluginPath) {
        Path pluginJarPath = PluginUtils.getPluginJarPath(pluginPath);

        return readManifestFromJar(pluginJarPath);
    }

}
