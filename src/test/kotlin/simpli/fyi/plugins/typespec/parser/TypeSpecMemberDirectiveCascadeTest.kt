package simpli.fyi.plugins.typespec.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import simpli.fyi.plugins.typespec.TypeSpecFileType
import simpli.fyi.plugins.typespec.psi.TypeSpecDecoratorApplication
import simpli.fyi.plugins.typespec.psi.TypeSpecDirectiveStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecFile
import simpli.fyi.plugins.typespec.psi.TypeSpecModelStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamedElement
import simpli.fyi.plugins.typespec.psi.TypeSpecScalarStatement

/**
 * Regression for `50ec08f` (the model-body cascade fix) and `4c4c191` (the
 * `enum`/`union`/`interface`/`model_spread` member-annotation fix). The *dangerous* part of the
 * original bug was not "a member-level directive fails to parse" but "the failure propagates
 * past the enclosing declaration's own closing brace and silently drops every top-level
 * declaration after it, because the enclosing `namespace Foo;` is the blockless dotted form
 * (body = `top_level_item_*`, no closing brace for recovery to resynchronise on)". A fixture
 * that only checks "the directive itself parses clean" would not catch that -- this class
 * specifically asserts the *later* declarations are still present, for every body kind.
 *
 * `4c4c191` is grounded in `parser.js`: `parseList` calls one shared `parseAnnotations()`
 * *before* dispatching to the item parser, collecting directives and decorators together in any
 * order, any number of each, and attaching them to the resulting item. `TypeSpec.bnf`'s fix adds
 * `private member_annotation_ ::= decorator_application | directive_statement` as the leading
 * prefix of `model_property`, `model_spread`, `enum_member`, `union_variant`, and
 * `interface_operation`. This class now asserts the POSITIVE (fixed) behaviour for all five
 * member-annotation sites, plus every annotation ordering upstream's shared collector must
 * accept: directive-then-decorators, decorators-then-directive, decorators-around-a-directive,
 * two directives on one member, and (least-covered site) a directive on a `model_spread`.
 */
class TypeSpecMemberDirectiveCascadeTest : BasePlatformTestCase() {

    private fun parse(name: String, text: String): TypeSpecFile =
        PsiFileFactory.getInstance(project)
            .createFileFromText(name, TypeSpecFileType.INSTANCE, text) as TypeSpecFile

    private fun assertClean(file: TypeSpecFile) {
        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertTrue(
            "expected zero PsiErrorElements, got: ${errors.map { it.errorDescription }}",
            errors.isEmpty(),
        )
    }

    // ---- the original fix: model bodies, blockless dotted namespace -------------------------

    /**
     * Mirrors the owner's real repro shape (ADR 0011 §Context case 3 / `TypeSpecCrossModuleResolveTest`):
     * a blockless dotted `namespace A.B.C;`, a model with a member-level `#deprecated` directive,
     * and further declarations (`scalar VolumeUnit`, `model MetaData`) textually AFTER it. All
     * three declarations must still be present and zero `PsiErrorElement`/unclaimed-leaf.
     */
    fun testDeclarationsAfterMemberDirectiveSurviveInBlocklessNamespace() {
        val text = """
            namespace A.B.C;

            model Widget {
              #deprecated "use Gadget instead"
              @key id: string;
            }

            scalar VolumeUnit extends string;

            model MetaData {}
        """.trimIndent()
        val file = parse("Cascade.tsp", text)
        assertClean(file)

        // `getTopLevelDeclarations()` only walks the FILE's direct children (ADR 0004 D7.4: it
        // deliberately "stops at the blockless namespace's ';'"), and for the blockless dotted
        // form used here, VolumeUnit/MetaData are children of the `namespace_statement` itself,
        // not of the file. Search the whole tree instead -- what this test needs to assert is
        // that they are still real, correctly-typed PSI nodes ANYWHERE in the tree, not merely
        // that some accessor's own file-only scope happens to include them.
        val decls = PsiTreeUtil.findChildrenOfType(file, TypeSpecNamedElement::class.java).toList()
        val names = decls.map { it.name }
        assertTrue(
            "expected VolumeUnit and MetaData (declared AFTER the directive-bearing model) to " +
                "still be in the declaration table, got: $names",
            names.containsAll(listOf("VolumeUnit", "MetaData")),
        )

        val volumeUnit = decls.firstOrNull { it.name == "VolumeUnit" }
        assertTrue(
            "VolumeUnit must still be a real TypeSpecScalarStatement, not swallowed as a leaf",
            volumeUnit is TypeSpecScalarStatement,
        )
        val metaData = decls.firstOrNull { it.name == "MetaData" }
        assertTrue(
            "MetaData must still be a real TypeSpecModelStatement, not swallowed as a leaf",
            metaData is TypeSpecModelStatement,
        )
    }

