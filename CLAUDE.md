# TypeSpec syntax highlighting plugin for IntelliJ IDEA

Goal: an IntelliJ plugin providing TypeSpec (`.tsp`) syntax highlighting that works on
**IntelliJ IDEA Community Edition**.

- Base: [JetBrains/intellij-platform-plugin-template](https://github.com/JetBrains/intellij-platform-plugin-template) (Kotlin + Gradle, IntelliJ Platform Gradle Plugin 2.x)
- Prior art: [siketyan/intellij-typespec-plugin](https://github.com/siketyan/intellij-typespec-plugin) — **Ultimate-only**; it declares `com.intellij.modules.ultimate` and uses the LSP API plus `JavaScript`/`NodeJS`. Use it as a feature checklist, never as an implementation to copy.

## Non-negotiable constraint

The only permitted `<depends>` is `com.intellij.modules.platform`. `platformType=IC` in
`gradle.properties`. Anything requiring `com.intellij.modules.ultimate`,
`com.intellij.platform.lsp.*`, `JavaScript`, or `NodeJS` is out of scope by definition —
that constraint is the whole point of this project.

## The team

| Agent | Model | Role |
|---|---|---|
| `tsp-architect` | opus | Designs milestones and records decisions in `docs/adr/`, `docs/plans/`. No production code. |
| `tsp-intellij-researcher` | opus | Answers IntelliJ Platform API / extension-point / Gradle / CE-compatibility questions **with citations**. No code. |
| `tsp-dev` | sonnet | Implements exactly one milestone. Gets it compiling. |
| `tsp-tester` | sonnet | Writes and runs tests; reports failures verbatim. Never edits `src/main/`. |

## Skills

| Skill | When |
|---|---|
| `tsp-plugin-bootstrap` | One-time repo scaffolding + build configuration |
| `intellij-syntax-highlighting` | Lexers, highlighters, file types, `plugin.xml` |
| `typespec-language` | Token sets, `.flex` rules, test fixtures |
| `intellij-plugin-testing` | Any test, and the verify/run commands |

## Workflow

1. **Bootstrap** — run the `tsp-plugin-bootstrap` skill. `./gradlew build` and `./gradlew runIde` green before anything else.
2. **Design** — `tsp-architect` writes `docs/plans/01-*.md` (lexer + highlighter first; parser/PSI later). It delegates every API question to `tsp-intellij-researcher`.
3. **Implement** — `tsp-dev` executes one milestone; blocked on an unknown API → ask the researcher, don't invent one.
4. **Verify** — `tsp-tester` adds lexer/highlighting tests, runs `./gradlew test` and `./gradlew verifyPlugin`.
5. Repeat 2–4 per milestone.

## Build commands

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

## Environment note

The system default JDK here is **Java 26**. Current IntelliJ Platform Gradle builds expect
JDK 21. If Gradle fails on the toolchain, pin a supported JDK for this project
(`org.gradle.java.home` or a toolchain block) rather than changing the system default.
