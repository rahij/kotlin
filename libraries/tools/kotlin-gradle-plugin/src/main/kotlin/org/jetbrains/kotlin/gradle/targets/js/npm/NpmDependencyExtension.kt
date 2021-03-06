/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm

import groovy.lang.Closure
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.reflect.TypeOf
import org.jetbrains.kotlin.gradle.plugin.DEFAULT_GENERATE_KOTLIN_EXTERNALS
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmDependency.Scope.*
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName
import java.io.File

interface BaseNpmDependencyExtension {
    @Deprecated("Declaring NPM dependency without version is forbidden")
    operator fun invoke(name: String): NpmDependency

    operator fun invoke(name: String, version: String): NpmDependency
}

interface NpmDirectoryDependencyExtension : BaseNpmDependencyExtension {
    operator fun invoke(name: String, directory: File): NpmDependency

    operator fun invoke(directory: File): NpmDependency
}

interface NpmDependencyWithExternalsExtension : BaseNpmDependencyExtension {
    operator fun invoke(
        name: String,
        version: String,
        generateKotlinExternals: Boolean
    ): NpmDependency
}

interface NpmDirectoryDependencyWithExternalsExtension : NpmDirectoryDependencyExtension {
    operator fun invoke(
        name: String,
        directory: File,
        generateKotlinExternals: Boolean
    ): NpmDependency

    operator fun invoke(
        directory: File,
        generateKotlinExternals: Boolean
    ): NpmDependency
}

interface NpmDependencyExtension :
    BaseNpmDependencyExtension,
    NpmDependencyWithExternalsExtension,
    NpmDirectoryDependencyExtension,
    NpmDirectoryDependencyWithExternalsExtension

interface DevNpmDependencyExtension :
    BaseNpmDependencyExtension,
    NpmDirectoryDependencyExtension

interface PeerNpmDependencyExtension :
    BaseNpmDependencyExtension

internal fun Project.addNpmDependencyExtension() {
    val extensions = (dependencies as ExtensionAware).extensions

    values()
        .forEach { scope ->
            val type = when (scope) {
                NORMAL, OPTIONAL -> NpmDependencyExtension::class.java
                DEV -> DevNpmDependencyExtension::class.java
                PEER -> PeerNpmDependencyExtension::class.java
            }

            val extension: BaseNpmDependencyExtension = when (scope) {
                NORMAL, OPTIONAL -> DefaultNpmDependencyExtension(
                    this,
                    scope,
                    DEFAULT_GENERATE_KOTLIN_EXTERNALS
                )
                DEV -> DefaultDevNpmDependencyExtension(
                    this
                )
                PEER -> DefaultPeerNpmDependencyExtension(
                    this
                )
            }

            extensions
                .add(
                    TypeOf.typeOf<BaseNpmDependencyExtension>(type),
                    scopePrefix(scope),
                    extension
                )
        }
}

private fun scopePrefix(scope: NpmDependency.Scope): String {
    val scopePrefix = scope.name
        .removePrefix(NORMAL.name)
        .toLowerCase()

    return lowerCamelCaseName(scopePrefix, "npm")
}

