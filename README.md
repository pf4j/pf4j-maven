PF4J Maven
=====================

A proof-of-concept for [pf4j/pf4j#208](https://github.com/pf4j/pf4j/issues/208) - plugins as Maven artifacts with automatic dependency resolution.

## Overview

Instead of packaging plugins as fat JARs or ZIPs with bundled dependencies, this approach allows plugins to be specified as Maven coordinates. Dependencies are resolved and downloaded automatically at runtime using [Maven Resolver](https://maven.apache.org/resolver/).

**Traditional PF4J:**
```
plugins/
  my-plugin.jar (fat jar with all dependencies inside)
```

**PF4J Maven:**
```
plugins.txt:
  com.example:my-plugin:jar:plugin:1.0.0

# Dependencies resolved automatically from Maven repositories
```

## Features

- Plugins specified as Maven coordinates (`groupId:artifactId:jar:classifier:version`)
- Automatic transitive dependency resolution
- Uses local Maven repository (`~/.m2/repository`) and Maven Central
- Caches resolved artifacts to avoid redundant downloads

## How It Works

1. Application reads plugin coordinates from `plugins.txt`
2. `MavenPluginManager` resolves each artifact using Maven Resolver
3. Plugin JAR is copied to `plugins/<artifactId>/`
4. Dependencies (excluding `provided` and `test` scope) are copied to `plugins/<artifactId>/lib/`
5. Standard PF4J loading takes over

## Usage

### Basic Usage

```java
// Use the builder to create MavenPluginManager
PluginManager pluginManager = MavenPluginManagerBuilder.create().build();
pluginManager.loadPlugins();
pluginManager.startPlugins();

List<Greeting> greetings = pluginManager.getExtensions(Greeting.class);
for (Greeting greeting : greetings) {
    System.out.println(">>> " + greeting.getGreeting());
}
```

### Builder Configuration

The builder supports various configuration options:

```java
PluginManager pluginManager = MavenPluginManagerBuilder.create()
    .pluginsRoot(Paths.get("my-plugins"))           // custom plugins directory
    .localRepository(Paths.get("/custom/m2/repo"))  // custom local Maven repo
    .addRemoteRepository("jitpack", "https://jitpack.io")  // additional repo
    .includeMavenCentral(true)                      // include Maven Central (default)
    .processLooseJars(true)                         // process loose JARs (default)
    .skipExistingDependencies(true)                 // skip if lib/ exists (default)
    .build();
```

### Extending MavenPluginManager

For advanced customization, extend `MavenPluginManager`:

```java
class CustomPluginManager extends MavenPluginManager {
    @Override
    protected PluginFactory createPluginFactory() {
        return new CustomPluginFactory();
    }
}
```

`plugins.txt`:
```
org.pf4j.demo:pf4j-maven-demo-plugin1:jar:plugin:1.0.0-SNAPSHOT
# org.pf4j.demo:pf4j-maven-demo-plugin2:jar:plugin:1.0.0-SNAPSHOT
```

## Demo

A working demo is available in the [demo](demo) folder.

**Quick run:**
```bash
./run-demo.sh      # Linux/Mac
run-demo.bat       # Windows
```

**Output:**
```
>>> Whazzup
>>> Welcome
```

## Project Structure

```
pf4j-maven/          # Core library
  MavenPluginManager
  MavenPluginManagerBuilder
  MavenPluginLoader
  MavenUtils

demo/
  app/               # Demo application
  api/               # Shared plugin API
  plugins/           # Demo plugins
    plugin1/         # welcome-plugin (uses commons-lang)
    plugin2/         # hello-plugin
```

## Requirements

- Java 8+
- Maven 3.6+

## Related

- [PF4J](https://github.com/pf4j/pf4j) - Plugin Framework for Java
- [Issue #208](https://github.com/pf4j/pf4j/issues/208) - Original proposal for this feature
