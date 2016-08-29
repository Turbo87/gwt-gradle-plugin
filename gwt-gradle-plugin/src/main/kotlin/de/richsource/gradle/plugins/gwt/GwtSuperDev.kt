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

import de.richsource.gradle.plugins.gwt.internal.GwtSuperDevOptionsImpl
import org.gradle.api.internal.IConventionAware
import java.io.File

/**
 * Task to run the GWT Super Dev Mode.
 */
open class GwtSuperDev : AbstractGwtActionTask("com.google.gwt.dev.codeserver.CodeServer"), GwtSuperDevOptions {

    private val options = GwtSuperDevOptionsImpl()

    init {
        outputs.upToDateWhen { false }
    }

    override fun addArgs() {
        if (java.lang.Boolean.TRUE != useClasspathForSrc) {
            // TODO warning if file?
            src.filter { it.exists() && it.isDirectory }.forEach { argIfSet("-src", it) }
        }
        dirArgIfSet("-workDir", workDir)
        argIfSet("-bindAddress", bindAddress)
        argIfSet("-port", port)
        argIfEnabled(noPrecompile, "-noprecompile")
        argOnOff(allowMissingSrc, "-allowMissingSrc", "-noallowMissingSrc")
        argOnOff(failOnError, "-failOnError", "-nofailOnError")
        argOnOff(compileTest, "-compileTest", "-nocompileTest")
        argIfSet("-compileTestRecompiles", compileTestRecompiles)
        argIfSet("-launcherDir", launcherDir)
        argOnOff(closureFormattedOutput, "-XclosureFormattedOutput", "-XnoclosureFormattedOutput")
    }


    fun configure(options: GwtSuperDevOptions) {
        with((this as IConventionAware).conventionMapping) {
            map("bindAddress") { options.bindAddress }
            map("port") { options.port }
            map("noPrecompile") { options.noPrecompile }
            map("useClasspathForSrc") { options.useClasspathForSrc }
            map("allowMissingSrc") { options.allowMissingSrc }
            map("failOnError") { options.failOnError }
            map("compileTest") { options.compileTest }
            map("compileTestRecompiles") { options.compileTestRecompiles }
            map("launcherDir") { options.launcherDir }
            map("closureFormattedOutput") { options.closureFormattedOutput }
        }
    }

    override fun prependSrcToClasspath(): Boolean {
        return java.lang.Boolean.TRUE == useClasspathForSrc
    }

    override fun getWorkDir(): File = options.workDir

    override fun setWorkDir(workDir: File) {
        options.workDir = workDir
    }

    override fun getBindAddress(): String = options.bindAddress

    override fun setBindAddress(bindAddress: String) {
        options.bindAddress = bindAddress
    }

    override fun getPort(): Int? = options.port

    override fun setPort(port: Int?) {
        options.port = port
    }

    override fun getNoPrecompile(): Boolean? = options.noPrecompile

    override fun setNoPrecompile(noPrecompile: Boolean?) {
        options.noPrecompile = noPrecompile
    }

    override fun setUseClasspathForSrc(useClasspathForSrc: Boolean?) {
        options.useClasspathForSrc = useClasspathForSrc
    }

    override fun getUseClasspathForSrc(): Boolean? = options.useClasspathForSrc

    override fun setLauncherDir(launcherDir: File) {
        options.launcherDir = launcherDir
    }

    override fun getLauncherDir(): File = options.launcherDir

    override fun setCompileTestRecompiles(compileTestRecompiles: Int?) {
        options.compileTestRecompiles = compileTestRecompiles
    }

    override fun getCompileTestRecompiles(): Int? = options.compileTestRecompiles

    override fun setCompileTest(compileTest: Boolean?) {
        options.compileTest = compileTest
    }

    override fun getCompileTest(): Boolean? = options.compileTest

    override fun setFailOnError(failOnError: Boolean?) {
        options.failOnError = failOnError
    }

    override fun getFailOnError(): Boolean? = options.failOnError

    override fun setAllowMissingSrc(allowMissingSrc: Boolean?) {
        options.allowMissingSrc = allowMissingSrc
    }

    override fun getAllowMissingSrc(): Boolean? = options.allowMissingSrc

    override fun getClosureFormattedOutput(): Boolean? = options.closureFormattedOutput

    override fun setClosureFormattedOutput(closureFormattedOutput: Boolean?) {
        options.closureFormattedOutput = closureFormattedOutput
    }
}
