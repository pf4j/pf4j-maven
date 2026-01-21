# Proposal: Loose Plugin JARs with Embedded POM

Move dependency declaration from external `plugins.txt` to the plugin's embedded pom.xml.

## Current State

```
plugins.txt:
  org.pf4j.demo:my-plugin:jar:plugin:1.0.0

plugins/
  (empty until loadPlugins() runs)
```

Dependencies are resolved based on external file. Plugin JAR must be fetched first.

## Implemented State

```
plugins/
  my-plugin.jar    # User places JAR here

my-plugin.jar!/META-INF/maven/<groupId>/<artifactId>/pom.xml:
  <dependencies>
    <dependency>
      <groupId>commons-lang</groupId>
      <artifactId>commons-lang</artifactId>
      <version>2.6</version>
    </dependency>
  </dependencies>
```

Dependencies are read from the embedded pom.xml (standard Maven artifact structure). Plugin is self-contained.

## Why pom.xml Instead of MANIFEST Attribute?

**DRY principle**: The pom.xml is already embedded in every Maven-built JAR. No need to duplicate dependency information in MANIFEST.

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

1. Scan `plugins/` for JARs with embedded pom.xml → resolve dependencies
2. Read `plugins.txt` (if exists) → download plugin + resolve dependencies from POM
3. Standard PF4J loading

Rationale:
- Demo/development works with plugins.txt (existing flow)
- Production can use loose JAR approach (self-contained plugins)
- Community can provide feedback on preferred approach

### 4. Backward compatibility

Plugins without embedded pom.xml (or with empty dependencies):
- Load normally (standard PF4J behavior)
- No Maven resolution attempted

## Flow

```
loadPlugins():
  │
  ├─► Scan plugins/ for loose JAR files
  │     │
  │     └─► For each JAR with pom.xml in META-INF/maven/:
  │           - Read dependencies from pom.xml
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

## Implementation (Done)

1. ✓ Scan `plugins/` for loose JAR files (`findLooseJars()`)
2. ✓ Read pom.xml from JAR (`readPomFromJar()` using MavenXpp3Reader)
3. ✓ Extract dependencies from Maven Model
4. ✓ Reorganize JAR into `plugins/<plugin-id>/` structure
5. ✓ Skip resolution if `lib/` already exists and non-empty
6. ✓ Keep `plugins.txt` support (existing code)
7. ✓ Document both mechanisms

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