    // ---- 4c4c191: enum/union/interface member-level directives now cascade-clean ------------

    private fun assertCascadeFixed(bodyKind: String, text: String) {
        val file = parse("Cascade$bodyKind.tsp", text)
        assertClean(file)
        val decls = PsiTreeUtil.findChildrenOfType(file, TypeSpecNamedElement::class.java).toList()
        val names = decls.map { it.name }
        assertTrue(
            "$bodyKind: expected Trailing (declared after the directive-bearing body) to " +
                "survive in the declaration table, got: $names",
            names.contains("Trailing"),
        )
    }

    /**
     * Regression for `4c4c191`: a member-level `#deprecated` directive inside an `enum` body now
     * parses clean via `enum_member`'s `member_annotation_*` prefix, and `model Trailing {}`
     * (declared after the enum) survives in the declaration table.
     */
    fun testEnumMemberDirectiveParsesCleanAndDeclarationsSurvive() {
        assertCascadeFixed(
            "Enum",
            """
            namespace A.B.C;

            enum Status {
              #deprecated "old"
              Active,
              Inactive
            }

            model Trailing {}
            """.trimIndent(),
        )
    }

    /** Same fix, `union` bodies -- `union_variant`'s own `member_annotation_*` prefix. */
    fun testUnionMemberDirectiveParsesCleanAndDeclarationsSurvive() {
        assertCascadeFixed(
            "Union",
            """
            namespace A.B.C;

            union Status {
              #deprecated "old"
              active: string,
              inactive: string
            }

            model Trailing {}
            """.trimIndent(),
        )
    }

    /** Same fix, `interface` bodies -- `interface_operation`'s own `member_annotation_*` prefix. */
    fun testInterfaceMemberDirectiveParsesCleanAndDeclarationsSurvive() {
        assertCascadeFixed(
            "Interface",
            """
            namespace A.B.C;

            interface Store {
              #deprecated "old"
              op get(): string;
            }

            model Trailing {}
            """.trimIndent(),
        )
    }

    // ---- 4c4c191: decorators interleaved with a member directive, in any order ---------------

    /**
     * Regression for `4c4c191`: minimal repro of the real re-synced-corpus shape
     * (`corpus/real/common/lima.tsp`, found re-syncing 2026-09-03). `model_property`'s
     * `member_annotation_*` prefix now collects decorators and directives together, in any order
     * -- so all three decorators (`@minLength`, `@maxLength`, `@field`) survive alongside the
     * directive, none dropped as unclaimed leaves.
     */
    fun testDecoratorsInterleavedWithMemberDirectiveAllSurvive() {
        val text = """
            model Widget {
              @minLength(0)
              @maxLength(40)
              #deprecated "legacy field"
              @field(70)
              name: string;
            }
        """.trimIndent()
        val file = parse("InterleavedDirective.tsp", text)
        assertClean(file)

        val decoratorApplications = PsiTreeUtil.findChildrenOfType(
            file,
            TypeSpecDecoratorApplication::class.java,
        )
        assertEquals(
            "expected all 3 decorators (@minLength, @maxLength, @field) to survive as " +
                "DECORATOR_APPLICATIONs",
            3,
            decoratorApplications.size,
        )
        val directives = PsiTreeUtil.findChildrenOfType(file, TypeSpecDirectiveStatement::class.java)
        assertEquals(1, directives.size)
    }

