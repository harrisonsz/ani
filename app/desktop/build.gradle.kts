/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.atomicfu")
    idea
}

dependencies {
    implementation(projects.app.shared)
    implementation(projects.app.shared.uiFoundation)
    implementation(projects.app.shared.application)
    implementation(compose.components.resources)
    implementation(libs.log4j.core)
    implementation(libs.vlcj)
}

// workaround for compose limitation
tasks.named("processResources") {
    dependsOn(":app:shared:desktopProcessResources")
    dependsOn(":app:shared:ui-foundation:desktopProcessResources")
}

sourceSets {
    main {
        resources.srcDirs(
            projects.app.shared.dependencyProject.layout.buildDirectory
                .file("processedResources/desktop/main"),
            projects.app.shared.uiFoundation.dependencyProject.layout.buildDirectory
                .file("processedResources/desktop/main"),
        )
    }
}

compose.desktop {
    application {
        jvmArgs(
            "-XX:+UseZGC",
            "-Dorg.slf4j.simpleLogger.defaultLogLevel=TRACE",
            "-Dsun.java2d.metal=true",
            "-Djogamp.debug.JNILibLoader=true", // JCEF 加载 native 库的日志, 方便 debug
            // JCEF
            "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "-Xmx256m",
        )
        if (getOs() == Os.MacOS) {
            jvmArgs(
                "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
            )
        }
        mainClass = "me.him188.ani.app.desktop.AniDesktop"
        nativeDistributions {
            modules(
                "jdk.unsupported", // sun.misc.Unsafe used by androidx datastore
                "java.management", // javax.management.MBeanRegistrationException
                "java.net.http",
                "jcef",
                "gluegen.rt",
                "jogl.all",
            )

            // ./gradlew suggestRuntimeModules

            appResourcesRootDir.set(file("appResources"))
            targetFormats(
                *buildList {
                    add(TargetFormat.Deb)
                    add(TargetFormat.Rpm)
                    add(TargetFormat.Dmg)
//                if (getOs() == Os.Windows) {
//                    add(TargetFormat.AppImage) // portable distribution (installation-free)
//                }
                }.toTypedArray(),
            )
            packageName = "Ani"
            description = project.description
            vendor = "Him188"

            val projectVersion = project.version.toString() // 3.0.0-beta22
            macOS {
                dockName = "Animeko"
                pkgPackageVersion = projectVersion
                pkgPackageBuildVersion = projectVersion
                iconFile.set(file("icons/a_512x512.icns"))
//                iconFile.set(project(":app:shared").projectDir.resolve("androidRes/mipmap-xxxhdpi/a.png"))
                infoPlist {
                    extraKeysRawXml = macOSExtraPlistKeys
                }
            }
            windows {
                this.upgradeUuid = UUID.randomUUID().toString()
                iconFile.set(file("icons/a_1024x1024_rounded.ico"))
            }

            // adding copyright causes package to fail.
//            copyright = """
//                    Ani
//                    Copyright (C) 2022-2024 Him188
//
//                    This program is free software: you can redistribute it and/or modify
//                    it under the terms of the GNU General Public License as published by
//                    the Free Software Foundation, either version 3 of the License, or
//                    (at your option) any later version.
//
//                    This program is distributed in the hope that it will be useful,
//                    but WITHOUT ANY WARRANTY; without even the implied warranty of
//                    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//                    GNU General Public License for more details.
//
//                    You should have received a copy of the GNU General Public License
//                    along with this program.  If not, see <https://www.gnu.org/licenses/>.
//            """.trimIndent()
            licenseFile.set(rootProject.rootDir.resolve("LICENSE.txt"))
            packageVersion = properties["package.version"].toString()
        }

        buildTypes.release.proguard {
            isEnabled.set(false)
        }
        // TODO: Uncomment this to enable proguard for desktop, need some tweaks
//        buildTypes.release.proguard {
//            this.configurationFiles.from(project(":app:shared").file("proguard-rules.pro"))
//            this.configurationFiles.from(file("proguard-desktop.pro"))
//        }
    }
}

