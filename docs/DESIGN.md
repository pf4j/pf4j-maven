# Design

Current design decisions and rationale.

## Core Concept

Plugins are Maven artifacts. Dependencies are resolved at runtime, not bundled.

```
Traditional PF4J:
  plugin.jar (fat jar, 50MB with all dependencies)

PF4J Maven:
  plugin.jar (thin jar, 50KB) + dependencies resolved from Maven repos
```

## Plugin Resolution Flow

```
plugins.txt                     # Maven coordinates
    ↓
MavenPluginManager.loadPlugins()
    ↓
Maven Resolver                  # Resolve from ~/.m2 or Central
    ↓
plugins/<artifactId>/
    ├── plugin.jar
    └── lib/
        ├── dep1.jar
        └── dep2.jar
    ↓
Standard PF4J loading
```

## Key Decisions

### Local repository first

Artifacts are resolved from `~/.m2/repository` before Maven Central. This allows:
- Offline development with pre-cached dependencies
- Use of SNAPSHOT versions during development
- Faster resolution for cached artifacts

### Dependencies in lib/ folder

Dependencies go to `plugins/<plugin>/lib/`, not mixed with the plugin JAR. This:
- Matches existing PF4J ZIP plugin structure
- Makes it clear what's plugin code vs dependencies
- Allows easy inspection and debugging

### Skip provided and test scope

Only `compile` and `runtime` scope dependencies are copied. `provided` scope assumes the host application supplies those (like pf4j itself).

### Caching

Artifacts are not re-copied if they already exist. This speeds up repeated startups during development.

## What This Is Not

- Not a replacement for pf4j-update (installation/update mechanism)
- Not a build tool (use Maven/Gradle to build plugins)
- Not a plugin marketplace

## Limitations

- Requires network access for first resolution (unless cached)
- No version conflict resolution between plugins
- No checksum verification (trusts Maven Resolver)
