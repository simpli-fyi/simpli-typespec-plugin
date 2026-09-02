---
name: intellij-plugin-testing
description: How to write and run tests for an IntelliJ plugin — lexer token tests, highlighting fixture tests, parsing tests, and the Gradle verification tasks (test, verifyPlugin, runIde). Use when adding any test to the plugin, setting up testData fixtures, or verifying a milestone.
---

# Testing an IntelliJ plugin

## Layout

```
src/test/kotlin/<group>/...      test classes
src/test/testData/               fixture files (.tsp, expected .txt trees)
```

Override `getTestDataPath()` to point at `src/test/testData` in `BasePlatformTestCase`
subclasses. Test class names end in `Test`.

## Test kinds, in the order you'll need them

**1. Lexer tests** — `LexerTestCase`, or drive the lexer directly and assert the
`(tokenType, text)` sequence. Fastest feedback, catches the majority of highlighting bugs.
Cover: every token category, plus unterminated string, unterminated block comment, `@@`,
backtick identifier, triple-quoted string, `#suppress`, EOF in each lexer state.

**2. Highlighting tests** — `BasePlatformTestCase`:

```kotlin
class TypeSpecHighlightingTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData"
    fun testBasic() {
        myFixture.configureByFile("basic.tsp")
        myFixture.checkHighlighting(true, false, true)
    }
}
```

Fixtures annotate expectations inline with `<warning>`/`<error>` tags; for pure coloring, the
useful assertion is "no errors and the file parses/lexes to EOF".

**3. Parsing tests** (only once a `ParserDefinition` exists) — `ParsingTestCase("", "tsp",
TypeSpecParserDefinition())`, `.tsp` input beside expected PSI-tree `.txt`. The first run
writes the expected file; **review it before committing** — a wrong tree gets frozen otherwise.

**4. File type registration** — assert `.tsp` resolves to the TypeSpec file type and that a
configured file's language is TypeSpec.

## Commands

```bash
./gradlew test
```

```bash
./gradlew verifyPlugin
```

```bash
./gradlew runIde
```

`verifyPlugin` runs the JetBrains Plugin Verifier against the configured IDEs — this is what
catches use of an API missing from IntelliJ IDEA **Community** or broken in a target version.
Treat a verifier warning about an Ultimate-only class as a build failure for this project.

`runIde` is the manual check: open a `.tsp` file in the sandbox and confirm coloring, then
switch themes (Light/Darcula) to confirm the `TextAttributesKey` fallbacks work.

## Rules

- Never modify `src/main/` to make a test pass — report the defect instead.
- Report failures with the actual assertion diff, not a summary.
- Tests that are skipped or not yet written are stated gaps, not silent omissions.
- A single generated-lexer change can shift many token offsets; when a lexer test fails,
  check the `.flex` diff first.