private abstract class NpmDependencyExtensionDelegate(
    protected val project: Project,
    protected val scope: NpmDependency.Scope,
    protected val _defaultGenerateKotlinExternals: Boolean?
) : NpmDependencyExtension,
    DevNpmDependencyExtension,
    PeerNpmDependencyExtension,
    Closure<NpmDependency>(project.dependencies) {
    protected val defaultGenerateKotlinExternals: Boolean
        get() = _defaultGenerateKotlinExternals ?: false

    override fun invoke(name: String): NpmDependency =
        onlyNameNpmDependency(name)

    override operator fun invoke(
        name: String,
        version: String,
        generateKotlinExternals: Boolean
    ): NpmDependency =
        NpmDependency(
            project = project,
            name = name,
            version = version,
            scope = scope,
            generateKotlinExternals = generateKotlinExternals
        )

    override fun invoke(name: String, version: String): NpmDependency =
        invoke(
            name = name,
            version = version,
            generateKotlinExternals = defaultGenerateKotlinExternals
        )

    override fun invoke(name: String, directory: File): NpmDependency =
        invoke(
            name = name,
            directory = directory,
            generateKotlinExternals = defaultGenerateKotlinExternals
        )

    override fun invoke(directory: File): NpmDependency =
        invoke(
            directory = directory,
            generateKotlinExternals = defaultGenerateKotlinExternals
        )

    override operator fun invoke(
        directory: File,
        generateKotlinExternals: Boolean
    ): NpmDependency =
        invoke(
            name = moduleName(directory),
            directory = directory,
            generateKotlinExternals = generateKotlinExternals
        )

    override fun call(vararg args: Any?): NpmDependency {
        if (args.size > 3) npmDeclarationException(args)

        val arg = args[0]
        return when (arg) {
            is String -> withName(
                name = arg,
                args = *args
            )
            else -> processNonStringFirstArgument(arg, *args)
        }
    }

    protected abstract fun processNonStringFirstArgument(arg: Any?, vararg args: Any?): NpmDependency

    private fun withName(name: String, vararg args: Any?): NpmDependency {
        val arg1 = if (args.size > 1) args[1] else null
        val generateKotlinExternals = generateKotlinExternalsIfPossible(*args)

        return when (arg1) {
            null -> invoke(
                name = name
            )
            is String -> invoke(
                name = name,
                version = arg1,
                generateKotlinExternals = generateKotlinExternals
            )
            else -> processNamedNonStringSecondArgument(
                name,
                arg1,
                generateKotlinExternals,
                *args
            )
        }
    }

    protected abstract fun processNamedNonStringSecondArgument(
        name: String,
        arg: Any?,
        generateKotlinExternals: Boolean,
        vararg args: Any?
    ): NpmDependency

    protected fun npmDeclarationException(args: Array<out Any?>): Nothing {
        throw IllegalArgumentException(
            """
            |Unable to add NPM dependency with scope '${scope.name.toLowerCase()}' by ${args.joinToString { "'$it'" }}
            |Possible variants:
            |${possibleVariants().joinToString("\n") { "- ${it.first} -> ${it.second}" }}
            """.trimMargin()
        )
    }

    protected open fun possibleVariants(): List<Pair<String, String>> {
        return listOf("${scopePrefix(scope)}('name', 'version')" to "name:version")
    }

    protected fun generateKotlinExternalsIfPossible(vararg args: Any?): Boolean {
        val arg2 = (if (args.size > 2) args[2] else null) as? Boolean

        if (arg2 != null && _defaultGenerateKotlinExternals == null) {
            npmDeclarationException(args)
        }

        return arg2 ?: defaultGenerateKotlinExternals
    }
}

private class DefaultNpmDependencyExtension(
    project: Project,
    scope: NpmDependency.Scope,
    defaultGenerateKotlinExternals: Boolean?
) : Closure<NpmDependency>(project.dependencies),
    NpmDependencyExtension {
    private val delegate = defaultNpmDependencyDelegate(
        project,
        scope,
        defaultGenerateKotlinExternals
    )

    override fun invoke(name: String): NpmDependency =
        delegate.invoke(name)

    override fun invoke(name: String, version: String): NpmDependency =
        delegate.invoke(name, version)

    override fun invoke(name: String, directory: File): NpmDependency =
        delegate.invoke(name, directory)

    override fun invoke(directory: File): NpmDependency =
        delegate.invoke(directory)

    override fun invoke(name: String, version: String, generateKotlinExternals: Boolean): NpmDependency =
        delegate.invoke(name, version, generateKotlinExternals)

    override fun invoke(name: String, directory: File, generateKotlinExternals: Boolean): NpmDependency =
        delegate.invoke(name, directory, generateKotlinExternals)

    override fun invoke(directory: File, generateKotlinExternals: Boolean): NpmDependency =
        delegate.invoke(directory, generateKotlinExternals)

    override fun call(vararg args: Any?): NpmDependency =
        delegate.call(*args)
}

