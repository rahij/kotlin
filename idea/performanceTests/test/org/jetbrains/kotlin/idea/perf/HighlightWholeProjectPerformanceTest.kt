/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.perf

import org.jetbrains.kotlin.idea.perf.Stats.Companion.tcSuite
import org.jetbrains.kotlin.idea.testFramework.ProjectOpenAction
import org.jetbrains.kotlin.idea.testFramework.logMessage
import java.io.File

class HighlightWholeProjectPerformanceTest : AbstractPerformanceProjectsTest() {

    companion object {

        @JvmStatic
        val hwStats: Stats = Stats("helloWorld project")

        @JvmStatic
        val warmUp = WarmUpProject(hwStats)

        init {
            // there is no @AfterClass for junit3.8
            Runtime.getRuntime().addShutdownHook(Thread { hwStats.close() })
        }

    }

    override fun setUp() {
        super.setUp()
        warmUp.warmUp(this)
    }

    fun testHighlightAllKtFilesInProject() {
        val projectSpecs = projectSpecs()
        for (projectSpec in projectSpecs) {
            val projectName = projectSpec.name
            val projectPath = projectSpec.path

            logMessage { "going to open project '$projectName' at $projectPath" }

            tcSuite("$projectName project") {
                val stats = Stats("$projectName project")
                stats.use { stat ->
                    perfGradleBasedProject(projectName, projectPath, stat)
                    //perfJpsBasedProject(projectName, stat)

                    val project = myProject!!
                    project.projectFilePath
                    val projectDir = File(projectPath)
                    projectDir.allFilesWithExtension("kt").forEach { file ->
                        val path = file.path
                        val localPath = path.substring(path.indexOf(projectPath) + projectPath.length + 1)
                        logMessage { "going to highlight $localPath" }
                        perfHighlightFile(localPath, stats = stat)
                    }
                }
            }
        }
    }

    private fun perfGradleBasedProject(name: String, path: String, stats: Stats) {
        myProject = perfOpenProject(
            name = name,
            stats = stats,
            note = "",
            path = path,
            openAction = ProjectOpenAction.GRADLE_PROJECT,
            fast = true
        )
    }

    private fun perfJpsBasedProject(name: String, stats: Stats) {
        myProject = perfOpenProject(
            name = name,
            stats = stats,
            note = "",
            path = "../$name",
            openAction = ProjectOpenAction.EXISTING_IDEA_PROJECT,
            fast = true
        )
    }

    private fun projectSpecs(): List<ProjectSpec> {
        val projects = System.getProperty("performanceProjects") ?: return emptyList()
        return projects.split(",").map {
            val idx = it.indexOf("=")
            if (idx <= 0) ProjectSpec(it, "../$it") else ProjectSpec(it.substring(0, idx), it.substring(idx + 1))
        }.filter {
            val path = File(it.path)
            path.exists() && path.isDirectory
        }
    }

    private data class ProjectSpec(val name: String, val path: String)
}