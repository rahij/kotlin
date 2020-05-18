/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.extensions.predicate

import org.jetbrains.kotlin.fir.extensions.AnnotationFqn
import org.jetbrains.kotlin.utils.DFS

@RequiresOptIn
private annotation class StateMachineImplementationDetail

sealed class StateMachine {
    abstract val success: Boolean
    abstract fun withAnnotation(annotation: AnnotationFqn): StateMachine?
    abstract fun nextDeclaration(): StateMachine?
}

@OptIn(StateMachineImplementationDetail::class)
class ClassicStateMachine(
    override val success: Boolean,
    @property:StateMachineImplementationDetail val annotations: MutableMap<AnnotationFqn, ClassicStateMachine> = mutableMapOf(),
    @property:StateMachineImplementationDetail var nextDeclaration: ClassicStateMachine? = null
) : StateMachine() {

    @OptIn(ExperimentalStdlibApi::class)
    val nextStates: Set<ClassicStateMachine>
        get() = buildSet {
            addAll(annotations.values)
            nextDeclaration?.let { add(it) }
        }

    @StateMachineImplementationDetail
    val allNodes: Set<ClassicStateMachine>
        get() = DFS.topologicalOrder(
            listOf(this)
        ) { it.nextStates }.toSet()

    override fun withAnnotation(annotation: AnnotationFqn): ClassicStateMachine? {
        if (success) return null
        return annotations[annotation]
    }

    override fun nextDeclaration(): ClassicStateMachine? {
        return nextDeclaration
    }
}

class OrStateMachine(val a: StateMachine, val b: StateMachine) : StateMachine() {
    override val success: Boolean
        get() = a.success || b.success

    override fun withAnnotation(annotation: AnnotationFqn): OrStateMachine? {
        val nextA = a.withAnnotation(annotation)
        val nextB = b.withAnnotation(annotation)
        if (nextA == null && nextB == null) return null
        return OrStateMachine(nextA ?: a, nextB ?: b)
    }

    override fun nextDeclaration(): OrStateMachine? {
        val nextA = a.nextDeclaration()
        val nextB = b.nextDeclaration()
        if (nextA == null && nextB == null) return null
        return OrStateMachine(nextA ?: a, nextB ?: b)
    }
}

class AndStateMachine(val a: StateMachine, val b: StateMachine) : StateMachine() {
    override val success: Boolean
        get() = a.success && b.success

    override fun withAnnotation(annotation: AnnotationFqn): AndStateMachine? {
        val nextA = a.withAnnotation(annotation)
        val nextB = b.withAnnotation(annotation)
        if (nextA == null && nextB == null) return null
        return AndStateMachine(nextA ?: a, nextB ?: b)
    }

    override fun nextDeclaration(): AndStateMachine? {
        val nextA = a.nextDeclaration()
        val nextB = b.nextDeclaration()
        if (nextA == null && nextB == null) return null
        return AndStateMachine(nextA ?: a, nextB ?: b)
    }
}

internal sealed class SimplifiedDeclarationPredicate {
    object Any : SimplifiedDeclarationPredicate()

    class Or(val a: SimplifiedDeclarationPredicate, val b: SimplifiedDeclarationPredicate) : SimplifiedDeclarationPredicate()

    class And(val a: SimplifiedDeclarationPredicate, val b: SimplifiedDeclarationPredicate) : SimplifiedDeclarationPredicate()

    class HasAnnotation(val annotation: AnnotationFqn) : SimplifiedDeclarationPredicate()
    class UnderAnnotated(val annotation: AnnotationFqn) : SimplifiedDeclarationPredicate()
}

// -------------------------------------------- Builder --------------------------------------------

internal fun toStateMachine(predicate: SimplifiedDeclarationPredicate): StateMachine {
    return when (predicate) {
        is SimplifiedDeclarationPredicate.Any -> toStateMachine(predicate)
        is SimplifiedDeclarationPredicate.Or -> toStateMachine(predicate)
        is SimplifiedDeclarationPredicate.And -> toStateMachine(predicate)
        is SimplifiedDeclarationPredicate.HasAnnotation -> toStateMachine(predicate)
        is SimplifiedDeclarationPredicate.UnderAnnotated -> toStateMachine(predicate)
    }
}
internal fun toStateMachine(predicate: SimplifiedDeclarationPredicate.Any): StateMachine {
    return ClassicStateMachine(success = true)
}

@OptIn(StateMachineImplementationDetail::class)
internal fun toStateMachine(predicate: SimplifiedDeclarationPredicate.Or): StateMachine {
    return OrStateMachine(toStateMachine(predicate.a), toStateMachine(predicate.b))
}

@OptIn(StateMachineImplementationDetail::class)
internal fun toStateMachine(predicate: SimplifiedDeclarationPredicate.And): StateMachine {
    return AndStateMachine(toStateMachine(predicate.a), toStateMachine(predicate.b))
}

@OptIn(StateMachineImplementationDetail::class)
internal fun toStateMachine(predicate: SimplifiedDeclarationPredicate.HasAnnotation): StateMachine {
    val start = ClassicStateMachine(success = false)
    val end = ClassicStateMachine(success = true, nextDeclaration = start)
    start.annotations[predicate.annotation] = end
    return end
}

@OptIn(StateMachineImplementationDetail::class)
internal fun toStateMachine(predicate: SimplifiedDeclarationPredicate.UnderAnnotated): StateMachine {
    val start = ClassicStateMachine(success = false)
    val middle = ClassicStateMachine(success = false)
    val end = ClassicStateMachine(success = true)
    middle.nextDeclaration = end
    start.annotations[predicate.annotation] = middle
    return start
}