package simpli.fyi.plugins.typespec.resolve

import com.intellij.psi.PsiPolyVariantReference
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import simpli.fyi.plugins.typespec.psi.TypeSpecModelStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamedElement
import simpli.fyi.plugins.typespec.psi.TypeSpecScalarStatement

/**
 * ADR 0011 §Context case 3, the owner's reported bug: module `shared` declares
 * `scalar VolumeUnit extends string;` and `model MetaData {}`; a second module does
 * `using Shared;` and references both — with **no** `import` anywhere connecting the two files.
 * Tiers A/B (current file, transitive import closure) see nothing; only the stub index (tier C′,
 * plan 06 M6.5c) reaches across. This is the test the deleted `TIER_C_FILE_CAP` would have
 * failed on any project past 50 `.tsp` files that mention the resolved word — hence the noise
 * scale below.
 */
class TypeSpecCrossModuleResolveTest : BasePlatformTestCase() {

    private fun tsp(path: String, text: String) = myFixture.addFileToProject(path, text)

    /**
     * 130 noise files, each mentioning `Shared` as plain text (a comment) — comfortably past the
     * deleted cap (50) — plus the real `shared` module. Regression pin: this is exactly the
     * shape the cap-based tier C used to fail, silently, on any project this size or larger.
     */
    private fun installNoiseFiles(count: Int = 130) {
        repeat(count) { i ->
            tsp(
                "noise/module-$i/decoy.tsp",
                "// Shared is mentioned here as plain text, never declared\nmodel Decoy$i {}\n",
            )
        }
    }

    // ---- 1: the owner's bug, verbatim --------------------------------------------------------

    fun testOwnerBugBothBareReferencesResolveAcrossModulesWithNoImport() {
        installNoiseFiles()
        tsp(
            "shared/shared.tsp",
            """
            namespace Shared;
            scalar VolumeUnit extends string;
            model MetaData {}
            """.trimIndent(),
        )
        val app = tsp(
            "app/app.tsp",
            """
            using Shared;
            model Measurement {
              measurementVolume: VolumeUnit;
              ...MetaData;
            }
            """.trimIndent(),
        )

        // sanity: no import statement anywhere connects app.tsp to shared.tsp.
        assertTrue((app as simpli.fyi.plugins.typespec.psi.TypeSpecFile).getImportStatements().isEmpty())

        myFixture.configureFromExistingVirtualFile(app.virtualFile)
        val text = myFixture.file.text

        val volumeUnitOffset = text.indexOf("measurementVolume: VolumeUnit") + "measurementVolume: ".length
        val volumeUnitTarget = myFixture.file.findReferenceAt(volumeUnitOffset)?.resolve()
        assertTrue(
            "expected VolumeUnit to resolve to a scalar statement, got $volumeUnitTarget",
            volumeUnitTarget is TypeSpecScalarStatement,
        )
        assertEquals("VolumeUnit", (volumeUnitTarget as TypeSpecNamedElement).name)
        assertEquals("shared.tsp", volumeUnitTarget.containingFile.name)

        val metaDataOffset = text.indexOf("...MetaData") + "...".length
        val metaDataTarget = myFixture.file.findReferenceAt(metaDataOffset)?.resolve()
        assertTrue(
            "expected MetaData to resolve to a model statement, got $metaDataTarget",
            metaDataTarget is TypeSpecModelStatement,
        )
        assertEquals("MetaData", (metaDataTarget as TypeSpecNamedElement).name)
        assertEquals("shared.tsp", metaDataTarget.containingFile.name)
    }

    // ---- 2: qualified cross-module reference, no `using` at all -----------------------------

    fun testQualifiedCrossModuleReferenceResolvesWithNoUsingAndNoImport() {
        installNoiseFiles()
        tsp(
            "shared2/shared.tsp",
            """
            namespace Shared;
            scalar VolumeUnit extends string;
            """.trimIndent(),
        )
        val app = tsp(
            "app2/app.tsp",
            """
            model Measurement {
              v: Shared.VolumeUnit;
            }
            """.trimIndent(),
        )

        assertTrue((app as simpli.fyi.plugins.typespec.psi.TypeSpecFile).getImportStatements().isEmpty())
        myFixture.configureFromExistingVirtualFile(app.virtualFile)
        val text = myFixture.file.text

        val offset = text.indexOf("Shared.VolumeUnit") + "Shared.".length
        val target = myFixture.file.findReferenceAt(offset)?.resolve()
        assertTrue(
            "expected the qualified reference to resolve to a scalar statement, got $target",
            target is TypeSpecScalarStatement,
        )
        assertEquals("VolumeUnit", (target as TypeSpecNamedElement).name)
        assertEquals("shared.tsp", target.containingFile.name)
    }

    // ---- 3: the leading `Shared` segment itself resolves to the namespace --------------------

    fun testQualifiedCrossModuleLeadingSegmentResolvesToNamespace() {
        installNoiseFiles(count = 10) // smaller — this case does not depend on scale
        tsp(
            "shared3/shared.tsp",
            "namespace Shared;\nscalar VolumeUnit extends string;\n",
        )
        val app = tsp(
            "app3/app.tsp",
            "model Measurement {\n  v: Shared.VolumeUnit;\n}\n",
        ) as simpli.fyi.plugins.typespec.psi.TypeSpecFile

        myFixture.configureFromExistingVirtualFile(app.virtualFile)
        val text = myFixture.file.text
        val offset = text.indexOf("Shared.VolumeUnit")

        val reference = myFixture.file.findReferenceAt(offset) as? PsiPolyVariantReference
        assertNotNull(reference)
        val results = reference!!.multiResolve(false).mapNotNull { it.element }
        assertTrue(
            "expected the leading 'Shared' segment to resolve to the namespace statement",
            results.any { it is simpli.fyi.plugins.typespec.psi.TypeSpecNamespaceStatement && it.name == "Shared" },
        )
    }
}
