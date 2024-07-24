package org.pf4j.maven;

import org.pf4j.PluginRuntimeException;
import org.pf4j.PropertiesPluginDescriptorFinder;
import org.pf4j.maven.util.PluginUtils;
import org.pf4j.util.FileUtils;

import java.io.IOException;
import java.nio.file.Path;

public class MavenPropertiesPluginDescriptorFinder extends PropertiesPluginDescriptorFinder {

    @Override
    protected Path getPropertiesPath(Path pluginPath, String propertiesFileName) {
        Path jarPath = PluginUtils.getPluginJarPath(pluginPath);

        try {
            return FileUtils.getPath(pluginPath, propertiesFileName);
        } catch (IOException e) {
            throw new PluginRuntimeException(e, "Cannot get properties file path for plugin '%s'", jarPath);
        }
    }

}
