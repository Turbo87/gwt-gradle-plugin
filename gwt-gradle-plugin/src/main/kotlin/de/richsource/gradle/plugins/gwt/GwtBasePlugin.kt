/**
 * Copyright (C) 2013-2016 Steffen Schaefer

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.richsource.gradle.plugins.gwt

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.IConventionAware
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.testing.Test
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.idea.IdeaPlugin
import java.io.File
import java.util.concurrent.Callable

open class GwtBasePlugin : Plugin<Project> {
    private lateinit var project: Project

    lateinit var extension: GwtPluginExtension
        private set

    lateinit var gwtConfiguration: Configuration
        private set

    lateinit var gwtSdkConfiguration: Configuration
        private set

    lateinit var allGwtConfigurations: ConfigurableFileCollection
        private set

    override fun apply(project: Project) {
        this.project = project
        project.plugins.apply(JavaPlugin::class.java)

        val gwtBuildDir = File(project.buildDir, BUILD_DIR)

        extension = configureGwtExtension(gwtBuildDir)

        configureAbstractActionTasks()
        configureAbstractTasks()
        configureGwtCompile()
        configureGwtDev()
        configureGwtSuperDev()

        gwtConfiguration = project.configurations.create(GWT_CONFIGURATION)
                .setDescription("Classpath for GWT client libraries that are not included in the war")
        gwtSdkConfiguration = project.configurations.create(GWT_SDK_CONFIGURATION)
                .setDescription("Classpath for GWT SDK libraries (gwt-dev, gwt-user)")
        allGwtConfigurations = project.files(gwtConfiguration, gwtSdkConfiguration)

        addToMainSourceSetClasspath(allGwtConfigurations)

        val testSourceSet = testSourceSet
        testSourceSet.compileClasspath = testSourceSet.compileClasspath.plus(allGwtConfigurations)

        project.afterEvaluate { project ->
            var runtimeClasspath = allGwtConfigurations.plus(testSourceSet.runtimeClasspath)
            if (extension.test.isHasGwtTests) {
                runtimeClasspath = project.files(*mainSourceSet.allJava.srcDirs.toTypedArray())
                        .plus(project.files(*testSourceSet.allJava.srcDirs.toTypedArray()))
                        .plus(runtimeClasspath)

                configureTestTasks(extension)
            }
            testSourceSet.runtimeClasspath = runtimeClasspath

            var versionSet = false
            var major = 2
            var minor = 5

            val gwtVersion = extension.gwtVersion
            if (gwtVersion != null && !extension.gwtVersion.isEmpty()) {
                val token = gwtVersion.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (token.size >= 2) {
                    try {
                        major = Integer.parseInt(token[0])
                        minor = Integer.parseInt(token[1])
                        versionSet = true
                    } catch (e: NumberFormatException) {
                        logger.warn("GWT version ${extension.gwtVersion} can not be parsed. Valid versions must have the format major.minor.patch where major and minor are positive integer numbers.")
                    }

                } else {
                    logger.warn("GWT version ${extension.gwtVersion} can not be parsed. Valid versions must have the format major.minor.patch where major and minor are positive integer numbers.")
                }
            }

            if (major == 2 && minor >= 5 || major > 2) {
                if (extension.isCodeserver) {
                    createSuperDevModeTask(project)
                }
            }

            if (versionSet) {
                project.dependencies.add(GWT_SDK_CONFIGURATION, gwtDependency(GWT_DEV, gwtVersion))
                project.dependencies.add(GWT_SDK_CONFIGURATION, gwtDependency(GWT_USER, gwtVersion))
                project.dependencies.add(JavaPlugin.RUNTIME_CONFIGURATION_NAME, gwtDependency(GWT_SERVLET, gwtVersion))

                if (major == 2 && minor >= 5 || major > 2) {
                    if (extension.isCodeserver) {
                        project.dependencies.add(GWT_CONFIGURATION, gwtDependency(GWT_CODESERVER, gwtVersion))
                    }
                    if (extension.isElemental) {
                        project.dependencies.add(GWT_CONFIGURATION, gwtDependency(GWT_ELEMENTAL, gwtVersion))
                    }
                } else {
                    logger.warn("GWT version is <2.5 -> additional dependencies are not added.")
                }
            }
        }

        project.plugins.withType(EclipsePlugin::class.java) { GwtEclipsePlugin().apply(project, this@GwtBasePlugin) }
        project.plugins.withType(IdeaPlugin::class.java) { GwtIdeaPlugin().apply(project, this@GwtBasePlugin) }
    }

    private fun gwtDependency(artifactId: String, gwtVersion: String): String {
        return "$GWT_GROUP:$artifactId:$gwtVersion"
    }

    private fun configureGwtExtension(buildDir: File): GwtPluginExtension {

        val extension = project.extensions.create(EXTENSION_NAME, GwtPluginExtension::class.java).apply {
            devWar = project.file(DEV_WAR)
            extraDir = File(buildDir, EXTRA_DIR)
            workDir = File(buildDir, WORK_DIR)
            genDir = File(buildDir, GEN_DIR)
            cacheDir = File(buildDir, CACHE_DIR)
            dev.logDir = File(buildDir, LOG_DIR)
            compiler.localWorkers = Runtime.getRuntime().availableProcessors()
            logLevel = this@GwtBasePlugin.logLevel
            superDev.useClasspathForSrc = true
        }

        (extension as IConventionAware).conventionMapping.map("src") {
            project.files(mainSourceSet.allJava.srcDirs)
                    .plus(project.files(mainSourceSet.output.resourcesDir))
        }

        return extension
    }


    private fun createSuperDevModeTask(project: Project) {
        val superDevTask = project.tasks.create(TASK_GWT_SUPER_DEV, GwtSuperDev::class.java)
        superDevTask.dependsOn(JavaPlugin.COMPILE_JAVA_TASK_NAME, JavaPlugin.PROCESS_RESOURCES_TASK_NAME)
        superDevTask.description = "Runs the GWT super dev mode"
    }

    private fun configureAbstractTasks() {
        project.tasks.withType(AbstractGwtTask::class.java) { task ->
            val conventionMapping = (task as IConventionAware).conventionMapping
            conventionMapping.map("extra") { extension.extraDir }
            conventionMapping.map("workDir") { extension.workDir }
            conventionMapping.map("gen") { extension.genDir }
            conventionMapping.map("cacheDir") { extension.cacheDir }
            // TODO logLevel was introduced to CodeServer in GWT 2.7
            // To not break compatibility with previous versions the conventionMapping is not applied for gwtSuperDev task
            // There should be GWT version depending configuration
            conventionMapping.map("logLevel") { extension.logLevel }
        }
    }

    private fun configureAbstractActionTasks() {
        val javaConvention = javaConvention
        val mainSourceSet = javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        project.tasks.withType(AbstractGwtActionTask::class.java) { task ->
            task.group = GwtBasePlugin.GWT_TASK_GROUP

            val conventionMapping = (task as IConventionAware).conventionMapping
            conventionMapping.map("modules", Callable {
                val devModules = extension.devModules
                if (task.isDevTask && devModules != null && !devModules.isEmpty()) {
                    return@Callable devModules
                }
                extension.modules
            })
            conventionMapping.map("src") { extension.src }
            conventionMapping.map("classpath") { mainSourceSet.compileClasspath.plus(project.files(mainSourceSet.output.classesDir)) }
            conventionMapping.map("minHeapSize") { extension.minHeapSize }
            conventionMapping.map("maxHeapSize") { extension.maxHeapSize }
            conventionMapping.map("sourceLevel") { extension.sourceLevel }
            conventionMapping.map("incremental") { extension.incremental }
            conventionMapping.map("jsInteropMode") { extension.jsInteropMode }
            conventionMapping.map("generateJsInteropExports") { extension.generateJsInteropExports }
            conventionMapping.map("methodNameDisplayMode") { extension.methodNameDisplayMode }
        }
    }

    private fun configureGwtCompile() {
        project.tasks.withType(AbstractGwtCompile::class.java) { task -> task.configure(extension.compiler) }
    }

    private fun configureGwtDev() {
        val debug = "true" == System.getProperty("gwtDev.debug")
        project.tasks.withType(GwtDev::class.java) { task ->
            task.configure(extension)
            task.isDebug = debug
        }
    }

    private fun configureGwtSuperDev() {
        project.tasks.withType(GwtSuperDev::class.java) { task ->
            task.configure(extension.superDev)
            val conventionMapping = (task as IConventionAware).conventionMapping
            conventionMapping.map("workDir") { extension.workDir }
        }
    }

    private fun configureTestTasks(gwtPluginExtension: GwtPluginExtension) {
        project.tasks.withType(Test::class.java) { testTask ->
            testTask.testLogging.showStandardStreams = true

            val testExtension = testTask.extensions.create("gwt", GwtTestExtension::class.java)
            testExtension.configure(gwtPluginExtension, testExtension as IConventionAware)

            project.afterEvaluate {
                val gwtArgs = testExtension.parameterString
                testTask.systemProperty("gwt.args", gwtArgs)
                logger.info("Using gwt.args for test: $gwtArgs")

                if (testExtension.cacheDir != null) {
                    testTask.systemProperty("gwt.persistentunitcachedir", testExtension.cacheDir)
                    testExtension.cacheDir.mkdirs()
                    logger.info("Using gwt.persistentunitcachedir for test: {0}", testExtension.cacheDir)
                }
            }

            project.plugins.withType(GwtWarPlugin::class.java) { testTask.dependsOn(GwtWarPlugin.TASK_WAR_TEMPLATE) }
        }
    }

    // QUIET or ERROR
    private val logLevel: LogLevel
        get() = when {
            logger.isTraceEnabled -> LogLevel.TRACE
            logger.isDebugEnabled -> LogLevel.DEBUG
            logger.isInfoEnabled -> LogLevel.INFO
            logger.isLifecycleEnabled || logger.isWarnEnabled -> LogLevel.WARN
            else -> LogLevel.ERROR
        }

    private val mainSourceSet: SourceSet
        get() = javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)

    private val testSourceSet: SourceSet
        get() = javaConvention.sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME)

    private val javaConvention: JavaPluginConvention
        get() = project.convention.getPlugin(JavaPluginConvention::class.java)

    private fun addToMainSourceSetClasspath(fileCollection: FileCollection) {
        mainSourceSet.compileClasspath = mainSourceSet.compileClasspath.plus(fileCollection)
    }

    companion object {
        @JvmField val GWT_TASK_GROUP = "GWT"

        val GWT_CONFIGURATION = "gwt"
        val GWT_SDK_CONFIGURATION = "gwtSdk"
        val EXTENSION_NAME = "gwt"
        @JvmField val BUILD_DIR = "gwt"
        val EXTRA_DIR = "extra"
        val WORK_DIR = "work"
        val GEN_DIR = "gen"
        val CACHE_DIR = "cache"
        val LOG_DIR = "log"

        val DEV_WAR = "war"

        val TASK_GWT_SUPER_DEV = "gwtSuperDev"

        val GWT_GROUP = "com.google.gwt"
        val GWT_DEV = "gwt-dev"
        val GWT_USER = "gwt-user"
        val GWT_CODESERVER = "gwt-codeserver"
        val GWT_ELEMENTAL = "gwt-elemental"
        val GWT_SERVLET = "gwt-servlet"

        private val logger = Logging.getLogger(GwtBasePlugin::class.java)
    }
}
