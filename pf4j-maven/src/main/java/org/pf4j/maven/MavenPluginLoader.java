package org.pf4j.maven;

import org.pf4j.BasePluginLoader;
import org.pf4j.PluginManager;

public class MavenPluginLoader extends BasePluginLoader {

    public MavenPluginLoader(PluginManager pluginManager) {
        super(pluginManager, new MavenPluginClasspath());
    }

}
