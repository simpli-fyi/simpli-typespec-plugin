package simpli.fyi.plugins.typespec.parser

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import simpli.fyi.plugins.typespec.TypeSpecFileType
import simpli.fyi.plugins.typespec.psi.TypeSpecFile
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenSets
import simpli.fyi.plugins.typespec.psi.TypeSpecTypes
import java.io.File

/**
 * The corpus-driven acceptance oracle (ADR 0007, plan 04 §M6a). Walks every `.tsp` file vendored
 * under `src/test/testData/corpus/` (never an absolute path — ADR 0007 D3) and asserts the two
 * properties of ADR 0007 D2 for each: no [PsiErrorElement], and no *unclaimed* leaf token.
 *
 * The second property is the one that actually matters here. ADR 0006 D6's `bad_*_token_`
 * recovery rules are `private` fallback alternatives, not `recoverWhile` — an unrecognised
 * top-level construct is swallowed one token at a time with **zero** `PsiErrorElement`s
 * (`const x = 5;` is the canonical repro, pinned by [testUnclaimedLeafCatchesConstStatement]
 * below). A swallowed token surfaces as a bare leaf child of [TypeSpecFile] itself (top-level
 * swallow) or of a body container — `MODEL_BODY` / `INTERFACE_BODY` / `ENUM_BODY` / `UNION_BODY`
 * — instead of being wrapped in a member rule (`MODEL_PROPERTY`, `INTERFACE_OPERATION`, …). Every
 * legitimately-parsed leaf is wrapped in some more specific rule below those containers, so
 * checking a leaf's *immediate* parent against exactly that set is precise and requires no
 * grammar change (do not make `bad_*_token_` public to make this easier — ADR 0007 D2's
 * implementation note).
 *
 * As of M6f (plan 04 §M6f) the ratchet is gone: every corpus file must satisfy both properties
 * unconditionally. There is no allowlist and no stored per-file categorisation to go stale — a
 * failure report is generated fresh from the actual [PsiErrorElement]s / unclaimed leaves observed
 * on *this* run, never from a label written on an earlier run (that staleness is exactly what
 * cost real time under the old `BASELINE.txt` scheme; see the M6b/M6c tsp-tester reports).
 */
class TypeSpecCorpusTest : BasePlatformTestCase() {

    private val corpusRoot: File
        get() = File("src/test/testData/corpus").absoluteFile

    // ------------------------------------------------------------------
    // Property (2) in isolation, per ADR 0007 D2's own admonition: a corpus
    // test that only checks "zero PsiErrorElement" is a false-green
    // generator for exactly the construct class this exists to catch.
    // ------------------------------------------------------------------
    fun testUnclaimedLeafCatchesConstStatement() {
        val file = createTypeSpecFile("ConstRepro.tsp", "const x = 5;")
        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertTrue(
            "expected zero PsiErrorElements for 'const x = 5;' (ADR 0007 §Context/4) -- " +
                "if this now fails, the grammar has changed and this regression pin needs updating, " +
                "not the assertion",
            errors.isEmpty(),
        )
        val unclaimed = findUnclaimedLeaves(file)
        assertTrue(
            "'const x = 5;' must be caught by the unclaimed-leaf property (ADR 0007 D2) -- " +
                "it produced ${unclaimed.size} unclaimed leaves in this build; if this is 0, the " +
                "harness has stopped detecting the silent-swallow failure mode it exists for",
            unclaimed.isNotEmpty(),
        )
    }

    fun testCorpusVendored() {
        assertTrue("corpus root missing: $corpusRoot", corpusRoot.isDirectory)
        val files = corpusFiles()
        assertTrue(
            "corpus/ is empty -- an empty corpus must fail loudly, never skip (ADR 0007 D3)",
            files.isNotEmpty(),
        )
    }

