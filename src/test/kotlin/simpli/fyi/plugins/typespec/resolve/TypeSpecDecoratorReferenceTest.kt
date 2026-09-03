package simpli.fyi.plugins.typespec.resolve

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.TokenType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import simpli.fyi.plugins.typespec.psi.TypeSpecDecStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecFile
import simpli.fyi.plugins.typespec.psi.TypeSpecNamedElement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamespaceStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes

/**
 * `TypeSpecDecoratorReferenceHost`/`TypeSpecDecoratorReference` — M5.6d
 * ([ADR 0009](../../../../../../../../docs/adr/0009-decorator-reference-strategy.md) option B,
 * [plan 05](../../../../../../../../docs/plans/05-import-and-decorator-navigation.md) M5.6d).
 *
 * Every fixture is built programmatically via `myFixture.addFileToProject` (same technique as
 * `TypeSpecStdLibraryTest`) so each test's exact byte offsets stay legible next to the
 * assertion — the whole point of this suite is off-by-one range correctness.
 */
class TypeSpecDecoratorReferenceTest : BasePlatformTestCase() {

    private fun tsp(path: String, text: String) = myFixture.addFileToProject(path, text)

    private fun resolveAt(file: TypeSpecFile, offset: Int): PsiPolyVariantReference? =
        file.findReferenceAt(offset) as? PsiPolyVariantReference

    private fun singleTarget(file: TypeSpecFile, offset: Int): Any? {
        val ref = resolveAt(file, offset) ?: return null
        val results = ref.multiResolve(false)
        assertEquals("expected at most one candidate at offset $offset", 1, results.size)
        return results[0].element
    }

    // =========================================================================================
    // 1. Real library import: @TypeSpec.OpenAPI.info, three segments, differing lengths
    //    (plan 05 M5.6d acceptance criterion 1 — the owner's exact reported gap)
    // =========================================================================================

    private fun installOpenApiLibrary(): TypeSpecFile {
        tsp(
            "node_modules/@typespec/openapi/package.json",
            """{"tspMain": "lib/main.tsp"}""",
        )
        return tsp(
            "node_modules/@typespec/openapi/lib/main.tsp",
            "namespace TypeSpec.OpenAPI;\nextern dec info(target: unknown, additionalInfo: unknown);\n",
        ) as TypeSpecFile
    }

    /**
     * `namespace TypeSpec.OpenAPI;` is dotted-sugar for a single statement (the same
     * `TypeSpecResolveTest` case 7 behaviour already pinned elsewhere in this suite) — segments
     * "TypeSpec" (index 0) and "OpenAPI" (index 1) therefore intentionally resolve to the SAME
     * PSI node, and only "info" (index 2) differs. This matches plan 05 M5.6d's acceptance
     * criterion verbatim ("the namespace statement, the namespace statement, and the extern dec
     * info"). Section 2 below uses independently-declared (non-dotted) namespaces to prove true
     * three-way distinctness without this sugar confound.
     */
    fun testRealLibraryImportEachSegmentOfQualifiedDecoratorResolves() {
        installOpenApiLibrary()
        val app = tsp(
            "app-openapi.tsp",
            "import \"@typespec/openapi\";\n\n@TypeSpec.OpenAPI.info(#{ version: \"1.5.1\" })\nnamespace App;\n",
        ) as TypeSpecFile
        val text = app.text
        val decoratorStart = text.indexOf("@TypeSpec.OpenAPI.info")
        assertTrue(decoratorStart >= 0)
        // "@TypeSpec.OpenAPI.info" -> @=0 TypeSpec=[1,9) .=9 OpenAPI=[10,17) .=17 info=[18,22)
        val typeSpecOffset = decoratorStart + 1
        val openApiOffset = decoratorStart + 10
        val infoOffset = decoratorStart + 18

        val typeSpecTarget = singleTarget(app, typeSpecOffset)
        val openApiTarget = singleTarget(app, openApiOffset)
        val infoTarget = singleTarget(app, infoOffset)

        assertTrue(typeSpecTarget is TypeSpecNamespaceStatement)
        assertTrue(openApiTarget is TypeSpecNamespaceStatement)
        assertTrue(infoTarget is TypeSpecDecStatement)

        assertSame(
            "dotted namespace sugar: 'TypeSpec' and 'OpenAPI' segments denote the same statement",
            typeSpecTarget,
            openApiTarget,
        )
        assertNotSame(
            "the 'info' segment must resolve to a different declaration than the namespace segments",
            typeSpecTarget,
            infoTarget,
        )
        assertEquals("info", (infoTarget as TypeSpecNamedElement).name)
        assertEquals("main.tsp", infoTarget.containingFile.name)
        assertTrue(
            "the info target must come from the imported library file, not app.tsp itself",
            infoTarget.containingFile.virtualFile?.path?.contains("node_modules/@typespec/openapi") == true,
        )
    }

