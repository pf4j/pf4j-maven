# Proposal: Maven Dependencies in MANIFEST

Move dependency declaration from external `plugins.txt` to the plugin's MANIFEST.

## Current State

```
plugins.txt:
  org.pf4j.demo:my-plugin:jar:plugin:1.0.0

plugins/
  (empty until loadPlugins() runs)
```

Dependencies are resolved based on external file. Plugin JAR must be fetched first.

## Proposed State

```
plugins/
  my-plugin.jar    # User places JAR here

my-plugin.jar!/META-INF/MANIFEST.MF:
  Plugin-Id: my-plugin
  Plugin-Class: com.example.MyPlugin
  Plugin-Version: 1.0.0
  Maven-Dependencies: commons-lang:commons-lang:2.6, com.google.guava:guava:32.0
```

Dependencies are declared in the plugin itself. Plugin is self-contained.

## MANIFEST Format

```
Maven-Dependencies: groupId:artifactId:version, groupId:artifactId:version
```

Or with explicit scope:
```
Maven-Dependencies: commons-lang:commons-lang:2.6:compile, slf4j-api:slf4j-api:2.0.0:provided
```

## Decisions

### 1. Directory structure

**Decision: Subdirectory (folder per plugin)**

```
plugins/
  my-plugin/
    my-plugin.jar
    lib/
      commons-lang-2.6.jar
```

Rationale:
- Consistent with existing PF4J ZIP structure
- One plugin = one folder (easy to inspect, delete, move)
- Works with existing `MavenPluginClasspath`

Future: Code structured to allow alternative layouts via strategy pattern if needed.

### 2. Plugin with existing lib/

**Decision: Skip Maven resolution**

If `lib/` exists and is non-empty, skip dependency resolution entirely.

```java
if (Files.exists(libPath) && !isEmpty(libPath)) {
    log.debug("lib/ exists, skipping Maven resolution");
    return;
}
```

Rationale:
- KISS - simple, predictable behavior
- Respects user intent (pre-bundled = knows what they're doing)
- Avoids version conflicts between pre-bundled and resolved

### 3. plugins.txt coexistence

**Decision: Support both mechanisms**

1. Scan `plugins/` for JARs with `Maven-Dependencies` in MANIFEST → resolve dependencies
2. Read `plugins.txt` (if exists) → download plugin + resolve dependencies from POM
3. Standard PF4J loading

Rationale:
- Demo/development works with plugins.txt (existing flow)
- Production can use MANIFEST approach (self-contained plugins)
- Community can provide feedback on preferred approach

### 4. Backward compatibility

Plugins without `Maven-Dependencies`:
- Load normally (standard PF4J behavior)
- No Maven resolution attempted

## Flow

```
loadPlugins():
  │
  ├─► Scan plugins/ for JAR files
  │     │
  │     └─► For each JAR with Maven-Dependencies in MANIFEST:
  │           - Create plugins/<plugin-id>/
  │           - Move JAR to plugins/<plugin-id>/
  │           - If lib/ empty: resolve and copy dependencies
  │
  ├─► Read plugins.txt (if exists)
  │     │
  │     └─► For each coordinate:
  │           - Download plugin JAR
  │           - Create plugins/<artifactId>/
  │           - Resolve and copy dependencies
  │
  └─► Standard PF4J loading
```

## Implementation Steps

1. Add method to scan `plugins/` for loose JAR files
2. Add MANIFEST reading for `Maven-Dependencies` attribute
3. Parse coordinates (comma-separated `groupId:artifactId:version`)
4. Reorganize JAR into `plugins/<plugin-id>/` structure
5. Skip resolution if `lib/` already exists and non-empty
6. Keep `plugins.txt` support (existing code)
7. Document both mechanisms

## Benefits

- Plugin is self-contained (one JAR to distribute)
- No external configuration needed for MANIFEST approach
- Backward compatible with plugins.txt
- Works with any distribution mechanism (copy, pf4j-update, etc.)
- Clear ownership: plugin declares its own dependencies

## Risks

- More complex loadPlugins() logic
- JAR reorganization at runtime
- Potential issues with signed JARs (moving them)
