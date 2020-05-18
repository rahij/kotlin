/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.transformers

import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase.*
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirBodyResolveProcessor
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirImplicitTypeBodyResolveProcessor
import org.jetbrains.kotlin.fir.resolve.transformers.contracts.FirContractResolveProcessor
import org.jetbrains.kotlin.fir.resolve.transformers.plugin.FirPluginAnnotationsResolveProcessor

fun FirResolvePhase.createProcessorsByPhase(state: FirResolveProcessor.State): FirResolveProcessor {
    return when (this) {
        RAW_FIR -> throw IllegalStateException("Raw FIR building phase does not have a transformer")
        ANNOTATIONS_FOR_PLUGINS -> FirPluginAnnotationsResolveProcessor(state)
//        FIRST_PLUGIN_GENERATION -> FirFirstGenerationTransformer()
        IMPORTS -> FirImportResolveProcessor(state)
        SUPER_TYPES -> FirSupertypeResolverProcessor(state)
        SEALED_CLASS_INHERITORS -> FirSealedClassInheritorsProcessor(state)
        TYPES -> FirTypeResolveProcessor(state)
        STATUS -> FirStatusResolveProcessor(state)
        CONTRACTS -> FirContractResolveProcessor(state)
        IMPLICIT_TYPES_BODY_RESOLVE -> FirImplicitTypeBodyResolveProcessor(state)
        BODY_RESOLVE -> FirBodyResolveProcessor(state)
    }
}



