package simpli.fyi.plugins.typespec.psi

import com.intellij.psi.PsiNameIdentifierOwner

/**
 * The named-element contract every TypeSpec declaration in scope for M5b implements
 * ([ADR 0004](../../../../../../../../docs/adr/0004-reference-resolution-approach.md) D7.2/D7.3):
 * `namespace`, `model`, model property, and template parameter. Hand-written, per
 * [ADR 0006](../../../../../../../../docs/adr/0006-grammar-toolchain.md) D7 — the generated PSI
 * interface for each of those rules `extends` this via the `.bnf`'s `implements=` attribute, and
 * [simpli.fyi.plugins.typespec.psi.impl.TypeSpecNamedElementMixin] satisfies it by ordinary Kotlin
 * inheritance.
 *
 * No extra members beyond [PsiNameIdentifierOwner]: `getName()`, `getNameIdentifier()`,
 * `getTextOffset()`, and `setName()` are all inherited from it.
 */
interface TypeSpecNamedElement : PsiNameIdentifierOwner
