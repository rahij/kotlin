/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

abstract class DeclarationCheckers {
    companion object {
        val EMPTY: DeclarationCheckers = object : DeclarationCheckers() {}
    }

    open val declarationCheckers: List<FirBasicDeclarationChecker> = emptyList()
    open val memberDeclarationCheckers: List<FirMemberDeclarationChecker> = emptyList()
    open val constructorCheckers: List<FirConstructorChecker> = emptyList()

    internal val allDeclarationCheckers get() = declarationCheckers
    internal val allMemberDeclarationCheckers get() = memberDeclarationCheckers + allDeclarationCheckers
    internal val allConstructorCheckers get() = constructorCheckers + allMemberDeclarationCheckers
}
