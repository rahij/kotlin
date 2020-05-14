/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.util

import org.jetbrains.kotlin.ir.declarations.IrSymbolOwner
import org.jetbrains.kotlin.ir.symbols.IrSymbolWithOwner

abstract class SymbolStorageSkeleton<D, B : IrSymbolOwner, S : IrSymbolWithOwner<B>> {
    val unboundSymbols = linkedSetOf<S>()

    abstract fun get(d: D): S?
    abstract fun set(d: D, s: S)
    abstract fun get(sig: IdSignature): S?

    abstract val D.originalValue: D

    inline fun declareByExisting(d0: D, existing: S?, createSymbol: () -> S, createOwner: (S) -> B): B {
        val symbol = if (existing == null) {
            val new = createSymbol()
            set(d0, new)
            new
        } else {
            unboundSymbols.remove(existing)
            existing
        }
        return createOwner(symbol)
    }

    inline fun declare(d: D, createSymbol: () -> S, createOwner: (S) -> B): B {
        val d0 = d.originalValue
        assert(d0 === d) {
            "Non-original descriptor in declaration: $d\n\tExpected: $d0"
        }
        val existing = get(d0)
        return declareByExisting(d0, existing, createSymbol, createOwner)
    }

    inline fun declare(sig: IdSignature, d: D, createSymbol: () -> S, createOwner: (S) -> B): B {
        val d0 = d.originalValue
        assert(d0 === d) {
            "Non-original descriptor in declaration: $d\n\tExpected: $d0"
        }
        val existing = get(sig)
        return declareByExisting(d0, existing, createSymbol, createOwner)
    }

    inline fun declareIfNotExists(d: D, createSymbol: () -> S, createOwner: (S) -> B): B {
        val d0 = d.originalValue
        assert(d0 === d) {
            "Non-original descriptor in declaration: $d\n\tExpected: $d0"
        }
        val existing = get(d0)
        val symbol = if (existing == null) {
            val new = createSymbol()
            set(d0, new)
            new
        } else {
            if (!existing.isBound) unboundSymbols.remove(existing)
            existing
        }
        return if (symbol.isBound) symbol.owner else createOwner(symbol)
    }

    inline fun referenced(d: D, orElse: () -> S): S {
        val d0 = d.originalValue
        assert(d0 === d) {
            "Non-original descriptor in declaration: $d\n\tExpected: $d0"
        }
        val s = get(d0)
        if (s == null) {
            val new = orElse()
            assert(unboundSymbols.add(new)) {
                "Symbol for ${new.descriptor} was already referenced"
            }
            set(d0, new)
            return new
        }
        return s
    }
}