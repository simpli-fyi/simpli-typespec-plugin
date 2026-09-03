<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Simpli TypeSpec Changelog

## [Unreleased]

First release. TypeSpec (`.tsp`) language support for IntelliJ IDEA Community Edition.

### Added

- Syntax highlighting for the full TypeSpec language, with every colour configurable
  under *Settings | Editor | Color Scheme | TypeSpec*.
- Go to declaration. Ctrl/Cmd-click a type to jump to it — in the same file, across
  files, across modules, and into imported libraries under `node_modules`. Names from
  the TypeSpec standard library resolve without an explicit import, as the compiler
  does.
- Find usages for models, operations, interfaces, enums, unions, aliases, scalars and
  namespaces.
- Navigable imports: `import "../shared/common.tsp"` and `import "@typespec/openapi"`
  both jump to the file they name.
- Navigable decorator names, one segment at a time — in `@TypeSpec.OpenAPI.info`, each
  of `TypeSpec`, `OpenAPI` and `info` goes to its own declaration.
- Editor conveniences: comment/uncomment, brace matching, quote handling, and
  spellchecking of comments and strings.
