# ADR 0002 — Build, platform and JDK baseline

- Status: **Accepted, with two items needing owner ratification** (plugin id/vendor, publishing).
  **D6 is superseded by [ADR 0006](0006-grammar-toolchain.md)** (grammar/lexer generation moves
  to the IPGP `grammarkit` subplugin; IPGP bumps 2.16.0 → 2.18.1). D1–D5 and D7 stand.
- Date: 2026-09-02
- Deciders: `tsp-architect` (proposed), project owner (to ratify)

## Context

`CLAUDE.md` mandates the JetBrains template
(`JetBrains/intellij-platform-plugin-template`, IntelliJ Platform Gradle Plugin 2.x) and
`platformType=IC`. Both instructions were written against an older template revision. The
template as it stands **today** (v2.6.0, verified by clone on 2026-09-02) has moved on, and
the platform itself has changed underneath the "IC" concept. This ADR records what we
actually do.

### Finding 1 — the template no longer has the properties `CLAUDE.md` and `tsp-plugin-bootstrap` describe

Template v2.6.0, verified contents:

- `gradle/libs.versions.toml` — **removed** (template 2.5.0). Plugin versions are inlined in
  `settings.gradle.kts`.
- `gradle.properties` now contains only:
  `group`, `version`, `pluginRepositoryUrl`, `kotlin.stdlib.default.dependency`,
  `org.gradle.configuration-cache`, `org.gradle.caching`.
- `pluginGroup`, `pluginName`, `platformType`, `platformVersion`, `pluginSinceBuild`,
  `pluginUntilBuild`, `gradleVersion`, `platformPlugins`, `platformBundledPlugins` — **all
  removed**. There is no `platformType` property to set to `IC` any more.
- The platform is now selected in `build.gradle.kts`:
  ```kotlin
  dependencies {
      intellijPlatform {
          intellijIdea("2025.2.6.2")
          testFramework(TestFrameworkType.Platform)
      }
  }
  ```
- `settings.gradle.kts` pins: Kotlin JVM `2.1.20`, `org.jetbrains.changelog` `2.5.0`,
  `org.jetbrains.intellij.platform.settings` **`2.16.0`**, foojay resolver `1.0.0`.
- Gradle wrapper: **9.5.0**. Configuration cache and build cache are **on**.
- `plugin.xml` already contains exactly `<depends>com.intellij.modules.platform</depends>`.
- CI (`.github/workflows/build.yml`) uses **Zulu JDK 21**.
- `sinceBuild`/`untilBuild` are no longer configured: the IntelliJ Platform Gradle Plugin
  derives `sinceBuild` from the resolved platform and leaves `untilBuild` open by default.

**Consequence:** the `tsp-plugin-bootstrap` skill's §3 table is stale. `tsp-dev` must follow
this ADR where the two disagree, and should update the skill as part of M0.

### Finding 2 — `IC` as a separate distribution ended at 2025.3

From 2025.3 onward IntelliJ IDEA Community and Ultimate ship as a **single unified
distribution** carrying the `IU` product code; `com.intellij.modules.ultimate` became a
*licensing* module rather than a distribution marker. Correspondingly the Gradle plugin
deprecated `intellijIdeaCommunity()` / `IntelliJPlatformType.IntellijIdeaCommunity` in
favour of `intellijIdea()`.

This directly threatens our enforcement story. `intellijIdea(...)` puts **Ultimate classes
on the compile classpath**, so nothing would fail fast if someone imported an Ultimate-only
API — the constraint would degrade from "the build stops you" to "someone notices in review".

However (verified against `https://cache-redirector.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/maven-metadata.xml`
on 2026-09-02) the `com.jetbrains.intellij.idea:ideaIC` artifact **is still published**,
latest `2026.2.1`, and `2025.2.6.3` is the last release of the genuinely pre-unification
Community line.

## Decisions

### D1 — Compile against a real Community distribution, pinned to the last pre-unification release

```kotlin
dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.2.6.3")   // deprecated helper, intentionally used
        testFramework(TestFrameworkType.Platform)
    }
}
```

Rationale: this is the only remaining way to get a compile classpath that **physically does
not contain** Ultimate API. That fail-fast property is the entire point of the project
constraint, and it is worth more than being on the newest platform. The deprecation warning
is expected; suppress it with a comment pointing at this ADR, do not "fix" it.

`platformType = IC` in `gradle.properties` (as literally written in `CLAUDE.md`) is
**not implementable** on this template — this decision is its faithful equivalent.

### D2 — Compatibility range: `sinceBuild` 252, `untilBuild` open

```kotlin
intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252"
            untilBuild = provider { null }   // open-ended
        }
    }
}
```

- `252` = 2025.2, matching D1.
- Open `untilBuild` because the plugin uses only stable, long-lived platform API and we do
  not want a hard cut-off on every IDE release. Forward compatibility is proven by D3, not
  asserted by a version range.
- Revisit only if a real incompatibility appears.

### D3 — Prove forward compatibility with the Plugin Verifier, not with the compile target

```kotlin
intellijPlatform {
    pluginVerification {
        ides {
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2025.2.6.3")
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2025.3.6.1")
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2026.1.5")
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2026.2.1")
        }
    }
}
```

`./gradlew verifyPlugin` must be clean across all four. **A verifier report naming any
class outside `com.intellij.modules.platform` is a build failure for this project**, not a
warning. This is wired in M0 with one IDE and widened in M7.

### D4 — JDK 21, pinned explicitly

The machine's default `java` is **26.0.1** (Oracle). Installed alternatives, verified:

| JDK | Path |
|---|---|
| 26.0.1 (default) | `/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home` |
| 25.0.3 | `/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home` |
| **21.0.10 (Zulu)** | `/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home` |