    // =========================================================================================
    // 2. True per-segment distinctness: independently-declared (non-dotted) namespaces
    // =========================================================================================

    fun testThreeDistinctDeclarationsThreeDistinctSegmentTargets() {
        val app = tsp(
            "app-distinct.tsp",
            """
            namespace NsOuter {
              namespace NsInner {
                extern dec deco(target: unknown);
              }
            }
            @NsOuter.NsInner.deco
            model M {}
            """.trimIndent(),
        ) as TypeSpecFile
        val text = app.text
        val decoratorStart = text.indexOf("@NsOuter.NsInner.deco")
        assertTrue(decoratorStart >= 0)
        // @=0 NsOuter=[1,8) .=8 NsInner=[9,16) .=16 deco=[17,21)
        val outerOffset = decoratorStart + 1
        val innerOffset = decoratorStart + 9
        val decoOffset = decoratorStart + 17

        val outerTarget = singleTarget(app, outerOffset) as? TypeSpecNamedElement
        val innerTarget = singleTarget(app, innerOffset) as? TypeSpecNamedElement
        val decoTarget = singleTarget(app, decoOffset) as? TypeSpecNamedElement

        assertNotNull(outerTarget)
        assertNotNull(innerTarget)
        assertNotNull(decoTarget)
        assertEquals("NsOuter", outerTarget!!.name)
        assertEquals("NsInner", innerTarget!!.name)
        assertEquals("deco", decoTarget!!.name)

        val distinctTargets = setOf(outerTarget, innerTarget, decoTarget)
        assertEquals(
            "each of the three segments must resolve to its own distinct PSI declaration " +
                "(a range bug would make two segments collide)",
            3,
            distinctTargets.size,
        )
    }

    // =========================================================================================
    // 3. Repeated segment name: @Foo.Bar.Foo — a naive range bug landing on an identically
    //    named segment must not accidentally "pass"
    // =========================================================================================

    fun testRepeatedSegmentNameResolvesIndependently() {
        val app = tsp(
            "app-repeat.tsp",
            """
            namespace Foo {
              namespace Bar {
                extern dec Foo(target: unknown);
              }
            }
            @Foo.Bar.Foo
            model M {}
            """.trimIndent(),
        ) as TypeSpecFile
        val text = app.text
        val decoratorStart = text.indexOf("@Foo.Bar.Foo")
        assertTrue(decoratorStart >= 0)
        // @=0 Foo=[1,4) .=4 Bar=[5,8) .=8 Foo=[9,12)
        val firstFooOffset = decoratorStart + 1
        val barOffset = decoratorStart + 5
        val secondFooOffset = decoratorStart + 9

        val firstFooTarget = singleTarget(app, firstFooOffset)
        val barTarget = singleTarget(app, barOffset)
        val secondFooTarget = singleTarget(app, secondFooOffset)

        assertTrue(firstFooTarget is TypeSpecNamespaceStatement)
        assertEquals("Foo", (firstFooTarget as TypeSpecNamedElement).name)

        assertTrue(barTarget is TypeSpecNamespaceStatement)
        assertEquals("Bar", (barTarget as TypeSpecNamedElement).name)

        assertTrue(
            "the second 'Foo' segment (index 2) must resolve to the extern dec, not the " +
                "namespace named 'Foo' (index 0) even though the text is identical",
            secondFooTarget is TypeSpecDecStatement,
        )
        assertEquals("Foo", (secondFooTarget as TypeSpecNamedElement).name)
        assertNotSame(
            "the two textually-identical 'Foo' segments must resolve to different declarations",
            firstFooTarget,
            secondFooTarget,
        )
    }

    // =========================================================================================
    // 4. Boundary offsets: first and last character of every segment
    // =========================================================================================

