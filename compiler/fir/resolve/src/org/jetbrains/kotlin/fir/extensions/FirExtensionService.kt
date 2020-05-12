/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.extensions

import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirSessionComponent
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.utils.ComponentArrayOwner
import kotlin.reflect.KClass

class SomeData

sealed class FirExtensionService : ComponentArrayOwner<FirExtension, SomeData>(), FirSessionComponent {
    companion object {
        fun create(session: FirSession): FirExtensionService {
            TODO()
        }


    }

    abstract fun <P : FirExtension> registerExtensions(extensionClass: KClass<P>, extensionFactories: List<FirExtension.Factory<P>>)

    abstract class ExtensionsAccessor<P : FirExtension> {
        abstract fun forDeclaration(declaration: FirDeclaration, owners: Collection<FirAnnotationContainer>)
        abstract fun forGeneratedDeclaration(origin: FirDeclarationOrigin.Plugin)
        abstract val all: Collection<P>
    }
}

/*
 * ExtensionService:
 * 1. declaration, specific extension -> all matching extension instances
 * 2. specific extension -> all extension instances
 */

private abstract class FirExtensionServiceImpl : FirExtensionService() {

}

/*
 *         @A || @B -> annotations.any { it in [@A, @B] } <=> [@A, @B].any { it in annotations }
 *         @A && @B -> [@A, @B].all { it in annotations }
 *
 *   :DNF:
 * (@A && @B) || (@C && @D) -> [@A, @B].all { it in annotations } || [@C, @D].all { it in annotations }
 *   :CNF:
 * (@A || @B) && (@C || @D) -> [@A, @B].any { it in annotations } && [@C, @D].any { it in annotations }
 * (@A || @B) && (@C || @D) -> annotations.any { it in [@A, @B] } && annotations.any { it in [@C, @D] }
 *
 * @A || #B -> [@A].any { it in annotations } || parents.any { [@B].any { it in annotations } }
 *
 *
 */
