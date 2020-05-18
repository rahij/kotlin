/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.extensions

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirSessionComponent
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.symbols.AbstractFirBasedSymbol

abstract class FirPredicateBasedProvider : FirSessionComponent {
    companion object {
        fun create(session: FirSession): FirPredicateBasedProvider {
            return FirPredicateBasedProviderImpl(session)
        }
    }

    abstract fun getSymbolsByPredicate(predicate: DeclarationPredicate): List<AbstractFirBasedSymbol<*>>
}

private class FirPredicateBasedProviderImpl(private val session: FirSession) : FirPredicateBasedProvider() {
    override fun getSymbolsByPredicate(predicate: DeclarationPredicate): List<AbstractFirBasedSymbol<*>> {
        TODO("Not yet implemented")
    }
}

val FirSession.predicateBasedProvider: FirPredicateBasedProvider by FirSession.sessionComponentAccessor()