    fun testFirstAndLastCharacterOfEverySegmentResolve() {
        val app = tsp(
            "app-boundary.tsp",
            """
            namespace A {
              namespace MiddleNamespace {
                extern dec z(target: unknown);
              }
            }
            @A.MiddleNamespace.z
            model M {}
            """.trimIndent(),
        ) as TypeSpecFile
        val text = app.text
        val decoratorStart = text.indexOf("@A.MiddleNamespace.z")
        assertTrue(decoratorStart >= 0)
        // @=0 A=[1,2) .=2 MiddleNamespace=[3,18) .=18 z=[19,20)
        val aFirst = decoratorStart + 1
        val aLast = decoratorStart + 1 // single-char segment: first == last
        val midFirst = decoratorStart + 3
        val midLast = decoratorStart + 17
        val zFirst = decoratorStart + 19
        val zLast = decoratorStart + 19

        for ((offset, expectedName) in listOf(
            aFirst to "A",
            aLast to "A",
            midFirst to "MiddleNamespace",
            midLast to "MiddleNamespace",
            zFirst to "z",
            zLast to "z",
        )) {
            val target = singleTarget(app, offset) as? TypeSpecNamedElement
            assertNotNull("no target resolved at offset $offset (expected '$expectedName')", target)
            assertEquals("wrong target resolved at offset $offset", expectedName, target!!.name)
        }
    }

    // =========================================================================================
    // 5. Offsets that must NOT carry a decorator reference: the '.' separator and the `@`/`@@`
    //    prefix. Argument-list identifiers keep their own, separate references.
    // =========================================================================================

    /**
     * Probed empirically (not assumed): [com.intellij.openapi.util.TextRange.containsOffset] is
     * documented and implemented as **inclusive on both ends**
     * (`start <= offset && offset <= end`) — the platform's standard "caret right after a token
     * still counts as being on it" contract, the same one every other reference in this plugin
     * relies on. Consequence for a `rangeInElement` built as `[segmentStart, segmentEnd)` half-open
     * (as [simpli.fyi.plugins.typespec.psi.impl.TypeSpecDecoratorReferenceHost] builds it): the
     * offset sitting exactly ON a `.` separator is `segmentEnd` of the segment BEFORE it, so it
     * resolves to that preceding segment, not to nothing. Verified directly: `@A.B.c`'s first `.`
     * (offset 2) resolves to "A"; its second `.` (offset 4) resolves to "B". The `@`/`@@` prefix
     * offsets, which sit strictly BEFORE the first segment's range, are the only offsets inside
     * the token that genuinely carry no reference — asserted below alongside the inclusive-end
     * dot behaviour, both as *intended*, not merely observed.
     */
    fun testOffsetOnDotSeparatorResolvesToPrecedingSegmentPrefixOffsetsDoNot() {
        val app = tsp(
            "app-noref.tsp",
            """
            namespace A {
              namespace B {
                extern dec c(target: unknown);
              }
            }
            @A.B.c
            model M {}
            """.trimIndent(),
        ) as TypeSpecFile
        val text = app.text
        val decoratorStart = text.indexOf("@A.B.c")
        assertTrue(decoratorStart >= 0)
        // @=0 A=[1,2) .=2 B=[3,4) .=4 c=[5,6)
        val atOffset = decoratorStart // the '@' character itself
        val firstDotOffset = decoratorStart + 2
        val secondDotOffset = decoratorStart + 4
        val pastLastCharOffset = decoratorStart + 6 // one past the whole token's own text

        assertNull("the '@' prefix character must not carry a decorator reference", resolveAt(app, atOffset))
        assertNull(
            "one past the token's own text must not carry a decorator reference",
            resolveAt(app, pastLastCharOffset),
        )

        val onFirstDot = singleTarget(app, firstDotOffset) as? TypeSpecNamedElement
        assertNotNull(
            "inclusive-end containsOffset means the first '.' resolves to the PRECEDING " +
                "segment ('A'), not to nothing",
            onFirstDot,
        )
        assertEquals("A", onFirstDot!!.name)

        val onSecondDot = singleTarget(app, secondDotOffset) as? TypeSpecNamedElement
        assertNotNull(
            "inclusive-end containsOffset means the second '.' resolves to the PRECEDING " +
                "segment ('B'), not to nothing",
            onSecondDot,
        )
        assertEquals("B", onSecondDot!!.name)

        // Sanity: an offset genuinely inside a segment DOES yield the same kind of reference.
        assertNotNull(resolveAt(app, decoratorStart + 1))
    }