    /**
     * The absolute gate (M6f, plan 04 §M6f). No allowlist: every corpus file must satisfy both
     * ADR 0007 D2 properties. On failure the message names the offending file(s) and shows the
     * actual observed [PsiErrorElement] text/position or unclaimed-leaf text/position -- enough to
     * diagnose without re-running by hand. Never a stored categorisation (that was `BASELINE.txt`'s
     * failure mode: a stale label naming a row that no longer applied).
     */
    fun testCorpusMatchesBaseline() {
        val files = corpusFiles()
        assertTrue(
            "corpus/ is empty -- an empty corpus must fail loudly, never skip (ADR 0007 D3)",
            files.isNotEmpty(),
        )

        val failures = mutableListOf<String>()
        for (f in files) {
            val relPath = f.relativeTo(corpusRoot).path.replace(File.separatorChar, '/')
            val text = f.readText()
            val psiFile = createTypeSpecFile(f.name, text)
            val errors = PsiTreeUtil.findChildrenOfType(psiFile, PsiErrorElement::class.java)
            val unclaimed = findUnclaimedLeaves(psiFile)
            if (errors.isEmpty() && unclaimed.isEmpty()) continue

            val detail = StringBuilder()
            errors.take(5).forEach { err ->
                val line = 1 + text.substring(0, err.textOffset.coerceAtMost(text.length)).count { it == '\n' }
                detail.append(
                    "      PsiErrorElement at offset ${err.textOffset} (line $line): " +
                        "${err.errorDescription} near ${snippet(text, err.textOffset)}\n",
                )
            }
            if (errors.size > 5) detail.append("      ... and ${errors.size - 5} more PsiErrorElement(s)\n")
            unclaimed.take(5).forEach { leaf ->
                val offset = leaf.textOffset
                val line = 1 + text.substring(0, offset.coerceAtMost(text.length)).count { it == '\n' }
                detail.append(
                    "      unclaimed leaf at offset $offset (line $line): " +
                        "'${leaf.text}' near ${snippet(text, offset)}\n",
                )
            }
            if (unclaimed.size > 5) detail.append("      ... and ${unclaimed.size - 5} more unclaimed leaf/leaves\n")

            failures += "  $relPath (${errors.size} PsiErrorElement(s), ${unclaimed.size} unclaimed leaf/leaves):\n$detail"
        }

        assertTrue(
            "\n${failures.size} corpus file(s) fail ADR 0007 D2 (no allowlist -- every corpus file " +
                "must pass both properties, plan 04 §M6f):\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    /** ~20 characters of surrounding source, single-lined, for a failure message. */
    private fun snippet(text: String, offset: Int): String {
        val start = (offset - 10).coerceAtLeast(0)
        val end = (offset + 10).coerceAtMost(text.length)
        return "\"" + text.substring(start, end).replace("\n", "\\n") + "\""
    }

    /**
     * ADR 0007 D4.2 / the coordinator's follow-up: `ANONYMISATION.md` keys its rename map by
     * `hash(source)`, never by the plaintext source name (see `tools/corpus-sync/anonymise.py`'s
     * `load_map`), so this test cannot simply grep for a hard-coded list of forbidden words --
     * that list would itself be the leak. Instead it re-derives the same hash every committed
     * file's candidate words and checks membership against the map's own hash-key set: if a word
     * anywhere in scope hashes to a value the map recognises as a real source-side identifier,
     * that word *is* (or is a casing variant of) a piece of the owner's schema vocabulary and its
     * presence in a committed file is exactly the leak ADR 0007 exists to prevent. The failure
     * message names only the file and the hash, never the offending word.
     *
     * Scope is deliberately wider than the `corpus/real` `.tsp` tree: the leak this test was extended for
     * ([coordinator follow-up]) was in `ANONYMISATION.md` itself, a file the original version of
     * this test never looked at.
     */
    fun testCorpusRealHasNoDomainLeakage() {
        val realRoot = File(corpusRoot, "real")
        assertTrue("corpus/real missing: $realRoot", realRoot.isDirectory)

        val mapHashes = mapHashKeys()
        assertTrue("ANONYMISATION.md's rename-map block is empty or missing", mapHashes.isNotEmpty())

        // Explicit, already-public (ADR 0007's own text names the private repo's path) company
        // identity terms that never appear inside the .tsp schema itself, so they can never be
        // caught by the hash-based check below (which only knows hashes of words actually seen
        // in the owner's .tsp corpus). Kept as a literal belt-and-braces check.
        val explicitForbidden = listOf("Airlines", "airlines", "Puenktlich", "puenktlich", "hansa", "Hansa")

        // Common English words that legitimately collide with a mapped schema term (e.g. the
        // property `type` -> `kind`) and appear constantly in this tool's own prose ("the type of
        // match", "compute the hash", ...). Finding one of these in documentation text carries no
        // information about the owner's schema, so they are excluded from the hash-based scan of
        // *prose* files. The full hash set is still used, unfiltered, for `corpus/real/**.tsp`
        // (anonymised code, not prose about the tool -- no legitimate reason for these words to
        // appear there at all).
        val genericProseWords = setOf(
            "id", "type", "time", "data", "source", "status", "subject", "reason", "cause",
            "state", "kind", "slot", "sync", "origin", "instant", "uid",
            // Added 2026-09-03 re-sync: common English words that are ALSO now mapped
            // identifiers (bare property names the owner's tree happens to use), so they
            // collide with this tool's own prose/comments and other hand-authored fixtures'
            // ordinary vocabulary the same way the pre-existing entries above do.
            "from", "to", "length", "value", "function", "original", "current", "actual",
        )
        // Case variants too: the real scan below hashes (word, lower, upper, capitalised)
        // for every candidate, so a generic word is only actually inert against every
        // one of those variants -- e.g. `type` must exclude the hash of `TYPE` as well
        // as `type`, or an all-caps *domain* concept that coincidentally reuses this
        // common word (this corpus has one, hash a90bab6ff81b -> KIND) makes ordinary
        // lowercase prose usage of "type" fail forever, which is not a leak.
        val genericHashes = genericProseWords.flatMap { w ->
            listOf(w, w.lowercase(), w.uppercase(), w.replaceFirstChar { it.uppercase() })
        }.map { sha256Hex12(it) }.toSet()

        // `anonymise.py`'s ALLOWLIST, mirrored exactly (case-sensitive, exact spelling --
        // same semantics as `rename_identifier`'s `if tok in ALLOWLIST`). Rule 1 requires
        // these preserved byte-for-byte everywhere in the corpus, so their literal presence
        // is never a leak -- even where a reserved word's spelling coincidentally equals a
        // hash key the map carries for an unrelated concept (this corpus has one:
        // `WellKnown.Timestamp`, the stdlib type, versus the owner's own `timestamp`
        // property, correctly anonymised to `capturedAt` wherever *that* word is used).
        // Keep this set in lockstep with `tools/corpus-sync/anonymise.py`'s KEYWORDS /
        // BUILTIN_SCALARS / LIBRARY_NAMES / DECORATOR_NAMES if any of those change.
        val reservedWords = setOf(
            // KEYWORDS
            "import", "model", "scalar", "namespace", "interface", "union", "if",
            "else", "projection", "using", "op", "extends", "is", "enum", "alias",
            "dec", "fn", "valueof", "typeof", "const", "init", "true", "false",
            "return", "void", "never", "unknown", "extern", "auto", "internal",
            "statemachine", "macro", "package", "metadata", "env", "arg", "declare",
            "array", "struct",
            // BUILTIN_SCALARS
            "string", "boolean", "bytes", "numeric", "integer", "float", "int8",
            "int16", "int32", "int64", "uint8", "uint16", "uint32", "uint64",
            "safeint", "float32", "float64", "decimal", "decimal128", "plainDate",
            "plainTime", "utcDateTime", "offsetDateTime", "duration", "url", "null",
            // LIBRARY_NAMES
            "TypeSpec", "Protobuf", "JsonSchema", "Timestamp", "WellKnown", "Http",
            "OpenAPI", "OpenAPI3", "Rest", "Versioning",
            // DECORATOR_NAMES
            "doc", "example", "pattern", "minLength", "maxLength", "minValue",
            "maxValue", "key", "field", "package", "service", "title",
            "friendlyName", "name", "scope", "jsonSchema", "summary", "format",
            "visibility", "encode", "encodedName", "discriminator",
            // DIRECTIVE_NAMES (added 2026-09-03, anonymise.py's `DIRECTIVE_NAMES`)
            "deprecated", "suppress",
            // PACKAGE_SPECIFIER_WORDS (added 2026-09-03, anonymise.py's `PACKAGE_SPECIFIER_WORDS`)
            "schema",
        )

        val offenders = mutableListOf<String>()

        val repoRoot = File(".").absoluteFile
        // A `<hash> -> target` rename-map row is, itself, 12 lowercase hex characters --
        // not prose. Left in the scan, its own letter runs (e.g. "ce" out of
        // "e78d587ce8db") are effectively random bytes that will eventually collide with
        // some other row's hash purely by chance, which is not a leak either: it carries
        // no information about the source vocabulary at all. Strip exactly the
        // hash-shaped tokens before extracting words. Also drop apostrophes so an
        // English contraction ("doesn't") scans as one word ("doesnt") instead of
        // splitting into a real word plus a stray one-letter fragment ("t") that can
        // likewise collide by chance.
        fun scan(fRaw: File, hashesToCheck: Set<String>) {
            val f = fRaw.absoluteFile
            val text = f.readText()
            for (term in explicitForbidden) {
                if (Regex("\\b${Regex.escape(term)}\\b").containsMatchIn(text)) {
                    offenders += "${f.relativeTo(repoRoot)}: contains forbidden term '$term'"
                }
            }
            val scanText = text
                .replace(Regex("\\b[0-9a-f]{12}\\b"), " ")
                .replace(Regex("['’]"), "")
            for (m in Regex("[A-Za-z]+").findAll(scanText)) {
                val word = m.value
                if (word in reservedWords) continue
                // A single letter (generic type parameter names -- `T`, `U`, ... -- are the
                // overwhelming majority of these across the fixtures) cannot itself carry
                // enough entropy to BE the owner's domain vocabulary; with ~130 map rows and
                // only 52 possible single-letter/case candidates, some are going to collide
                // with an unrelated row by pure chance (this corpus already has one: `T` as a
                // template parameter hashes to the same row as a genuine short domain
                // abbreviation). Two or more letters is where a real identifier starts.
                if (word.length < 2) continue
                for (candidate in setOf(word, word.lowercase(), word.uppercase(), word.replaceFirstChar { it.uppercase() })) {
                    val h = sha256Hex12(candidate)
                    if (h in hashesToCheck) {
                        offenders += "${f.relativeTo(repoRoot)}: word hashes to known source-map entry $h"
                        break
                    }
                }
            }
        }

        // corpus/real/**.tsp -- the anonymised output itself, full hash set (no prose exclusions).
        // This is the one tree where a hash hit is unconditionally a leak: every file in it was
        // derived from the real upstream repository by the anonymisation tool, so any surviving
        // occurrence of a mapped word is either an anonymisation bug or an un-anonymised escape.
        realRoot.walkTopDown().filter { it.isFile && it.extension == "tsp" }.forEach { scan(it, mapHashes) }

        // Everything else that is committed test data or tooling but was NOT derived from the
        // real repository -- the map file itself, both PROVENANCE.md files, the
        // re-sync tooling, and every hand-authored fixture under `parser/` and `psi/` (golden
        // `.tsp` input and `.txt` PSI-tree output alike). These fixtures reuse ordinary short
        // property/type-parameter names (`id`, `type`, `T`) that legitimately, coincidentally
        // collide with entries the real map carries for unrelated concepts -- scanned against
        // the hash set minus generic English collisions, same as the tooling/prose files.
        val proseHashes = mapHashes - genericHashes
        val extraFiles = listOfNotNull(
            File(corpusRoot, "ANONYMISATION.md").takeIf { it.isFile },
            File(corpusRoot, "real/PROVENANCE.md").takeIf { it.isFile },
            File(corpusRoot, "stdlib/PROVENANCE.md").takeIf { it.isFile },
        ) +
            (File("tools/corpus-sync").takeIf { it.isDirectory }?.walkTopDown()?.filter { it.isFile }?.toList() ?: emptyList()) +
            (File("src/test/testData/parser").takeIf { it.isDirectory }?.walkTopDown()?.filter { it.isFile }?.toList() ?: emptyList()) +
            (File("src/test/testData/psi").takeIf { it.isDirectory }?.walkTopDown()?.filter { it.isFile }?.toList() ?: emptyList())
        extraFiles.forEach { scan(it, proseHashes) }

        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }

    /**
     * Staleness guard (tsp-tester, 2026-09-03): the member-directive/decorator grammar defect
     * (fixed in `4c4c191`) sat undetected in part because `corpus/real` had drifted out of sync
     * with a real upstream checkout, and nothing made that drift loud. This test re-derives the
     * same content fingerprint `anonymise.py --print-fingerprint` computes -- file count plus a
     * SHA-256 over `relPath\ncontent\n` for every `.tsp` file under `corpus/real`, sorted by relative
     * path -- and compares it against the `` ```corpus-fingerprint``` `` block recorded in
     * `corpus/real/PROVENANCE.md`. It hashes only already-anonymised, already-committed content
     * (never anything source-side), so a hand-edit to a fixture, a partial re-sync, or simply
     * forgetting to regenerate this block after a real re-sync now fails loudly here instead of
     * silently -- exactly the staleness class that let the grammar defect above go unnoticed.
     */
    fun testCorpusFingerprintMatchesRecorded() {
        val realRoot = File(corpusRoot, "real")
        assertTrue("corpus/real missing: $realRoot", realRoot.isDirectory)

        val files = realRoot.walkTopDown()
            .filter { it.isFile && it.extension == "tsp" && "node_modules" !in it.path.split(File.separatorChar) }
            .sortedBy { it.relativeTo(realRoot).path.replace(File.separatorChar, '/') }
            .toList()

        val digest = java.security.MessageDigest.getInstance("SHA-256")
        for (f in files) {
            val rel = f.relativeTo(realRoot).path.replace(File.separatorChar, '/')
            digest.update(rel.toByteArray(Charsets.UTF_8))
            digest.update('\n'.code.toByte())
            digest.update(f.readBytes())
            digest.update('\n'.code.toByte())
        }
        val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
        val actualCount = files.size

        val provenanceFile = File(realRoot, "PROVENANCE.md")
        assertTrue("corpus/real/PROVENANCE.md missing: $provenanceFile", provenanceFile.isFile)
        val provenanceText = provenanceFile.readText()
        val block = Regex("```corpus-fingerprint\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL)
            .find(provenanceText)?.groupValues?.get(1)
        assertTrue(
            "corpus/real/PROVENANCE.md has no ```corpus-fingerprint``` block -- run " +
                "`python3 tools/corpus-sync/anonymise.py --print-fingerprint " +
                "src/test/testData/corpus/real` and add the printed block to PROVENANCE.md",
            block != null,
        )
        val recordedCount = Regex("files:\\s*(\\d+)").find(block!!)?.groupValues?.get(1)?.toIntOrNull()
        val recordedSha = Regex("sha256:\\s*([0-9a-f]+)").find(block)?.groupValues?.get(1)

        val mismatch = recordedCount != actualCount || recordedSha != actualSha
        assertTrue(
            "corpus/real has drifted from the fingerprint recorded in PROVENANCE.md -- if this " +
                "is a deliberate change (a real re-sync or a hand-edited fixture), regenerate " +
                "the block with `python3 tools/corpus-sync/anonymise.py --print-fingerprint " +
                "src/test/testData/corpus/real` and update PROVENANCE.md in the same commit; " +
                "if it is NOT deliberate, corpus/real changed without anyone updating its own " +
                "provenance record -- treat that as the bug.\n" +
                "  recorded: files=$recordedCount sha256=$recordedSha\n" +
                "  actual:   files=$actualCount sha256=$actualSha",
            !mismatch,
        )
    }

    // ------------------------------------------------------------------

    private fun createTypeSpecFile(name: String, text: String): TypeSpecFile {
        val psiFile = PsiFileFactory.getInstance(project)
            .createFileFromText(name, TypeSpecFileType.INSTANCE, text)
        return psiFile as TypeSpecFile
    }

    private fun corpusFiles(): List<File> =
        if (!corpusRoot.isDirectory) {
            emptyList()
        } else {
            corpusRoot.walkTopDown()
                .filter { it.isFile && it.extension == "tsp" }
                .sortedBy { it.path }
                .toList()
        }

    /** The hash-key set from ANONYMISATION.md's `rename-map` block -- `hash(source) -> target`
     *  lines. Only the hash (left-hand side) is meaningful here; the plaintext source name is
     *  never recovered, matching `tools/corpus-sync/anonymise.py`'s `load_map`. */
    private fun mapHashKeys(): Set<String> {
        val mapFile = File(corpusRoot, "ANONYMISATION.md")
        if (!mapFile.isFile) return emptySet()
        val text = mapFile.readText()
        val block = Regex("```rename-map\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL)
            .find(text)?.groupValues?.get(1) ?: return emptySet()
        return block.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
            trimmed.substringBefore("->").trim().takeIf { it.isNotEmpty() }
        }.toSet()
    }

    /** `sha256(name)` first 12 hex chars -- must match `anonymise.py`'s `name_hash` exactly. */
    private fun sha256Hex12(name: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(name.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.substring(0, 12)
    }

    private val bodyContainerTypes = setOf(
        TypeSpecTypes.MODEL_BODY,
        TypeSpecTypes.INTERFACE_BODY,
        TypeSpecTypes.ENUM_BODY,
        TypeSpecTypes.UNION_BODY,
    )

    private val bodyDelimiterTokens = setOf(
        simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.LBRACE,
        simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.RBRACE,
    )

    /**
     * Every leaf whose *immediate* parent is the file itself or a body container, rather than a
     * more specific member/statement rule -- the tree shape [ADR 0007 D2]'s implementation note
     * describes for a token the `bad_*_token_` recovery rules swallowed silently.
     */
    private fun findUnclaimedLeaves(file: TypeSpecFile): List<PsiElement> {
        val result = mutableListOf<PsiElement>()
        // Walk the full AST (not the PSI `children` view, which can omit some leaf tokens),
        // mapping back to PSI at each step -- exhaustive over every token in the file.
        fun visitNode(node: com.intellij.lang.ASTNode) {
            val psi = node.psi
            val firstChildNode = node.firstChildNode
            if (firstChildNode == null) {
                if (psi is PsiWhiteSpace) return
                if (TypeSpecTokenSets.COMMENTS.contains(node.elementType)) return
                val parentNode = node.treeParent ?: return
                val parentPsi = parentNode.psi
                val parentIsFile = parentPsi is TypeSpecFile
                val parentIsBodyContainer = bodyContainerTypes.contains(parentNode.elementType)
                if (parentIsBodyContainer && bodyDelimiterTokens.contains(node.elementType)) {
                    // The container rule's *own* `{`/`}` (e.g. `model_body ::= '{' model_member_*
                    // '}'`) are direct leaf children of every legitimately-parsed body, empty or
                    // not -- not a swallow, and must not be flagged as one.
                    return
                }
                if (parentIsFile || parentIsBodyContainer) {
                    result += psi
                }
                return
            }
            var child: com.intellij.lang.ASTNode? = firstChildNode
            while (child != null) {
                visitNode(child)
                child = child.treeNext
            }
        }
        visitNode(file.node)
        return result
    }

}