private class DefaultDevNpmDependencyExtension(
    project: Project
) : Closure<NpmDependency>(project.dependencies),
    DevNpmDependencyExtension {
    private val delegate = defaultNpmDependencyDelegate(
        project,
        DEV,
        null
    )

    override fun invoke(name: String): NpmDependency =
        delegate.invoke(name)

    override fun invoke(name: String, version: String): NpmDependency =
        delegate.invoke(name, version)

    override fun invoke(name: String, directory: File): NpmDependency =
        delegate.invoke(name, directory)

    override fun invoke(directory: File): NpmDependency =
        delegate.invoke(directory)

    override fun call(vararg args: Any?): NpmDependency =
        delegate.call(*args)
}

private fun defaultNpmDependencyDelegate(
    project: Project,
    scope: NpmDependency.Scope,
    defaultGenerateKotlinExternals: Boolean?
): NpmDependencyExtensionDelegate {
    return object : NpmDependencyExtensionDelegate(
        project,
        scope,
        defaultGenerateKotlinExternals
    ) {
        override operator fun invoke(
            name: String,
            directory: File,
            generateKotlinExternals: Boolean
        ): NpmDependency =
            directoryNpmDependency(
                project = project,
                name = name,
                directory = directory,
                scope = scope,
                generateKotlinExternals = generateKotlinExternals
            )

        override fun processNonStringFirstArgument(arg: Any?, vararg args: Any?): NpmDependency {
            val generateKotlinExternals = generateKotlinExternalsIfPossible(args)

            return when (arg) {
                is File -> invoke(
                    directory = arg,
                    generateKotlinExternals = generateKotlinExternals
                )
                else -> npmDeclarationException(args)
            }
        }

        override fun processNamedNonStringSecondArgument(
            name: String,
            arg: Any?,
            generateKotlinExternals: Boolean,
            vararg args: Any?
        ): NpmDependency {
            return when (arg) {
                is File -> invoke(
                    name = name,
                    directory = arg,
                    generateKotlinExternals = generateKotlinExternals
                )
                else -> npmDeclarationException(args)
            }
        }

        override fun possibleVariants(): List<Pair<String, String>> {
            val result = super.possibleVariants() + listOf(
                "${scopePrefix(scope)}(File)" to "File.name:File",
                "${scopePrefix(scope)}('name', File)" to "name:File"
            )

            if (_defaultGenerateKotlinExternals == null) {
                return result
            }

            return result
                .map { (first, second) ->
                    val value = first.replace(")", ", generateKotlinExternals = $defaultGenerateKotlinExternals)")
                    value to second
                }
        }
    }
}

private class DefaultPeerNpmDependencyExtension(
    project: Project
) : Closure<NpmDependency>(project.dependencies),
    PeerNpmDependencyExtension {
    private val delegate: NpmDependencyExtensionDelegate = object : NpmDependencyExtensionDelegate(
        project,
        PEER,
        null
    ) {
        override fun invoke(
            name: String,
            directory: File,
            generateKotlinExternals: Boolean
        ): NpmDependency =
            npmDeclarationException(arrayOf(name, directory))

        override fun processNonStringFirstArgument(arg: Any?, vararg args: Any?): NpmDependency =
            npmDeclarationException(args)

        override fun processNamedNonStringSecondArgument(
            name: String,
            arg: Any?,
            generateKotlinExternals: Boolean,
            vararg args: Any?
        ): NpmDependency =
            npmDeclarationException(args)
    }

    override fun invoke(name: String): NpmDependency =
        delegate.invoke(name)

    override fun invoke(name: String, version: String): NpmDependency =
        delegate.invoke(name, version)

    override fun call(vararg args: Any?): NpmDependency =
        delegate.call(*args)
}