    // ---- annotation-ordering sweep: upstream accepts ANY order, ANY count -------------------

    private fun assertOrderingAccepted(
        label: String,
        text: String,
        expectedDecorators: Int,
        expectedDirectives: Int,
    ) {
        val file = parse("Ordering$label.tsp", text)
        assertClean(file)
        val decoratorApplications = PsiTreeUtil.findChildrenOfType(
            file,
            TypeSpecDecoratorApplication::class.java,
        )
        val directives = PsiTreeUtil.findChildrenOfType(file, TypeSpecDirectiveStatement::class.java)
        assertEquals(
            "$label: expected $expectedDecorators DECORATOR_APPLICATION(s), got " +
                "${decoratorApplications.size}",
            expectedDecorators,
            decoratorApplications.size,
        )
        assertEquals(
            "$label: expected $expectedDirectives DIRECTIVE_STATEMENT(s), got ${directives.size}",
            expectedDirectives,
            directives.size,
        )
    }

    /** `#directive` then decorators then the property -- directive-first ordering. */
    fun testDirectiveThenDecoratorsThenProperty() {
        assertOrderingAccepted(
            "DirectiveThenDecorators",
            """
            model Widget {
              #deprecated "old"
              @a
              @b
              name: string;
            }
            """.trimIndent(),
            expectedDecorators = 2,
            expectedDirectives = 1,
        )
    }

    /** A decorator, then the directive, then another decorator -- directive sandwiched. */
    fun testDecoratorDirectiveDecoratorSandwiched() {
        assertOrderingAccepted(
            "DecoratorDirectiveDecorator",
            """
            model Widget {
              @a
              #deprecated "old"
              @b
              name: string;
            }
            """.trimIndent(),
            expectedDecorators = 2,
            expectedDirectives = 1,
        )
    }

    /** Both decorators before the directive -- decorators-first ordering (the original bug's shape). */
    fun testDecoratorsThenDirectiveThenProperty() {
        assertOrderingAccepted(
            "DecoratorsThenDirective",
            """
            model Widget {
              @a
              @b
              #deprecated "old"
              name: string;
            }
            """.trimIndent(),
            expectedDecorators = 2,
            expectedDirectives = 1,
        )
    }

    /** Two directives on one member, no decorators. */
    fun testTwoDirectivesOnOneMember() {
        assertOrderingAccepted(
            "TwoDirectives",
            """
            model Widget {
              #deprecated "old"
              #suppress "some-rule" "reason"
              name: string;
            }
            """.trimIndent(),
            expectedDecorators = 0,
            expectedDirectives = 2,
        )
    }

    /**
     * A directive on a `model_spread` (`#d ...Foo;`). `model_spread` gained the
     * `member_annotation_*` prefix in `4c4c191` (a NEW sequence element, with the pin shifting
     * from position 1 to position 2) and has the least existing coverage of the five
     * `member_annotation_*` sites -- this is the case the task explicitly calls out to check.
     */
    fun testDirectiveOnModelSpread() {
        assertOrderingAccepted(
            "DirectiveOnSpread",
            """
            model Widget {
              #deprecated "old"
              ...Foo;
            }
            """.trimIndent(),
            expectedDecorators = 0,
            expectedDirectives = 1,
        )
    }

    /** A decorator AND a directive on a `model_spread`, decorator-first. */
    fun testDecoratorAndDirectiveOnModelSpread() {
        assertOrderingAccepted(
            "DecoratorAndDirectiveOnSpread",
            """
            model Widget {
              @a
              #deprecated "old"
              ...Foo;
            }
            """.trimIndent(),
            expectedDecorators = 1,
            expectedDirectives = 1,
        )
    }
}
