package simpli.fyi.plugins.typespec.stubs

import com.intellij.psi.stubs.IStubElementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADR 0011 D6 — "forgetting a [TypeSpecStubVersion.VERSION] bump does not fail the build or any
 * test... it corrupts a *user's* on-disk index." This test cannot detect every D6 trigger (see
 * KDoc on each assertion below for what it does and does not catch); it pins the two triggers
 * that are mechanically checkable without re-deriving the whole bump checklist: the **set** of
 * stubbed element types ([TypeSpecStubTypes], D6 item 2) and their **external IDs** (D6 item 3),
 * against the version number current at the time this test was written.
 *
 * If a future change adds/removes a stubbed rule or renames a holder field/`externalIdPrefix`
 * without bumping [TypeSpecStubVersion.VERSION], this test fails — forcing whoever made the
 * change to look at this file and, per D6, bump the version deliberately rather than silently.
 * If they *do* bump the version, they are expected to update [expectedVersion] here in the same
 * commit — the test failing is the intended signal, not a bug in the test.
 */
class TypeSpecStubVersionGuardTest {

    /**
     * Bump alongside [TypeSpecStubVersion.VERSION] — this is the guard's own trigger, deliberately
     * a plain literal rather than a reference to the constant, so the test cannot pass merely
     * because the production code and this file happen to agree by construction; it needs BOTH
     * to be updated by a human in the same commit.
     */
    private val expectedVersion = 1

    /** D6 item 2: the exact set of stubbed rules (`TypeSpec.bnf`'s `stubClass=` set). */
    private val expectedStubbedTypeNames = setOf(
        "NAMESPACE_STATEMENT",
        "MODEL_STATEMENT",
        "OP_STATEMENT",
        "INTERFACE_STATEMENT",
        "ENUM_STATEMENT",
        "UNION_STATEMENT",
        "ALIAS_STATEMENT",
        "SCALAR_STATEMENT",
        "DEC_STATEMENT",
        "FN_STATEMENT",
    )

    /** D6 item 3: `externalIdPrefix="tsp."` (plugin.xml) + each holder field's own name. */
    private fun expectedExternalId(fieldName: String) = "tsp.$fieldName"

    @Test
    fun `stub version matches this test's pinned expectation`() {
        assertEquals(
            "TypeSpecStubVersion.VERSION changed without updating this guard's pinned " +
                "expectation — per ADR 0011 D6, update expectedVersion here in the SAME commit " +
                "as any deliberate version bump; if this fired unexpectedly, the version was " +
                "bumped (or not) without reviewing this checklist",
            expectedVersion,
            TypeSpecStubVersion.VERSION,
        )
    }

    /**
     * Catches: a `TypeSpec.bnf` rule gaining or losing `stubClass=` (D6 item 2) without a version
     * bump — reflected here as [TypeSpecStubTypes]'s declared field set changing.
     *
     * Does NOT catch: a stub class gaining/losing/reordering a *serialized field* (D6 item 1), a
     * change to what `shouldCreateStub`/`skipChildProcessingWhenBuildingStubs`/
     * `shouldBuildStubFor` consider (D6 item 4), a change to what a stored value *means* — e.g.
     * backtick-stripping or the namespace-path computation (D6 item 5) — or a grammar change that
     * reshapes a stubbed rule's subtree without changing the rule set itself (D6 item 6). Those
     * are semantic changes with no mechanically checkable fingerprint in this codebase; they rely
     * on the human-authored checklist in [TypeSpecStubVersion]'s KDoc.
     */
    @Test
    fun `stubbed element type set is pinned`() {
        val fields = TypeSpecStubTypes::class.java.declaredFields
            .filter { IStubElementType::class.java.isAssignableFrom(it.type) }
        val actualNames = fields.map { it.name }.toSet()
        assertEquals(
            "the set of stubbed element types (TypeSpecStubTypes' fields) changed — this is an " +
                "ADR 0011 D6 item-2 stub-version bump; update this test's expectation AND bump " +
                "TypeSpecStubVersion.VERSION in the same commit",
            expectedStubbedTypeNames,
            actualNames,
        )
    }

    /**
     * Catches: a rename of a [TypeSpecStubTypes] field, or of the `externalIdPrefix` this
     * assumes (`"tsp."`, plugin.xml) — either changes an external ID (D6 item 3) without
     * necessarily changing the type-name set the previous assertion pins.
     *
     * Does NOT catch: `TypeSpecFileElementType.getExternalId()` (`"typespec.FILE"`) drifting —
     * that external ID has no field-reflection fingerprint to pin against; it is asserted
     * directly below instead, so it is covered, just by a different mechanism than reflection.
     */
    @Test
    fun `external ids are externalIdPrefix plus field name`() {
        val fields = TypeSpecStubTypes::class.java.declaredFields
            .filter { IStubElementType::class.java.isAssignableFrom(it.type) }
        assertTrue("expected the 10 stubbed fields to be found by reflection", fields.isNotEmpty())
        for (field in fields) {
            val value = field.get(null) as IStubElementType<*, *>
            assertEquals(
                "external ID for field '${field.name}' does not match externalIdPrefix + field name",
                expectedExternalId(field.name),
                value.externalId,
            )
        }
    }

    @Test
    fun `file element type external id and version are pinned`() {
        val fileElementType = TypeSpecFileElementType()
        assertEquals("typespec.FILE", fileElementType.externalId)
        assertEquals(
            "the file stub version must be the same single constant as the declaration index",
            TypeSpecStubVersion.VERSION,
            fileElementType.stubVersion,
        )
    }

    @Test
    fun `declaration name index version matches stub version`() {
        assertEquals(
            "TypeSpecDeclarationNameIndex.getVersion() must stay wired to the single shared " +
                "TypeSpecStubVersion constant (ADR 0011 D6) — a drift here is a corrupted-index " +
                "bug even if TypeSpecStubVersion.VERSION itself is correct",
            TypeSpecStubVersion.VERSION,
            TypeSpecDeclarationNameIndex().version,
        )
    }
}
