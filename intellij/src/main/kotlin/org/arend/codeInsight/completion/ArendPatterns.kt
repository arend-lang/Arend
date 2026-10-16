package org.arend.codeInsight.completion

import com.intellij.patterns.*
import com.intellij.psi.PsiElement

fun <T : PsiElement> withAncestors(vararg et: Class<out T>): ElementPattern<PsiElement> =
        StandardPatterns.and(*et.mapIndexed { i, it -> PlatformPatterns.psiElement().withSuperParent(i + 1, PlatformPatterns.psiElement(it)) }.toTypedArray())
