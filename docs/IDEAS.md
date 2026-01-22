# Ideas

Ideas and potential improvements for pf4j-maven. These are exploratory notes, not a committed roadmap — some may be implemented, others may not.

Based on [pf4j/pf4j#208](https://github.com/pf4j/pf4j/issues/208) discussion and Maven Resolver capabilities.

## From Issue #208 Discussion

Features proposed in the original discussion:

| Feature | Proposed By | Status | Notes |
|---------|-------------|--------|-------|
| Transitive dependencies toggle | @janhoy | Not implemented | `mvn-dependencies-transitive=false\|true` per plugin |
| Checksum validation | @janhoy | Not implemented | Listed as limitation in DESIGN.md |
| Pre-bundled lib/ support | @janhoy | Partial | `skipExistingDependencies` exists |
| Dependency declarations for conflict detection | @janhoy | Not implemented | Detect colliding or blacklisted deps |

## Current Limitations (from DESIGN.md)

```
- No version conflict resolution between plugins
- No checksum verification (trusts Maven Resolver)
```

## Maven Resolver Capabilities Not Yet Exposed

Maven Resolver supports these features that we don't currently expose:

### Offline Mode
```java
// Maven Resolver supports offline mode
session.setOffline(true);
```
Use case: Production environments with pre-cached dependencies, air-gapped systems.

### Proxy Configuration
```java
// Maven Resolver supports proxy
Proxy proxy = new Proxy("http", "proxy.corp.com", 8080, auth);
RemoteRepository repo = new RemoteRepository.Builder(...)
    .setProxy(proxy)
    .build();
```
Use case: Corporate networks behind HTTP proxies.

### Repository Authentication
```java
// Maven Resolver supports authentication
Authentication auth = new AuthenticationBuilder()
    .addUsername("user")
    .addPassword("pass")
    .build();
RemoteRepository repo = new RemoteRepository.Builder(...)
    .setAuthentication(auth)
    .build();
```
Use case: Private Nexus/Artifactory repositories.

### Checksum Policy
```java
// Maven Resolver supports checksum policies
RepositoryPolicy policy = new RepositoryPolicy(true,
    RepositoryPolicy.UPDATE_POLICY_DAILY,
    RepositoryPolicy.CHECKSUM_POLICY_FAIL); // or WARN, IGNORE
```
Use case: Security-conscious environments requiring integrity verification.

### SNAPSHOT Update Policy
```java
// Configure SNAPSHOT update frequency
RepositoryPolicy snapshotPolicy = new RepositoryPolicy(true,
    RepositoryPolicy.UPDATE_POLICY_ALWAYS,  // or DAILY, NEVER
    RepositoryPolicy.CHECKSUM_POLICY_WARN);
```
Use case: Development vs production SNAPSHOT handling.

## Corner Cases to Address

### 1. Version Conflicts
```
Plugin A depends on commons-lang:2.6
Plugin B depends on commons-lang:3.12

Current: Each plugin gets its own copy in lib/
Problem: If plugins share classloader or interact, version mismatch possible
```
**Possible solutions:**
- Warn at resolution time
- Provide conflict detection API
- Document as known limitation (each plugin isolated)

### 2. Circular Plugin Dependencies
```
Plugin A -> Plugin B -> Plugin A
```
**Current behavior:** Eclipse Aether handles this during resolution.
**Action:** Add test to verify behavior, document.

### 3. Missing Plugin-Dependencies Declaration
```
Plugin A has Maven dependency on Plugin B (detected by Plugin-Id in MANIFEST)
But Plugin A's MANIFEST doesn't declare: Plugin-Dependencies: plugin-b
```
**Current behavior:** Plugin B installed but classloader visibility not established.
**Possible solutions:**
- Warn when detected
- Auto-generate Plugin-Dependencies (risky)
- Document as best practice

### 4. Signed JARs
```
Moving JAR from plugins/ to plugins/<id>/ may invalidate signature
```
**Possible solutions:**
- Copy instead of move for signed JARs
- Detect signed JARs and warn
- Document limitation

### 5. Parent POM Resolution
```xml
<parent>
    <groupId>com.example</groupId>
    <artifactId>parent</artifactId>
    <version>1.0</version>
</parent>
```
**Current behavior:** Parent POM not resolved, dependencies from parent not included.
**Possible solutions:**
- Use ModelBuilder to resolve effective POM
- Document as limitation (use flattened POMs)

### 6. Dependency Management / BOM
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```
**Current behavior:** Not resolved.
**Action:** Document as limitation.

## Proposed Features

### High Priority (Security/Enterprise)

#### 1. Checksum Verification Policy
```java
MavenPluginManagerBuilder.create()
    .checksumPolicy(ChecksumPolicy.FAIL)  // FAIL, WARN, IGNORE
    .build();
```
**Complexity:** Low - Maven Resolver already does this, just expose config.

#### 2. Repository Authentication
```java
MavenPluginManagerBuilder.create()
    .addRemoteRepository("nexus", "https://nexus.company.com/repo",
        Credentials.of("user", "password"))
    .build();
```
**Complexity:** Low - Maven Resolver supports this.

#### 3. Offline Mode
```java
MavenPluginManagerBuilder.create()
    .offline(true)  // Use only local repository
    .build();
```
**Complexity:** Low - Single flag on session.

#### 4. Proxy Support
```java
MavenPluginManagerBuilder.create()
    .proxy("http", "proxy.corp.com", 8080)
    .proxyAuth("user", "password")  // optional
    .build();
```
**Complexity:** Medium - Need to apply to all repositories.

### Medium Priority (Usability)

#### 5. Version Conflict Detection
```java
// During resolution, detect and warn about version conflicts
ConflictReport report = pluginManager.getConflictReport();
for (Conflict c : report.getConflicts()) {
    log.warn("Conflict: {} required by {} plugins with versions: {}",
        c.getArtifact(), c.getPlugins(), c.getVersions());
}
```
**Complexity:** Medium - Need to track all resolved artifacts across plugins.

#### 6. Dependency Blacklist
```java
MavenPluginManagerBuilder.create()
    .blacklist("log4j:log4j:2.14.0")  // CVE-2021-44228
    .blacklist("commons-collections:commons-collections:[3.0,3.2.1]")
    .build();
```
**Complexity:** Medium - Check resolved artifacts against blacklist.

#### 7. Plugin Update Check
```java
List<UpdateInfo> updates = pluginManager.checkForUpdates();
for (UpdateInfo u : updates) {
    log.info("Plugin {} has update: {} -> {}",
        u.getPluginId(), u.getCurrentVersion(), u.getLatestVersion());
}
```
**Complexity:** Medium - Query repository for latest versions.

#### 8. SNAPSHOT Update Policy
```java
MavenPluginManagerBuilder.create()
    .snapshotUpdatePolicy(UpdatePolicy.ALWAYS)  // ALWAYS, DAILY, NEVER
    .build();
```
**Complexity:** Low - Configure repository policy.

### Lower Priority (Nice to Have)

#### 9. Download Progress Listener
```java
MavenPluginManagerBuilder.create()
    .downloadListener(event -> {
        System.out.printf("Downloading %s: %d%%\n",
            event.getArtifact(), event.getProgress());
    })
    .build();
```
**Complexity:** Low - Already have ConsoleTransferListener, just expose API.

#### 10. Plugin Uninstall
```java
pluginManager.uninstallPlugin("my-plugin");
// Removes plugins/my-plugin/ directory
```
**Complexity:** Low - Delete directory, but consider running plugins.

#### 11. Force Refresh
```java
MavenPluginManagerBuilder.create()
    .skipExistingDependencies(false)
    .forceRefresh(true)  // Re-download even if exists
    .build();
```
**Complexity:** Low - Delete lib/ before resolution.

#### 12. Dependency Exclusions
```java
MavenPluginManagerBuilder.create()
    .exclude("commons-logging:commons-logging")  // Global exclusion
    .build();
```
**Complexity:** Medium - Apply exclusions during resolution.

#### 13. GPG Signature Verification
```java
MavenPluginManagerBuilder.create()
    .verifySignatures(true)
    .trustedKeys(Paths.get("trusted-keys.gpg"))
    .build();
```
**Complexity:** High - Need GPG library, key management.

#### 14. Transitive Dependencies Toggle
```java
// Per-plugin control via MANIFEST or pom.xml property
Maven-Dependencies-Transitive: false
```
**Complexity:** Medium - Need to read and apply per-plugin.

## Implementation Order Recommendation

### Phase 1: Enterprise Essentials
1. Offline mode
2. Proxy support
3. Repository authentication
4. Checksum policy

### Phase 2: Safety & Visibility
5. Version conflict detection (warning only)
6. Dependency blacklist
7. SNAPSHOT update policy

### Phase 3: Developer Experience
8. Download progress listener (expose existing)
9. Plugin uninstall
10. Force refresh
11. Plugin update check

### Phase 4: Advanced
12. Dependency exclusions
13. Transitive toggle
14. GPG signature verification

## References

- [Maven Resolver Documentation](https://maven.apache.org/resolver/)
- [Checksum Policies](https://maven.apache.org/resolver/about-checksums.html)
- [PF4J Issue #208](https://github.com/pf4j/pf4j/issues/208)
- [Eclipse Aether API](https://maven.apache.org/resolver/apidocs/)
