/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.extensions

import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Multimap
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirSessionComponent
import org.jetbrains.kotlin.fir.declarations.FirRegularClass

abstract class FirRegisteredPluginAnnotations : FirSessionComponent {
    companion object {
        fun create(): FirRegisteredPluginAnnotations {
            return FirRegisteredPluginAnnotationsImpl()
        }
    }

    abstract val annotations: Set<AnnotationFqn>
    abstract val metaAnnotations: Set<AnnotationFqn>
    abstract fun getAnnotationsWithMetaAnnotation(metaAnnotation: AnnotationFqn): Collection<AnnotationFqn>

    abstract fun registerAnnotations(annotations: Collection<AnnotationFqn>)
    abstract fun registerMetaAnnotations(metaAnnotations: Collection<AnnotationFqn>)
    abstract fun registerUserDefinedAnnotation(metaAnnotation: AnnotationFqn, annotationClasses: Collection<FirRegularClass>)
}

private class FirRegisteredPluginAnnotationsImpl : FirRegisteredPluginAnnotations() {
    override val annotations: MutableSet<AnnotationFqn> = mutableSetOf()
    override val metaAnnotations: MutableSet<AnnotationFqn> = mutableSetOf()

    // MetaAnnotation -> Annotations
    private val userDefinedAnnotations: Multimap<AnnotationFqn, AnnotationFqn> = LinkedHashMultimap.create()

    override fun getAnnotationsWithMetaAnnotation(metaAnnotation: AnnotationFqn): Collection<AnnotationFqn> {
        return userDefinedAnnotations[metaAnnotation]
    }

    override fun registerAnnotations(annotations: Collection<AnnotationFqn>) {
        this.annotations += annotations
    }

    override fun registerMetaAnnotations(metaAnnotations: Collection<AnnotationFqn>) {
        this.metaAnnotations += metaAnnotations
    }

    override fun registerUserDefinedAnnotation(metaAnnotation: AnnotationFqn, annotationClasses: Collection<FirRegularClass>) {
        require(annotationClasses.all { it.classKind == ClassKind.ANNOTATION_CLASS })
        val annotations = annotationClasses.map { it.symbol.classId.asSingleFqName() }
        registerAnnotations(annotations)
        userDefinedAnnotations.putAll(metaAnnotation, annotations)
    }
}

val FirSession.registeredPluginAnnotations: FirRegisteredPluginAnnotations by FirSession.sessionComponentAccessor()