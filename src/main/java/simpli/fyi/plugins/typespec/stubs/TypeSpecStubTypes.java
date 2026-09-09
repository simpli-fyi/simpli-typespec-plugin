// This file is NOT generated — it is the hand-written holder registered as
// <stubElementTypeHolder> (see plugin.xml). Grammar-Kit's own "<Lang>Types" convention is
// TypeSpecTypes (psi/TypeSpecTypes.java, generated); this class is deliberately separate and
// deliberately hand-written.
package simpli.fyi.plugins.typespec.stubs;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * The stub element type holder for the 10 rules that carry {@code stubClass=} in
 * {@code TypeSpec.bnf} — exactly the set {@code TypeSpecFileDeclarations.build()} walks
 * (a direct child of a file or a {@code namespace} statement): {@code namespace_statement},
 * {@code model_statement}, {@code op_statement}, {@code interface_statement},
 * {@code enum_statement}, {@code union_statement}, {@code alias_statement},
 * {@code scalar_statement}, {@code dec_statement}, {@code fn_statement}
 * (plan 06 M6.5a, ADR 0011).
 *
 * <p>MUST be a plain Java {@code interface} holding ONLY these 10 fields, and MUST be
 * registered via {@code <stubElementTypeHolder class="...TypeSpecStubTypes"
 * externalIdPrefix="tsp."/>}. {@code StubElementTypeHolderEP.initializeOptimized}
 * (bytecode-verified against ideaIC-2025.2.6.3) asserts {@code clazz.isInterface()} and then
 * wraps <em>every</em> declared non-synthetic field, casting each value to
 * {@code ObjectStubSerializer} — a Kotlin {@code object} fails that assertion under {@code -ea}
 * (which tests run with), and pointing the EP at the generated {@code TypeSpecTypes} (~60
 * non-stub fields) fails the same way for a different reason (those fields are not
 * {@code ObjectStubSerializer}s at all). Hence: a separate, hand-written interface containing
 * nothing else.
 *
 * <p>Each field's external ID is {@code externalIdPrefix + fieldName} (e.g.
 * {@code "tsp.MODEL_STATEMENT"}), computed via reflection by
 * {@code StubElementTypeHolderEP} itself and cross-checked at load time against
 * {@link TypeSpecDeclStubElementType#getExternalId()} — the two MUST agree exactly (an
 * {@code IllegalStateException} otherwise), which is why every field below is constructed with
 * a debug name textually identical to its own field name. Changing a field name here, or the
 * {@code externalIdPrefix} in plugin.xml, is an ADR 0011 D6 item-3 stub-version bump.
 */
public interface TypeSpecStubTypes {

    TypeSpecDeclStubElementType NAMESPACE_STATEMENT = new TypeSpecDeclStubElementType("NAMESPACE_STATEMENT");
    TypeSpecDeclStubElementType MODEL_STATEMENT = new TypeSpecDeclStubElementType("MODEL_STATEMENT");
    TypeSpecDeclStubElementType OP_STATEMENT = new TypeSpecDeclStubElementType("OP_STATEMENT");
    TypeSpecDeclStubElementType INTERFACE_STATEMENT = new TypeSpecDeclStubElementType("INTERFACE_STATEMENT");
    TypeSpecDeclStubElementType ENUM_STATEMENT = new TypeSpecDeclStubElementType("ENUM_STATEMENT");
    TypeSpecDeclStubElementType UNION_STATEMENT = new TypeSpecDeclStubElementType("UNION_STATEMENT");
    TypeSpecDeclStubElementType ALIAS_STATEMENT = new TypeSpecDeclStubElementType("ALIAS_STATEMENT");
    TypeSpecDeclStubElementType SCALAR_STATEMENT = new TypeSpecDeclStubElementType("SCALAR_STATEMENT");
    TypeSpecDeclStubElementType DEC_STATEMENT = new TypeSpecDeclStubElementType("DEC_STATEMENT");
    TypeSpecDeclStubElementType FN_STATEMENT = new TypeSpecDeclStubElementType("FN_STATEMENT");

    /**
     * {@code TypeSpec.bnf}'s {@code elementTypeFactory(...)} target for the 10 rules above —
     * looked up by Grammar-Kit's generated {@code TypeSpecTypes} field name (e.g.
     * {@code "MODEL_STATEMENT"}). MUST return one of the singletons above, never a fresh
     * instance: {@code TypeSpecTypes.MODEL_STATEMENT} and {@code TypeSpecStubTypes.MODEL_STATEMENT}
     * must be the identical object, or every {@code ==} comparison the generated parser and PSI
     * factory make against these constants silently stops matching.
     */
    @NotNull
    static IElementType factory(@NotNull String name) {
        switch (name) {
            case "NAMESPACE_STATEMENT":
                return NAMESPACE_STATEMENT;
            case "MODEL_STATEMENT":
                return MODEL_STATEMENT;
            case "OP_STATEMENT":
                return OP_STATEMENT;
            case "INTERFACE_STATEMENT":
                return INTERFACE_STATEMENT;
            case "ENUM_STATEMENT":
                return ENUM_STATEMENT;
            case "UNION_STATEMENT":
                return UNION_STATEMENT;
            case "ALIAS_STATEMENT":
                return ALIAS_STATEMENT;
            case "SCALAR_STATEMENT":
                return SCALAR_STATEMENT;
            case "DEC_STATEMENT":
                return DEC_STATEMENT;
            case "FN_STATEMENT":
                return FN_STATEMENT;
            default:
                throw new IllegalArgumentException("Unknown TypeSpec stub element type: " + name);
        }
    }
}
