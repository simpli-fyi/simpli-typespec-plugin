package simpli.fyi.plugins.typespec.stubs

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.StubBuilder
import com.intellij.psi.tree.IStubFileElementType
import simpli.fyi.plugins.typespec.TypeSpecLanguage

/**
 * Replaces the plain `IFileElementType` [simpli.fyi.plugins.typespec.psi.TypeSpecElementTypes]
 * used to hold for `.tsp` files (plan 06 M6.5a) — same field, same name, same instance-identity
 * contract as before: [simpli.fyi.plugins.typespec.parser.TypeSpecParserDefinition.getFileNodeType]
 * is untouched, since [IStubFileElementType] IS-A `IFileElementType`.
 */
class TypeSpecFileElementType : IStubFileElementType<TypeSpecFileStub>(TypeSpecLanguage.INSTANCE) {

    override fun getStubVersion(): Int = TypeSpecStubVersion.VERSION

    override fun getExternalId(): String = "typespec.FILE"

    override fun getBuilder(): StubBuilder = TypeSpecStubBuilder()

    /**
     * ADR 0011 D4: excluded at **build** time, not only at query time — no stub is ever built
     * for a `node_modules` file. [TypeSpecNodeModules] is the single predicate shared with the
     * stub query scope (from M6.5b/c onward) so the two can never drift apart.
     */
    override fun shouldBuildStubFor(file: VirtualFile): Boolean = !TypeSpecNodeModules.isUnder(file)
}
