/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.extensions.predicate

import org.jetbrains.kotlin.fir.extensions.AnnotationFqn
import org.jetbrains.kotlin.utils.DFS

@RequiresOptIn
private annotation class StateMachineImplementationDetail

@OptIn(StateMachineImplementationDetail::class)
class StateMachine(
    @set:StateMachineImplementationDetail var success: Boolean,
    @property:StateMachineImplementationDetail val annotations: MutableMap<AnnotationFqn, StateMachine> = mutableMapOf(),
    @property:StateMachineImplementationDetail var nextDeclaration: StateMachine? = null
) {

    @OptIn(ExperimentalStdlibApi::class)
    val nextStates: Set<StateMachine> by lazy(LazyThreadSafetyMode.NONE) {
        buildSet {
            addAll(annotations.values)
            nextDeclaration?.let { add(it) }
        }
    }

    @StateMachineImplementationDetail
    fun copy(newStartNode: StateMachine? = null): StateMachine {
        val map = mutableMapOf<StateMachine, StateMachine>()
        for (node in allNodes) {
            map[node] = StateMachine(node.success)
        }
        if (newStartNode != null) {
            map[this] = newStartNode
        }
        for (oldNode in allNodes) {
            val newNode = map.getValue(oldNode)
            oldNode.annotations.mapValuesTo(newNode.annotations) { map.getValue(it.value) }
            newNode.nextDeclaration = oldNode.nextDeclaration?.let { map.getValue(it) }
        }

        return map.getValue(this)
    }

    @StateMachineImplementationDetail
    val allNodes: Set<StateMachine> by lazy(LazyThreadSafetyMode.NONE) {
        DFS.topologicalOrder(
            listOf(this)
        ) { it.nextStates }.toSet()
    }

    fun withAnnotation(annotation: AnnotationFqn): StateMachine {
        if (success) return this
        return annotations[annotation] ?: this
    }

    fun nextDeclaration(): StateMachine {
        return nextDeclaration ?: this
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
    return StateMachine(success = true)
}

@OptIn(StateMachineImplementationDetail::class)
internal fun toStateMachine(predicate: SimplifiedDeclarationPredicate.Or): StateMachine {
    val leftMachine = toStateMachine(predicate.a)
    val rightMachine = toStateMachine(predicate.a)

    for (node in leftMachine.allNodes) {
        rightMachine.copy(node)
    }
    return leftMachine
}

@OptIn(StateMachineImplementationDetail::class)
internal fun toStateMachine(predicate: SimplifiedDeclarationPredicate.And): StateMachine {
    val leftMachine = toStateMachine(predicate.a)
    val rightMachine = toStateMachine(predicate.a)

    val unifiedLeft = leftMachine.copy()
    for (node in unifiedLeft.allNodes) {
        if (!node.success) continue
        node.success = false
        rightMachine.copy(node)
    }

    val unifiedRight = rightMachine.copy()
    for (node in unifiedRight.allNodes) {
        if (!node.success) continue
        node.success = false
        leftMachine.copy(node)
    }

    unifiedRight.copy(unifiedLeft)
    return unifiedLeft
}

@OptIn(StateMachineImplementationDetail::class)
internal fun toStateMachine(predicate: SimplifiedDeclarationPredicate.HasAnnotation): StateMachine {
    val start = StateMachine(success = false)
    val end = StateMachine(success = true, nextDeclaration = start)
    start.annotations[predicate.annotation] = end
    return end
}

@OptIn(StateMachineImplementationDetail::class)
internal fun toStateMachine(predicate: SimplifiedDeclarationPredicate.UnderAnnotated): StateMachine {
    val start = StateMachine(success = false)
    val middle = StateMachine(success = false)
    val end = StateMachine(success = true)
    middle.nextDeclaration = end
    start.annotations[predicate.annotation] = middle
    return start
}