package simpli.fyi.plugins.typespec.stubs

import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import simpli.fyi.plugins.typespec.psi.TypeSpecNamedElement

/**
 * "Which declarations are named X?" over every stub-indexed `.tsp` source in the project (plan
 * 06 M6.5b, ADR 0011 D3). Keyed by the stub's already backtick-stripped **simple** name — never a
 * namespace-qualified key: bare-name lookup is required regardless (a `using` brings names in
 * unqualified), and TypeSpec namespaces merge across files, so a qualified key would not even be
 * unique. The qualified question ("is there a `VolumeUnit` under `Shared`?") is answered by
 * comparing [TypeSpecDeclStub.namespacePath] on each hit, in memory, with no AST load — see
 * [TypeSpecStubQueries.declarationsNamed].
 *
 * `node_modules` never reaches this index at all: [TypeSpecDeclStubElementType.indexStub] only
 * runs for stubs that exist, and [TypeSpecFileElementType.shouldBuildStubFor] never builds one
 * for a `node_modules` file in the first place (ADR 0011 D4).
 */
class TypeSpecDeclarationNameIndex : StringStubIndexExtension<TypeSpecNamedElement>() {

    override fun getKey(): StubIndexKey<String, TypeSpecNamedElement> = KEY

    override fun getVersion(): Int = TypeSpecStubVersion.VERSION

    companion object {
        val KEY: StubIndexKey<String, TypeSpecNamedElement> =
            StubIndexKey.createIndexKey("tsp.decl.name")
    }
}