    fun testOffsetInsideArgumentListYieldsNoDecoratorReference() {
        val app = tsp(
            "app-argref.tsp",
            "model Widget {}\n@doc(Widget)\nmodel M {}\n",
        ) as TypeSpecFile
        val text = app.text
        val widgetArgOffset = text.indexOf("Widget)") // inside the argument, not the decorator name

        val ref = app.findReferenceAt(widgetArgOffset)
        assertNotNull("the argument identifier keeps its own reference", ref)
        // It must resolve to the model Widget declaration, i.e. it's an ordinary identifier
        // reference, never a TypeSpecDecoratorReference from the decorator name.
        assertFalse(ref is TypeSpecDecoratorReference)
        val resolved = ref!!.resolve()
        assertTrue(resolved is TypeSpecNamedElement)
        assertEquals("Widget", (resolved as TypeSpecNamedElement).name)
    }

    // =========================================================================================
    // 6. `@@` augment form: same segment/range logic against AUGMENT_DECORATOR, several segments
    // =========================================================================================

    fun testAugmentDecoratorSeveralSegmentsResolvesSameAsDecoratorForm() {
        installOpenApiLibrary()
        val app = tsp(
            "app-augment.tsp",
            "import \"@typespec/openapi\";\n\nnamespace App;\n\n@@TypeSpec.OpenAPI.info(App, #{ version: \"1.5.1\" });\n",
        ) as TypeSpecFile
        val text = app.text
        val decoratorStart = text.indexOf("@@TypeSpec.OpenAPI.info")
        assertTrue(decoratorStart >= 0)
        // @@=0..2 TypeSpec=[2,10) .=10 OpenAPI=[11,18) .=18 info=[19,23)
        val typeSpecOffset = decoratorStart + 2
        val openApiOffset = decoratorStart + 11
        val infoOffset = decoratorStart + 19

        val typeSpecTarget = singleTarget(app, typeSpecOffset)
        val openApiTarget = singleTarget(app, openApiOffset)
        val infoTarget = singleTarget(app, infoOffset)

        assertTrue(typeSpecTarget is TypeSpecNamespaceStatement)
        assertSame(typeSpecTarget, openApiTarget)
        assertTrue(infoTarget is TypeSpecDecStatement)
        assertEquals("info", (infoTarget as TypeSpecNamedElement).name)

        // Prefix boundary: offset 0 and 1 (both '@' characters) must not carry a reference.
        assertNull(resolveAt(app, decoratorStart))
        assertNull(resolveAt(app, decoratorStart + 1))
    }

    // =========================================================================================
    // 7. Two-segment form
    // =========================================================================================

    fun testTwoSegmentDecoratorBothSegmentsResolve() {
        val app = tsp(
            "app-twoseg.tsp",
            """
            namespace Foo {
              extern dec Bar(target: unknown);
            }
            @Foo.Bar
            model M {}
            """.trimIndent(),
        ) as TypeSpecFile
        val text = app.text
        val decoratorStart = text.indexOf("@Foo.Bar")
        assertTrue(decoratorStart >= 0)
        val fooOffset = decoratorStart + 1
        val barOffset = decoratorStart + 5

        val fooTarget = singleTarget(app, fooOffset)
        val barTarget = singleTarget(app, barOffset)

        assertTrue(fooTarget is TypeSpecNamespaceStatement)
        assertEquals("Foo", (fooTarget as TypeSpecNamedElement).name)
        assertTrue(barTarget is TypeSpecDecStatement)
        assertEquals("Bar", (barTarget as TypeSpecNamedElement).name)
    }

    // =========================================================================================
    // 8. Single-segment @doc resolving via the (implicit, M5.6g) std library
    // =========================================================================================

    fun testSingleSegmentDocResolvesViaStdLibrary() {
        tsp(
            "node_modules/@typespec/compiler/package.json",
            """{"tspMain": "lib/std/main.tsp"}""",
        )
        tsp(
            "node_modules/@typespec/compiler/lib/std/main.tsp",
            "namespace TypeSpec;\nextern dec doc(target: unknown, value: unknown);\n",
        )
        tsp("node_modules/@typespec/compiler/lib/intrinsics.tsp", "namespace TypeSpec;\n")
        val app = tsp("app-doc.tsp", "@doc(\"hello\")\nmodel M {}\n") as TypeSpecFile

        val text = app.text
        val docOffset = text.indexOf("@doc") + 1
        val target = singleTarget(app, docOffset)
        assertTrue("expected @doc to resolve via the implicit std library, got $target", target is TypeSpecDecStatement)
        assertEquals("doc", (target as TypeSpecNamedElement).name)
        assertEquals("main.tsp", target.containingFile.name)
    }

