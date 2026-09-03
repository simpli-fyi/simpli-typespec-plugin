package simpli.fyi.plugins.typespec.stubs

import com.intellij.psi.stubs.PsiFileStubImpl
import simpli.fyi.plugins.typespec.psi.TypeSpecFile

/**
 * The stub tree's root, one per `.tsp` file (plan 06 M6.5a). Carries nothing beyond what
 * [PsiFileStubImpl] already provides — every declaration lives on a [TypeSpecDeclStub] child.
 */
class TypeSpecFileStub(file: TypeSpecFile?) : PsiFileStubImpl<TypeSpecFile>(file)
