# Simpli TypeSpec Highlighter

An IntelliJ plugin providing [TypeSpec](https://typespec.io/) (`.tsp`) syntax highlighting,
built to run on **IntelliJ IDEA Community Edition** with no Ultimate-only APIs, no LSP, and
no Node.js dependency.

See `CLAUDE.md` for the project constraint and team workflow, and `docs/adr/` /
`docs/plans/` for design decisions and the milestone roadmap.

## Status

Milestone M0 (bootstrap) is complete: the project builds and verifies against IntelliJ IDEA
Community `2025.2.6.3`, with zero plugin functionality yet. Follow `docs/plans/00-milestones.md`
for what ships next.

## Build

```bash
./gradlew build
```

```bash
./gradlew test
```

```bash
./gradlew verifyPlugin
```

```bash
./gradlew runIde
```

## Non-negotiable constraint

The only permitted `<depends>` in `plugin.xml` is `com.intellij.modules.platform`. The build
is pinned to a real Community (`ideaIC`) compile classpath specifically so that importing an
Ultimate-only, LSP, JavaScript, or NodeJS API fails to compile — see
`docs/adr/0002-build-and-platform-baseline.md`.
