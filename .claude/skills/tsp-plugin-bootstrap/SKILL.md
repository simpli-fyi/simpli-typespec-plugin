---
name: tsp-plugin-bootstrap
description: Scaffold this repository from the JetBrains IntelliJ Platform plugin template and configure it for a Community-Edition TypeSpec plugin. Use once, at project start, or when the build configuration (platform version, JDK, plugin id, dependencies) needs to be re-derived from the template.
---

# Bootstrapping the TypeSpec plugin repo

One-time setup. The working directory is `/Users/KHODIAKOVA/IdeaProjects/intellij-tsp-plugin`.

This was executed once, as milestone M0. The authoritative record of what was actually done
and why is `docs/adr/0002-build-and-platform-baseline.md` — read that first. This skill is a
recipe for re-deriving the same setup (e.g. if the repo needs to be re-bootstrapped from a
newer template release); where it and the ADR disagree, **the ADR wins**.

## 1. Get the template

The template is meant to be used via GitHub's "Use this template" button. Locally, the
equivalent is a clone with history dropped — but **never** wipe an existing working
directory that already has `CLAUDE.md`, `.claude/`, `docs/`, or `.idea/` in it. Clone into a
scratch directory and copy files in selectively instead:

```bash
git clone https://github.com/JetBrains/intellij-platform-plugin-template.git /tmp/iptp
cd /tmp/iptp && git checkout <tag, e.g. 2.6.0> && rm -rf .git
# then copy only the files this milestone's plan calls for, into the real working directory
```

Delete/skip template-only leftovers that don't apply: `.github/workflows/template-cleanup.yml`,
`.github/workflows/template-verify.yml`, `.github/template-cleanup/`, `.github/readme/`,
`CODE_OF_CONDUCT.md`. Delete the template's demo code (`MyBundle.kt`,
`services/MyProjectService.kt`, `startup/MyProjectActivity.kt`,
`toolWindow/MyToolWindowFactory.kt`, the `MyBundle.properties` resource bundle,
`MyPluginTest.kt`, `testData/rename/`) and the `<extensions>`/`<resource-bundle>` entries in
`plugin.xml` that reference them.

## 2. Read before editing

Read, in this order — do not guess their contents, they change between template releases:
`README.md`, `gradle.properties`, `build.gradle.kts`, `settings.gradle.kts`,
`src/main/resources/META-INF/plugin.xml`.

**As of template v2.6.0 there is no `gradle/libs.versions.toml` and no
`pluginGroup`/`pluginName`/`platformType`/`platformVersion`/`pluginSinceBuild`/
`pluginUntilBuild`/`gradleVersion`/`platformPlugins`/`platformBundledPlugins` property.**
Those were all removed. Do not look for them — confirm what actually exists in the cloned
template before writing any config. See ADR 0002 Finding 1 for the full diff against the
older template shape this skill used to describe.

## 3. Configure the build (template v2.6.0 shape)

`gradle.properties` now holds only: `group`, `version`, `pluginRepositoryUrl`,
`kotlin.stdlib.default.dependency`, `org.gradle.configuration-cache`, `org.gradle.caching`,
plus (added by this project, see §5) `org.gradle.java.home`.

| Key | Where | Value for this project |
|---|---|---|
| `group` (gradle.properties) | Gradle group | `simpli.fyi` |
| `version` (gradle.properties) | plugin version | `0.0.1` |
| `<id>` (plugin.xml) | plugin id | `simpli.fyi.plugins.typespec` |
| `<name>` (plugin.xml) | display name | `TypeSpec (Community)` |
| `<vendor>` (plugin.xml) | vendor | project owner's email |
| Platform artifact (build.gradle.kts, `intellijPlatform {}` block) | compile target | `intellijIdeaCommunity("2025.2.6.3")` — **the deprecated helper, used intentionally** |
| `sinceBuild` / `untilBuild` (build.gradle.kts, `pluginConfiguration.ideaVersion`) | compatibility range | `"252"` / open |

There is no `platformType` property to set to `IC` any more. **The equivalent enforcement
mechanism is D1 in ADR 0002**: call the deprecated `intellijIdeaCommunity(...)` helper
instead of `intellijIdea(...)`, because from 2025.3 onward `intellijIdea(...)` resolves the
*unified* distribution that carries Ultimate classes on the compile classpath — which would
silently defeat the whole point of this project. Pin to the last genuinely pre-unification
Community release (`2025.2.6.3` at the time of ADR 0002; re-verify against the
`ideaIC` Maven metadata before bumping).

## 4. `plugin.xml` dependency block

```xml
<depends>com.intellij.modules.platform</depends>
```

That is the whole list. **Nothing** may be added from: `com.intellij.modules.ultimate`,
`com.intellij.platform.lsp`, `JavaScript`, `NodeJS`. See `intellij-syntax-highlighting`
for what goes in the `<extensions>` block.

## 5. JDK

The IntelliJ Platform Gradle plugin needs a supported JDK (21 for the 2025.2 platform line).
This machine's default `java` is 26 (Oracle), which the Gradle daemon / JBR reject. Verify
available JDKs with `/usr/libexec/java_home -V`, then pin the **Gradle daemon** (not the
system default) to Zulu 21:

```properties
# gradle.properties
org.gradle.java.home = /Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home
```

```kotlin
// build.gradle.kts
kotlin { jvmToolchain(21) }
```

`org.gradle.java.home` is an absolute, machine-specific path. ADR 0002 D4's decision for
this single-machine repo is to commit it; if the repo is ever shared across machines, move
it to `~/.gradle/gradle.properties` instead and say so. Do **not** change the system default
JDK.

## 6. Verify the skeleton before writing any plugin code

```bash
./gradlew clean build
```

```bash
./gradlew verifyPlugin
```

`./gradlew runIde` must launch a sandbox IntelliJ **Community** instance, but it blocks — do
not run it in an unattended agent turn; leave it for manual verification. Only once
`build`/`verifyPlugin` are green does implementation start. Report the actual outcome of
every command, verbatim.
