package simpli.fyi.plugins.typespec.psi

import com.intellij.util.IncorrectOperationException

/**
 * The single source of truth for whether a TypeSpec name is legal, and how it must be spelled
 * when written to source (ADR 0012 D2). Every write path (`TypeSpecIdentifierManipulator`,
 * `TypeSpecNamedElementMixin.setName`, from M6.5g on) funnels through [escape]; there is one
 * escaper and one caller convention.
 *
 * The escaping rule is **canonical and minimal, not preserving**: `` `model` `` renamed to
 * `Foo` yields `Foo` — a name's decorative backticks are dropped as soon as they are no longer
 * needed. This makes [escape] idempotent over [unescape], which is what lets the manipulator
 * re-canonicalise a whole name on every call rather than splice inside existing backticks.
 *
 * [TypeSpecPsiUtil.stripBackticks] is a separate, deliberately tiny sibling used on the
 * resolver's hot path; the two are behaviourally identical for the inputs that function sees,
 * but are not merged (ADR 0012 D2).
 */
object TypeSpecNames {

    /**
     * Mirrors JFlex's `[:jletter:]` / `[:jletterdigit:]`, which the `.flex`'s `Identifier`
     * macro is built from — this is a mirror of the lexer, not an approximation of the
     * TypeSpec language spec. A name is a bare (unbacktickable) identifier when:
     *  - its first character satisfies [Character.isJavaIdentifierStart];
     *  - every subsequent character satisfies [Character.isJavaIdentifierPart];
     *  - no character is an ISO control character (rules out a pasted zero-width character
     *    that `isJavaIdentifierPart` would otherwise silently accept);
     *  - it is not one of [TypeSpecKeywords.ALL].
     */
    fun isBareIdentifier(n: String): Boolean {
        if (n.isEmpty()) return false
        if (n.any { it.isISOControl() }) return false
        if (!Character.isJavaIdentifierStart(n[0])) return false
        for (i in 1 until n.length) {
            if (!Character.isJavaIdentifierPart(n[i])) return false
        }
        return n !in TypeSpecKeywords.ALL
    }

    /**
     * Mirrors `DefaultWordsScanner.stripWords` (decompiled, ADR 0012 F12), which is what backs
     * `ReferencesSearch`'s word-index prefilter — this answers a *recall* question, not a
     * *spelling* one, and is deliberately not a variant of [isBareIdentifier] (ADR 0012 D7):
     * conflating the two already caused one defect. A name is index-keyable — the whole string
     * comes back as a single word occurrence — when:
     *  - its first character satisfies [Character.isJavaIdentifierStart] **or** is an ASCII
     *    digit (`stripWords`' word-start test admits `'0'..'9'` even though
     *    `isJavaIdentifierStart` does not — this is the word-*start* class, not the word-*part*
     *    class used for every other character);
     *  - every character (including the first) satisfies [Character.isJavaIdentifierPart].
     *
     * Two things this deliberately omits, both verified against the decompiled scanner and both
     * out of scope for a recall question:
     *  - **No ISO-control exclusion** — `stripWords` treats control characters as word parts, so
     *    they do not hurt recall. That exclusion belongs to [isBareIdentifier] alone, for
     *    spelling reasons.
     *  - **No keyword clause** — keyword-ness is irrelevant to whether the index can find the
     *    word; `isIndexKeyableName("model")` is `true` even though `isBareIdentifier("model")`
     *    is `false`.
     *
     * Do not let this predicate borrow a clause from [isBareIdentifier], or vice versa — see the
     * comparison table in plan 07 §M6.5f approach 2b.
     */
    fun isIndexKeyableName(name: String): Boolean =
        name.isNotEmpty() &&
            (Character.isJavaIdentifierStart(name[0]) || name[0] in '0'..'9') &&
            name.all { Character.isJavaIdentifierPart(it) }

    /**
     * Strips one well-formed surrounding backtick pair, else returns the input unchanged.
     * Behaviourally identical to [TypeSpecPsiUtil.stripBackticks] for the inputs that function
     * sees; kept separate on purpose (ADR 0012 D2) — that one is the resolver's hot path.
     */
    fun unescape(text: String): String {
        return if (text.length >= 2 && text.startsWith("`") && text.endsWith("`")) {
            text.substring(1, text.length - 1)
        } else {
            text
        }
    }

    /**
     * Canonicalises a raw, possibly user-typed name into the text that must be written to
     * source: bare if legal bare, backtick-wrapped otherwise. Mirrors `RenameUtil.isValidName`'s
     * own `.trim()` (ADR 0012 F2) so what the rename dialog validated and what this writes
     * cannot disagree.
     *
     * @throws IncorrectOperationException if [raw], once trimmed and unescaped, is empty or
     *   contains a backtick, `\r`, or `\n` — none of those are representable in either shape
     *   the lexer accepts.
     */
    fun escape(raw: String): String {
        var n = raw.trim()
        if (n.length >= 2 && n.startsWith("`") && n.endsWith("`")) {
            n = n.substring(1, n.length - 1)
        }
        if (n.isEmpty()) {
            throw IncorrectOperationException("Name must not be empty")
        }
        if (n.contains('`') || n.contains('\r') || n.contains('\n')) {
            throw IncorrectOperationException("Name \"$raw\" is not representable in TypeSpec source")
        }
        return if (isBareIdentifier(n)) n else "`$n`"
    }
}
