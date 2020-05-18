/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.extensions

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirSessionComponent
import org.jetbrains.kotlin.fir.utils.ArrayMapAccessor
import org.jetbrains.kotlin.fir.utils.ComponentArrayOwner
import org.jetbrains.kotlin.fir.utils.TypeRegistry
import kotlin.reflect.KClass

class FirExtensionService(val session: FirSession) : ComponentArrayOwner<FirExtension, List<FirExtension>>(), FirSessionComponent {
    companion object : TypeRegistry<FirExtension, List<FirExtension>>() {
        inline fun <reified P : FirExtension, V : List<P>> registeredExtensions(): ArrayMapAccessor<FirExtension, List<FirExtension>, V> {
            return generateAccessor(P::class)
        }
    }

    fun <P : FirExtension> registerExtensions(extensionClass: KClass<P>, extensionFactories: List<FirExtension.Factory<P>>) {
        registerComponent(
            extensionClass,
            extensionFactories.map { it.create(session) }
        )
    }

    override val typeRegistry: TypeRegistry<FirExtension, List<FirExtension>>
        get() = Companion
}

val FirSession.extensionService: FirExtensionService by FirSession.sessionComponentAccessor()