Decision: run the **Gradle daemon itself** on JDK 21, matching the template's own CI
(`actions/setup-java` → zulu 21) and the JBR that IntelliJ 2025.2 tests require.

In `gradle.properties`:

```properties
org.gradle.java.home = /Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home
```

and, for portability on other machines, a toolchain in `build.gradle.kts`:

```kotlin
kotlin { jvmToolchain(21) }
```

`org.gradle.java.home` is an **absolute, machine-specific path**. It must NOT be committed
to the shared `gradle.properties` if this repo is ever shared — put it in
`~/.gradle/gradle.properties`, or commit it and accept the coupling. Owner decision; the M0
default is to put it in the repo's `gradle.properties` with a comment, because this repo is
currently single-machine. Do **not** change the system default JDK.

### D5 — Naming

Ratified by the owner on 2026-09-02, to sit alongside the already-released
`simpli.fyi.plugins.forge.manifest.validator` ("Simpli Forge Manifest Validator").

| Item | Value | Status |
|---|---|---|
| Gradle `group` | `simpli.fyi` | **ratified** |
| Kotlin package root | `simpli.fyi.plugins.typespec` | **ratified** — deliberately forward-domain, not reverse-domain, so the package matches the plugin id and the Forge plugin's id shape |
| `<id>` in `plugin.xml` | `simpli.fyi.plugins.typespec` | **ratified** — follows `simpli.fyi.plugins.<product>` |
| `<name>` | `Simpli TypeSpec Highlighter` | **ratified** — the `Simpli <product> <function>` pattern also sidesteps the Marketplace display-name collision with `siketyan`'s plugin |
| `<vendor>` | `simpli.fyi`, `hello@simpli.fyi`, `https://simpli.fyi` | **ratified** |
| `version` | `0.0.1` | proposed |
| Source-set layout | `src/main/kotlin/simpli/fyi/plugins/typespec/...`, JFlex spec in `src/main/grammars/`, generated lexer into `build/generated/sources/jflex/` | decided |

### D6 — Lexer generation: no `org.jetbrains.grammarkit` plugin

> ⚠ **SUPERSEDED by [ADR 0006](0006-grammar-toolchain.md) (2026-09-02).** The conclusion below
> is **reversed**: `JetBrains/gradle-grammar-kit-plugin` is now *archived*, and Grammar-Kit
> integration moved into a subplugin of the IntelliJ Platform Gradle Plugin we already apply
> (`org.jetbrains.intellij.platform.grammarkit`, added in IPGP 2.12.0). M5a adopts it, bumps
> IPGP to 2.18.1 and retires the hand-rolled `JavaExec` task described here. The fallback
> clause below (commit `idea-flex.skeleton`, or commit the generated `.java`) is **withdrawn**.
> D6's *reasoning* was sound for the information available in M2 — it is kept verbatim below
> as the record of why the hand-rolled task existed for M2–M4b. **Every other decision in this
> ADR (D1–D5, D7) is unaffected.**

The Grammar-Kit Gradle plugin's latest release is `2023.3.0.3`; it predates Gradle 9 and
the configuration cache that this template enables. Instead, M2 adds:

```kotlin
val jflex: Configuration by configurations.creating
dependencies { jflex("org.jetbrains.intellij.deps.jflex:jflex:1.10.17") }
// + a JavaExec task with declared inputs/outputs
```

`org.jetbrains.intellij.deps.jflex:jflex` is **not on Maven Central**; it resolves from the
JetBrains `intellij-dependencies` repository, which `defaultRepositories()` already
includes (verified: versions up to `1.10.17`, last updated 2026-04-29). The JetBrains fork
of JFlex uses the IntelliJ lexer skeleton, which is what makes the generated lexer
restartable/incremental.

**Fallback if the JavaExec route fights the configuration cache:** commit
`idea-flex.skeleton` and pass `--skel`, or commit the generated `.java` and regenerate
manually. Amend this ADR if the fallback is taken.

### D7 — Publishing

**Deferred, and out of scope until the owner says otherwise.** No Marketplace token, no
signing certificate, no `publishPlugin` invocation. The template's `release.yml` workflow
should be left in place but never triggered until D5's open items are settled.

## Open questions for the owner

1. ~~Gradle `group` / plugin `<id>`~~ — **resolved**, see D5.
2. ~~`<vendor>` name / email / URL~~ — **resolved**, see D5. `pluginRepositoryUrl` is still unset.
3. Do you intend to publish to the JetBrains Marketplace at all? The display-name collision
   with `siketyan/intellij-typespec-plugin` is resolved by the `Simpli` prefix, but D7
   (publishing) remains deferred.

Note on the plugin id: an earlier id containing the word `intellij` was rejected outright by
the Plugin Verifier's `TemplateWordInPluginId` check. `simpli.fyi.plugins.typespec` avoids it,
so no verifier mute is needed.
4. Is `org.gradle.java.home` acceptable in the committed `gradle.properties` (single-machine
   repo), or should it go in `~/.gradle/gradle.properties`?
5. Minimum supported IDE: is `252` (2025.2) right, or would you rather start at `253`/`261`
   and drop the deprecated `intellijIdeaCommunity()` helper?

## Consequences

- The build fails fast on any Ultimate API — the constraint is enforced by the compiler,
  not by discipline.
- We develop against a platform that is roughly a year behind current. Anything added to the
  platform after 2025.2 is unavailable to us. This is accepted; nothing in plans 00/01 needs
  it.
- `runIde` launches IntelliJ IDEA **Community 2025.2.6.3**, not the user's daily IDE. That
  is the correct sandbox for this project.
- The `tsp-plugin-bootstrap` skill must be updated in M0 to match this ADR, otherwise the
  next person re-derives a build that does not exist.
