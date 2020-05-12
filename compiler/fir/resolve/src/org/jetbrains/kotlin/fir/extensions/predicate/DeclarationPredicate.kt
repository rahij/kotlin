/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.extensions.predicate

import org.jetbrains.kotlin.fir.declarations.FirAnnotatedDeclaration
import org.jetbrains.kotlin.fir.extensions.AnnotationFqn
import org.jetbrains.kotlin.fir.extensions.extensionsService
import org.jetbrains.kotlin.fir.extensions.fqName

// -------------------------------------------- Predicates --------------------------------------------

sealed class DeclarationPredicate {
    abstract fun match(declaration: FirAnnotatedDeclaration, owners: List<FirAnnotatedDeclaration>): Boolean

    abstract val annotations: Set<AnnotationFqn>
    abstract val metaAnnotations: Set<AnnotationFqn>

    abstract val matchesAll: Boolean

    internal abstract fun <R, D> accept(visitor: DeclarationPredicateVisitor<R, D>, data: D): R

    object Any : DeclarationPredicate() {
        override fun match(declaration: FirAnnotatedDeclaration, owners: List<FirAnnotatedDeclaration>): Boolean = true

        override val annotations: Set<AnnotationFqn>
            get() = emptySet()
        override val metaAnnotations: Set<AnnotationFqn>
            get() = emptySet()

        override val matchesAll: Boolean
            get() = true

        override fun <R, D> accept(visitor: DeclarationPredicateVisitor<R, D>, data: D): R {
            return visitor.visitAny(this, data)
        }
    }

    class Or(val a: DeclarationPredicate, val b: DeclarationPredicate) : DeclarationPredicate() {
        override fun match(declaration: FirAnnotatedDeclaration, owners: List<FirAnnotatedDeclaration>): Boolean {
            return a.match(declaration, owners) || b.match(declaration, owners)
        }

        override val annotations: Set<AnnotationFqn> = a.annotations + b.annotations
        override val metaAnnotations: Set<AnnotationFqn> = a.metaAnnotations + b.metaAnnotations

        override val matchesAll: Boolean
            get() = a.matchesAll || b.matchesAll

        override fun <R, D> accept(visitor: DeclarationPredicateVisitor<R, D>, data: D): R {
            return visitor.visitOr(this, data)
        }
    }

    class And(val a: DeclarationPredicate, val b: DeclarationPredicate) : DeclarationPredicate() {
        override fun match(declaration: FirAnnotatedDeclaration, owners: List<FirAnnotatedDeclaration>): Boolean {
            return a.match(declaration, owners) && b.match(declaration, owners)
        }

        override val annotations: Set<AnnotationFqn> = a.annotations + b.annotations
        override val metaAnnotations: Set<AnnotationFqn> = a.metaAnnotations + b.metaAnnotations

        override val matchesAll: Boolean
            get() = a.matchesAll && b.matchesAll

        override fun <R, D> accept(visitor: DeclarationPredicateVisitor<R, D>, data: D): R {
            return visitor.visitAnd(this, data)
        }
    }
}

sealed class Annotated(final override val annotations: Set<AnnotationFqn>) : DeclarationPredicate() {
    init {
        require(annotations.isNotEmpty()) {
            "Annotations should be not empty"
        }
    }

    final override val metaAnnotations: Set<AnnotationFqn>
        get() = emptySet()

    final override val matchesAll: Boolean
        get() = false

    protected fun FirAnnotatedDeclaration.hasAnnotation(): Boolean {
        return annotations.any { it.fqName(session) in this@Annotated.annotations }
    }
}

class AnnotatedWith(annotations: Set<AnnotationFqn>) : Annotated(annotations) {
    override fun match(declaration: FirAnnotatedDeclaration, owners: List<FirAnnotatedDeclaration>): Boolean {
        return declaration.hasAnnotation()
    }

    override fun <R, D> accept(visitor: DeclarationPredicateVisitor<R, D>, data: D): R {
        return visitor.visitAnnotatedWith(this, data)
    }
}

class UnderAnnotatedWith(annotations: Set<AnnotationFqn>) : Annotated(annotations) {
    override fun match(declaration: FirAnnotatedDeclaration, owners: List<FirAnnotatedDeclaration>): Boolean {
        return owners.any { it.hasAnnotation() }
    }

    override fun <R, D> accept(visitor: DeclarationPredicateVisitor<R, D>, data: D): R {
        return visitor.visitUnderAnnotatedWith(this, data)
    }
}

sealed class MetaAnnotated(final override val metaAnnotations: Set<AnnotationFqn>) : DeclarationPredicate() {
    init {
        require(metaAnnotations.isNotEmpty()) {
            "Annotations should be not empty"
        }
    }

    final override val annotations: Set<AnnotationFqn>
        get() = emptySet()

    final override val matchesAll: Boolean
        get() = false

    protected fun FirAnnotatedDeclaration.hasAnnotation(): Boolean {
        val userDefinedAnnotations = session.extensionsService.userDefinedAnnotations
        val registeredAnnotations = metaAnnotations.flatMap { userDefinedAnnotations[it] }
        return annotations.any { it.fqName(session) in registeredAnnotations }
    }
}

class AnnotatedWithMeta(metaAnnotations: Set<AnnotationFqn>) : MetaAnnotated(metaAnnotations) {
    override fun match(declaration: FirAnnotatedDeclaration, owners: List<FirAnnotatedDeclaration>): Boolean {
        return declaration.hasAnnotation()
    }

    override fun <R, D> accept(visitor: DeclarationPredicateVisitor<R, D>, data: D): R {
        return visitor.visitAnnotatedWithMeta(this, data)
    }
}

class UnderMetaAnnotated(metaAnnotations: Set<AnnotationFqn>) : MetaAnnotated(metaAnnotations) {
    override fun match(declaration: FirAnnotatedDeclaration, owners: List<FirAnnotatedDeclaration>): Boolean {
        return owners.any { it.hasAnnotation() }
    }

    override fun <R, D> accept(visitor: DeclarationPredicateVisitor<R, D>, data: D): R {
        return visitor.visitUnderMetaAnnotated(this, data)
    }
}

// -------------------------------------------- DSL --------------------------------------------

infix fun DeclarationPredicate.or(other: DeclarationPredicate): DeclarationPredicate = DeclarationPredicate.Or(this, other)
infix fun DeclarationPredicate.and(other: DeclarationPredicate): DeclarationPredicate = DeclarationPredicate.And(this, other)

fun under(vararg annotations: AnnotationFqn): DeclarationPredicate = UnderAnnotatedWith(annotations.toSet())
fun with(vararg annotations: AnnotationFqn): DeclarationPredicate = AnnotatedWith(annotations.toSet())
fun metaUnder(vararg metaAnnotations: AnnotationFqn): DeclarationPredicate = AnnotatedWithMeta(metaAnnotations.toSet())
fun metaWith(vararg metaAnnotations: AnnotationFqn): DeclarationPredicate = UnderMetaAnnotated(metaAnnotations.toSet())

fun withOrUnder(vararg annotations: AnnotationFqn): DeclarationPredicate = with(*annotations) or under(*annotations)
fun metaWithOrUnder(vararg metaAnnotations: AnnotationFqn): DeclarationPredicate = metaWith(*metaAnnotations) or metaUnder(*metaAnnotations)
