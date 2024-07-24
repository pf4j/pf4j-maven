package org.pf4j.maven;

import org.pf4j.DefaultPluginClasspath;
import org.pf4j.PluginClasspath;

public class MavenPluginClasspath extends PluginClasspath {

    public MavenPluginClasspath() {
        addJarsDirectories(".");
        addJarsDirectories(DefaultPluginClasspath.LIB_DIR);
    }

}