    // =========================================================================================
    // 9. Unknown decorator: reference exists, soft, unresolved, no error highlighting
    // =========================================================================================

    fun testUnknownDecoratorSoftUnresolvedNoErrorHighlighting() {
        val app = tsp("app-unknown.tsp", "@totallyUnknownDecoratorXyz\nmodel M {}\n") as TypeSpecFile
        val text = app.text
        val offset = text.indexOf("totallyUnknownDecoratorXyz")

        val ref = app.findReferenceAt(offset)
        assertNotNull("a reference must exist even though the decorator name is unknown", ref)
        assertTrue(ref is PsiReferenceBase<*>)
        assertTrue("an unknown decorator reference must be soft", (ref as PsiReferenceBase<*>).isSoft)

        val poly = ref as PsiPolyVariantReference
        assertEquals("unknown decorator must resolve to nothing", 0, poly.multiResolve(false).size)
        assertNull(ref.resolve())

        myFixture.configureByText("app-unknown-highlight.tsp", "@totallyUnknownDecoratorXyz\nmodel M {}\n")
        myFixture.checkHighlighting(true, false, true)
    }

    // =========================================================================================
    // 10. Highlighting unchanged: one DECORATOR token spans the whole @Ns.name text
    // =========================================================================================

    fun testHighlightingStillYieldsOneDecoratorTokenSpanningWholeText() {
        val text = "@TypeSpec.OpenAPI.info\nmodel M {}\n"
        val psiFile = myFixture.configureByText("app-hl.tsp", text)
        val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(
            psiFile.virtualFile,
            EditorColorsManager.getInstance().globalScheme,
            project,
        )
        highlighter.setText(text)
        val it = highlighter.createIterator(0)
        assertFalse(it.atEnd())
        while (it.tokenType == TokenType.WHITE_SPACE) it.advance()
        assertEquals(TypeSpecTokenTypes.DECORATOR, it.tokenType)
        assertEquals("@TypeSpec.OpenAPI.info", text.substring(it.start, it.end))
        it.advance()
        // the very next non-whitespace token must NOT also be a DECORATOR fragment — confirms
        // the token was not split into several adjacent DECORATOR/segment tokens.
        while (it.tokenType == TokenType.WHITE_SPACE) it.advance()
        assertFalse(TypeSpecTokenTypes.DECORATOR === it.tokenType)
    }

    // =========================================================================================
    // 11. `check(startOffsetInParent == 0)` probe: a decorated declaration with several stacked
    //     decorator_application nodes in front of it. Each decorator_application is its own PSI
    //     node whose own first child is its own DECORATOR token — the repetition happens at the
    //     PARENT (e.g. model_statement's decorator_application*), never inside a single
    //     decorator_application node itself, so this must resolve cleanly, never throw.
    // =========================================================================================

    fun testMultipleStackedDecoratorsEachStillHaveTokenAsOwnFirstChild() {
        val app = tsp(
            "app-stacked.tsp",
            """
            namespace Foo {
              extern dec first(target: unknown);
              extern dec second(target: unknown);
            }
            @Foo.first
            @Foo.second
            model M {}
            """.trimIndent(),
        ) as TypeSpecFile
        val text = app.text
        val firstOffset = text.indexOf("@Foo.first") + 5 // "first"
        val secondOffset = text.indexOf("@Foo.second") + 5 // "second"

        var threw: Throwable? = null
        var firstTarget: Any? = null
        var secondTarget: Any? = null
        try {
            firstTarget = singleTarget(app, firstOffset)
            secondTarget = singleTarget(app, secondOffset)
        } catch (t: Throwable) {
            threw = t
        }
        assertNull("resolving segments of stacked decorator_application nodes must not throw " +
            "(the host's startOffsetInParent==0 check() could only fire if the token were not " +
            "each node's own first child)", threw)
        assertTrue(firstTarget is TypeSpecDecStatement)
        assertEquals("first", (firstTarget as TypeSpecNamedElement).name)
        assertTrue(secondTarget is TypeSpecDecStatement)
        assertEquals("second", (secondTarget as TypeSpecNamedElement).name)
        assertNotSame(firstTarget, secondTarget)
    }
}
