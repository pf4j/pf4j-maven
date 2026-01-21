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

## Flow

```
1. User places plugin.jar in plugins/
2. loadPlugins() scans plugins/ for JARs
3. For each JAR:
   a. Read MANIFEST
   b. If Maven-Dependencies present:
      - Parse coordinates
      - Resolve from Maven repos
      - Copy to plugins/<plugin-id>/lib/
   c. Move JAR to plugins/<plugin-id>/
4. Standard PF4J loading continues
```

## MANIFEST Format

```
Maven-Dependencies: groupId:artifactId:version, groupId:artifactId:version
```

Or with explicit scope:
```
Maven-Dependencies: commons-lang:commons-lang:2.6:compile, slf4j-api:slf4j-api:2.0.0:provided
```

Multiple lines (if needed):
```
Maven-Dependencies: commons-lang:commons-lang:2.6
Maven-Dependencies: com.google.guava:guava:32.0
```

## Questions to Resolve

### 1. Directory structure

**Option A:** Flat with convention
```
plugins/
  my-plugin.jar
  my-plugin.lib/
    commons-lang-2.6.jar
```

**Option B:** Subdirectory (current approach)
```
plugins/
  my-plugin/
    my-plugin.jar
    lib/
      commons-lang-2.6.jar
```

Option B is more consistent with existing PF4J ZIP structure.

### 2. What if plugin already has lib/?

If user places a plugin with pre-bundled dependencies:
- Skip Maven resolution entirely?
- Merge with resolved dependencies?
- Fail with error?

Recommendation: Skip resolution if `lib/` already exists and is non-empty.

### 3. Backward compatibility

Plugins without `Maven-Dependencies`:
- Load normally (standard PF4J behavior)
- No Maven resolution attempted

### 4. plugins.txt coexistence

Options:
- **Remove entirely** - convention only (scan plugins/)
- **Keep as optional bootstrap** - for downloading plugins initially
- **Both mechanisms** - plugins.txt OR JAR with MANIFEST

Recommendation: Start with convention only. Add plugins.txt back if needed.

## Implementation Steps

1. Modify `loadPlugins()` to scan `plugins/` for JAR files
2. Add MANIFEST reading for `Maven-Dependencies` attribute
3. Parse coordinates and resolve dependencies
4. Reorganize into `plugins/<plugin-id>/` structure
5. Remove `plugins.txt` requirement

## Benefits

- Plugin is self-contained (one JAR to distribute)
- No external configuration needed
- Works with any distribution mechanism (copy, pf4j-update, etc.)
- Clear ownership: plugin declares its own dependencies

## Risks

- More complex loadPlugins() logic
- JAR reorganization at runtime
- Potential issues with signed JARs (moving them)