afterEvaluate {
    val os = getOs()
    when (os) {
        Os.Windows -> {
            tasks.named("createRuntimeImage", AbstractJLinkTask::class) {
                val dirsNames = listOf(
                    // From your (JBR's) Java Home to Packed Java Home 
                    "bin/jcef_helper.exe" to "bin/jcef_helper.exe",
                    "bin/icudtl.dat" to "bin/icudtl.dat",
                    "bin/v8_context_snapshot.bin" to "bin/v8_context_snapshot.bin",
                )

                dirsNames.forEach { (sourcePath, destPath) ->
                    val source = File(javaHome.get()).resolve(sourcePath)
                    inputs.file(source)
                    val dest = destinationDir.file(destPath)
                    outputs.file(dest)
                    doLast("copy $sourcePath") {
                        source.copyTo(dest.get().asFile)
                        logger.info("Copied $source to $dest")
                    }
                }
            }
        }

        Os.MacOS -> {
            tasks.named("createRuntimeImage", AbstractJLinkTask::class) {
                val dirsNames = listOf(
                    // From your (JBR's) Java Home to Packed Java Home 
                    "../Frameworks" to "lib/",
                )

                dirsNames.forEach { (sourcePath, destPath) ->
                    val source = File(javaHome.get()).resolve(sourcePath).normalize()
                    inputs.dir(source)
                    val dest = destinationDir.file(destPath)
                    outputs.dir(dest)
                    doLast("copy $sourcePath") {
                        ProcessBuilder().run {
                            command("cp", "-r", source.absolutePath, dest.get().asFile.normalize().absolutePath)
                            inheritIO()
                            start()
                        }.waitFor().let {
                            if (it != 0) {
                                throw GradleException("Failed to copy $sourcePath")
                            }
                        }
                        logger.info("Copied $source to $dest")
                    }
                }
            }
        }

        Os.Linux -> {}
        Os.Unknown -> {}
    }
}

val macOSExtraPlistKeys: String
    get() = """
        <key>CFBundleURLTypes</key>
        <array>
            <dict>
                <key>CFBundleURLName</key>
                <string>me.him188.ani</string>
                <key>CFBundleURLSchemes</key>
                <array>
                    <string>ani</string>
                </array>
            </dict>
        </array>
    """.trimIndent()

// workaround for CMP resources bug
tasks.withType(KotlinCompilationTask::class) {
    dependsOn("generateComposeResClass")
}

//kotlin.sourceSets.main.get().resources.srcDir(project(":common").projectDir.resolve("src/androidMain/res/raw"))

tasks.withType(AbstractJPackageTask::class) {
    doLast {
        fun unpackJar(jar: File, dest: File, filter: (ZipEntry) -> Boolean = { true }) {
            val zip = ZipFile(jar)
            zip.use {
                zip.entries().asSequence().filter(filter).forEach { entry ->
                    val file = dest.resolve(entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        }

        val jarsToUnpack = listOf("anitorrent-native")

        destinationDir.get().asFile.walk().firstOrNull { file ->
            jarsToUnpack.any { file.name.startsWith(it) && file.extension == "jar" }
        }?.let { file ->
            unpackJar(file, file.parentFile) {
                it.name.endsWith("dylib") || it.name.endsWith("so") || it.name.endsWith("dll")
            }
        }
    }
}

idea {
    module {
        excludeDirs.add(file("appResources/macos-x64/lib"))
        excludeDirs.add(file("appResources/macos-x64/plugins"))
        excludeDirs.add(file("appResources/macos-arm64/lib"))
        excludeDirs.add(file("appResources/macos-arm64/plugins"))
        excludeDirs.add(file("appResources/windows-x64/lib"))
        excludeDirs.add(file("test-sandbox"))
    }
}
