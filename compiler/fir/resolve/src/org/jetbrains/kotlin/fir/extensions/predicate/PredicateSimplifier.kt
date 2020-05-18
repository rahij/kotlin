/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.extensions.predicate

import org.jetbrains.kotlin.fir.extensions.AnnotationFqn

typealias ResolvedUserDefinedAnnotations = Map<AnnotationFqn, Collection<AnnotationFqn>>

internal fun DeclarationPredicate.toSimplifiedPredicate(data: ResolvedUserDefinedAnnotations): SimplifiedDeclarationPredicate {
    val mappedPredicate = this.accept(PredicateMapper, data) ?: this
    return mappedPredicate.accept(PredicateSimplifier, null)
}

internal fun DeclarationPredicate.toStateMachine(data: ResolvedUserDefinedAnnotations): StateMachine {
    return toStateMachine(toSimplifiedPredicate(data))
}

private object PredicateSimplifier : DeclarationPredicateVisitor<SimplifiedDeclarationPredicate, Nothing?>() {
    override fun visitPredicate(predicate: DeclarationPredicate, data: Nothing?): SimplifiedDeclarationPredicate {
        throw IllegalStateException("Should not be here")
    }

    override fun visitAny(predicate: DeclarationPredicate.Any, data: Nothing?): SimplifiedDeclarationPredicate {
        return SimplifiedDeclarationPredicate.Any
    }

    override fun visitAnd(predicate: DeclarationPredicate.And, data: Nothing?): SimplifiedDeclarationPredicate {
        return SimplifiedDeclarationPredicate.And(predicate.a.accept(this, data), predicate.b.accept(this, data))
    }

    override fun visitOr(predicate: DeclarationPredicate.Or, data: Nothing?): SimplifiedDeclarationPredicate {
        return SimplifiedDeclarationPredicate.Or(predicate.a.accept(this, data), predicate.b.accept(this, data))
    }

    override fun visitAnnotatedWith(predicate: AnnotatedWith, data: Nothing?): SimplifiedDeclarationPredicate {
        return toSimplePredicate(
            predicate.annotations.toList(),
            singleBuilder = SimplifiedDeclarationPredicate::HasAnnotation,
        )
    }

    override fun visitUnderAnnotatedWith(
        predicate: UnderAnnotatedWith,
        data: Nothing?
    ): SimplifiedDeclarationPredicate {
        return toSimplePredicate(
            predicate.annotations.toList(),
            singleBuilder = SimplifiedDeclarationPredicate::UnderAnnotated,
        )
    }

    private fun toSimplePredicate(
        annotations: List<AnnotationFqn>,
        left: Int = 0,
        right: Int = annotations.lastIndex,
        singleBuilder: (AnnotationFqn) -> SimplifiedDeclarationPredicate
    ): SimplifiedDeclarationPredicate {
        return when (right - left) {
            0 -> throw IllegalStateException()
            1 -> singleBuilder(annotations[left])
            else -> {
                val middle = (right - left) / 2
                val leftPredicate = toSimplePredicate(annotations, left, middle, singleBuilder)
                val rightPredicate = toSimplePredicate(annotations, middle + 1, right, singleBuilder)
                SimplifiedDeclarationPredicate.Or(leftPredicate, rightPredicate)
            }
        }
    }
}

private object PredicateMapper : DeclarationPredicateVisitor<DeclarationPredicate?, ResolvedUserDefinedAnnotations>() {
    override fun visitPredicate(predicate: DeclarationPredicate, data: ResolvedUserDefinedAnnotations): DeclarationPredicate? {
        return null
    }

    override fun visitAnd(predicate: DeclarationPredicate.And, data: ResolvedUserDefinedAnnotations): DeclarationPredicate? {
        val a = predicate.a.accept(this, data)
        val b = predicate.b.accept(this, data)
        if (a == null && b == null) return null
        return DeclarationPredicate.And(a ?: predicate.a, b ?: predicate.b)
    }

    override fun visitOr(predicate: DeclarationPredicate.Or, data: ResolvedUserDefinedAnnotations): DeclarationPredicate? {
        val a = predicate.a.accept(this, data)
        val b = predicate.b.accept(this, data)
        if (a == null && b == null) return null
        return DeclarationPredicate.Or(a ?: predicate.a, b ?: predicate.b)
    }

    override fun visitAnnotatedWithMeta(predicate: AnnotatedWithMeta, data: ResolvedUserDefinedAnnotations): DeclarationPredicate? {
        val annotations = predicate.mapAnnotations(data)
        if (annotations.isEmpty()) return null
        return AnnotatedWith(annotations)
    }

    override fun visitUnderMetaAnnotated(predicate: UnderMetaAnnotated, data: ResolvedUserDefinedAnnotations): DeclarationPredicate? {
        val annotations = predicate.mapAnnotations(data)
        if (annotations.isEmpty()) return null
        return UnderAnnotatedWith(annotations)
    }

    private fun MetaAnnotated.mapAnnotations(data: ResolvedUserDefinedAnnotations): Set<AnnotationFqn> {
        return metaAnnotations.flatMapTo(mutableSetOf()) { data[it] ?: emptySet() }
    }
}