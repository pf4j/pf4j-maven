# Plugin Dependencies

How MavenPluginManager handles dependencies between plugins.

## Responsibility Separation

Understanding which component controls each behavior:

| Aspect | Controlled By |
|--------|---------------|
| Dependency resolution, version selection | Maven Resolver |
| Scope filtering (provided, test) | Maven Resolver |
| Transitive dependency tree | Maven Resolver |
| Plugin lifecycle, extension discovery | PF4J |
| Classloader hierarchy, Plugin-Dependencies | PF4J |
| Plugin vs library detection | MavenPluginManager |
| Installation directory structure | MavenPluginManager |

Maven Resolver behavior can be customized through standard Maven mechanisms (settings.xml, repository configuration, etc.).

## Scenarios

### 1. Plugin depends on a library

Standard case: Plugin A has a Maven dependency on a regular library.

```
plugins.txt:
  com.example:plugin-a:1.0.0

plugin-a pom.xml:
  <dependency>
    <groupId>commons-lang</groupId>
    <artifactId>commons-lang</artifactId>
    <version>2.6</version>
  </dependency>
```

**Result:**
```
plugins/
  plugin-a/
    plugin-a.jar
    lib/
      commons-lang-2.6.jar
```

### 2. Plugin depends on another plugin (Maven dependency)

Plugin A has a Maven dependency on Plugin B, where B is also a PF4J plugin (has `Plugin-Id` in MANIFEST).

```
plugins.txt:
  com.example:plugin-a:1.0.0

plugin-a pom.xml:
  <dependency>
    <groupId>com.example</groupId>
    <artifactId>plugin-b</artifactId>
    <version>1.0.0</version>
  </dependency>
```

**Detection:** MavenPluginManager reads `Plugin-Id` from plugin-b.jar MANIFEST and installs it as a separate plugin.

**Result:**
```
plugins/
  plugin-a/
    plugin-a.jar
    lib/
  plugin-b/
    plugin-b.jar
    lib/
```

### 3. Standard PF4J Plugin-Dependencies

Plugin A declares dependency using PF4J's `Plugin-Dependencies` manifest attribute.

```
plugin-a MANIFEST.MF:
  Plugin-Id: plugin-a
  Plugin-Dependencies: plugin-b
```

**Behavior:** Standard PF4J. Plugin B must be installed. PF4J loads B before A and manages classloader visibility.

## Detection Logic

When processing Maven dependencies, MavenPluginManager:

1. Receives resolved artifact from Maven Resolver
2. Reads MANIFEST.MF from JAR
3. Checks for `Plugin-Id` attribute
4. If present: installs as separate plugin
5. If absent: copies to parent plugin's lib/

## Corner Cases

### Version conflicts

```
plugin-a -> plugin-c:1.0.0
plugin-b -> plugin-c:2.0.0
```

**This is Maven Resolver behavior.** Maven uses "nearest wins" strategy by default. Version resolution can be customized through:
- Dependency management in pom.xml
- Maven settings.xml
- Repository policies

MavenPluginManager receives the already-resolved version from Maven.

### Plugin already installed

If a plugin directory already exists with JAR files, installation is skipped. This is MavenPluginManager behavior to avoid overwriting.

### Missing Plugin-Dependencies declaration

If Plugin A has Maven dependency on Plugin B but no `Plugin-Dependencies: plugin-b`:

- Plugin B is installed separately (MavenPluginManager)
- Classloader visibility not established (PF4J limitation)

**Fix:** Add `Plugin-Dependencies` to MANIFEST for classloader access.

### Scope filtering

Only `compile` and `runtime` scopes are processed. `provided` and `test` are skipped. This is standard Maven scope semantics handled by Maven Resolver.

## Best Practices

1. **Declare both dependencies**
   - Maven: for artifact resolution
   - Plugin-Dependencies: for PF4J classloader visibility

2. **Use dependency management**
   - Coordinate versions via Maven BOM or dependencyManagement
   - Maven Resolver handles version selection

3. **Explicit over implicit**
   - For production, list all plugins in plugins.txt
   - Automatic detection is a development convenience
