/**
 * Copyright (C) 2013-2016 Steffen Schaefer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.richsource.gradle.plugins.gwt

import org.hamcrest.core.IsInstanceOf.instanceOf
import org.hamcrest.core.IsCollectionContaining.*
import org.hamcrest.core.IsNot.*
import org.junit.Assert.*

import org.gradle.api.Project
import org.gradle.api.internal.project.AbstractProject
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.bundling.War
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.eclipse.model.BuildCommand
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test

class GwtPluginTest {

    private lateinit var project: Project
    private lateinit var tasks: TaskContainer
    private lateinit var extensions: ExtensionContainer

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().build()
        project.plugins.apply(GwtPlugin::class.java)

        extensions = project.extensions
        tasks = project.tasks
    }

    @Test
    fun testExtensionAvailable() {
        assertThat(extensions.getByName(GwtBasePlugin.EXTENSION_NAME), instanceOf<Any>(GwtPluginExtension::class.java))
    }

    @Test
    fun testConfigurationAvailable() {
        assertNotNull(project.configurations.findByName(GwtBasePlugin.GWT_CONFIGURATION))
    }

    @Test
    fun testBasicTasksAvailable() {
        assertThat(tasks.getByName(GwtCompilerPlugin.TASK_COMPILE_GWT), instanceOf<Any>(GwtCompile::class.java))
        assertThat(tasks.getByName(GwtCompilerPlugin.TASK_DRAFT_COMPILE_GWT), instanceOf<Any>(GwtDraftCompile::class.java))
    }

    @Test
    fun testSuperDevTaskAvailable() {
        extension.isCodeserver = true
        (project as AbstractProject).evaluate()

        assertThat(tasks.getByName(GwtBasePlugin.TASK_GWT_SUPER_DEV), instanceOf<Any>(GwtSuperDev::class.java))
    }

    @Test
    fun testSuperDevTaskNotAvailable() {
        extension.isCodeserver = false
        (project as AbstractProject).evaluate()

        assertNull(tasks.findByName(GwtBasePlugin.TASK_GWT_SUPER_DEV))
    }

    @Test
    fun testWarTasksAvailable() {
        project.plugins.apply(WarPlugin::class.java)

        assertThat(tasks.getByName(GwtWarPlugin.TASK_WAR_TEMPLATE), instanceOf<Any>(ExplodedWar::class.java))
        assertThat(tasks.getByName(GwtWarPlugin.TASK_GWT_DEV), instanceOf<Any>(GwtDev::class.java))
        assertThat(tasks.getByName(GwtWarPlugin.TASK_DRAFT_WAR), instanceOf<Any>(War::class.java))
    }

    @Test
    fun testEclipseSetup() {
        project.plugins.apply(EclipsePlugin::class.java)

        val eclipseModel = project.extensions.getByType(EclipseModel::class.java)
        assertThat(eclipseModel.project.natures, hasItem(GwtEclipsePlugin.ECLIPSE_NATURE))
        assertThat(eclipseModel.project.buildCommands, hasItem(BuildCommand(GwtEclipsePlugin.ECLIPSE_BUILDER_PROJECT_VALIDATOR)))
        assertThat(eclipseModel.project.buildCommands, not(hasItem(BuildCommand(GwtEclipsePlugin.ECLIPSE_BUILDER_WEBAPP_VALIDATOR))))

        project.plugins.apply(WarPlugin::class.java)
        assertThat(eclipseModel.project.buildCommands, hasItem(BuildCommand(GwtEclipsePlugin.ECLIPSE_BUILDER_WEBAPP_VALIDATOR)))
    }

    private val extension: GwtPluginExtension
        get() = extensions.getByType(GwtPluginExtension::class.java)

}
