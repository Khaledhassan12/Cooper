package org.sketchware.daygreen.builds

import android.content.Context
import java.io.File
import java.util.function.BooleanSupplier
import extensions.anbui.daydream.configs.Configs
import mod.hey.studios.compiler.kotlin.KotlinCompilerBridge
import mod.hey.studios.project.proguard.ProguardHandler
import mod.hey.studios.project.stringfog.StringfogHandler
import mod.jbk.build.BuildProgressReceiver
import mod.jbk.build.BuiltInLibraries
import pro.sketchware.utility.FileUtil
import a.a.a.ProjectBuilder
import a.a.a.jC
import a.a.a.lC
import a.a.a.wq
import a.a.a.yB
import a.a.a.yq
import mod.hey.studios.build.BuildSettings

class BuildTaskNew @JvmOverloads constructor(
    private val context: Context,
    private val q: yq,
    private val scId: String,
    private val receiver: BuildProgressReceiver,
    private val listener: Listener,
    private val canceled: BooleanSupplier
) {

    enum class Result { SUCCESS, FAILED, CANCELLED }

    interface Listener {
        fun onTaskStart(taskName: String)
        fun onTaskDone(taskName: String, millis: Long)
        fun onTaskSkipped(taskName: String)
        fun onTaskFailed(taskName: String, error: Throwable)
        fun onBuildFinished(result: Result, totalMillis: Long)
    }

    private fun step(
        name: String,
        stepNo: Int,
        skipIf: () -> Boolean = { false },
        body: Step
    ) {
        if (canceled.asBoolean) throw CancelledException()

        if (skipIf()) {
            listener.onTaskSkipped(name)
            return
        }

        receiver.onProgress("Task: $name", stepNo)
        listener.onTaskStart(name)
        val startTime = System.currentTimeMillis()
        try {
            body.run()
            listener.onTaskDone(name, System.currentTimeMillis() - startTime)
        } catch (e: Exception) {
            listener.onTaskFailed(name, e)
            throw e
        }
    }

    private fun hasFiles(dir: File): Boolean =
        dir.isDirectory && dir.walkTopDown().any { it.isFile }

    private fun interface Step {
        @Throws(Exception::class)
        fun run()
    }

    private class CancelledException : Exception()

    private lateinit var builder: ProjectBuilder
    
    // Silent receiver untuk menyembunyikan log dari compiler internal
    private val silentReceiver = BuildProgressReceiver { _, _ -> }

    @Throws(Exception::class)
    fun run(): Boolean {
        val totalStartTime = System.currentTimeMillis()
        
        // Membaca pengaturan build (Debug atau Release)
        val buildSettings = BuildSettings(scId)
        val isRelease = buildSettings.getValue(BuildSettings.SETTING_BUILD_MODE, BuildSettings.BUILD_MODE_DEBUG) == BuildSettings.BUILD_MODE_RELEASE
        val modeName = if (isRelease) "Release" else "Debug"

        try {
            step("deleteTempFiles", 1) {
                FileUtil.deleteFile(q.projectMyscPath)
            }

            step("generateSourceCode", 2) {
                q.c(context)
                q.a()
                q.a(context, wq.e("600"))

                if (yB.a(lC.b(scId), "custom_icon")) {
                    q.aa(wq.e() + File.separator + scId + File.separator + "mipmaps")
                    if (yB.a(lC.b(scId), "isIconAdaptive", false)) {
                        q.createLauncherIconXml(
                            """<?xml version="1.0" encoding="utf-8"?>
                            <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                                <background android:drawable="@mipmap/ic_launcher_background"/>
                                <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
                                <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>
                            </adaptive-icon>""".trimIndent()
                        )
                    } else {
                        q.a(wq.e() + File.separator + scId + File.separator + "icon.png")
                    }
                }

                jC.d(scId).b(q.resDirectoryPath + File.separator + "drawable-xhdpi")
                jC.d(scId).c(q.resDirectoryPath + File.separator + "raw")
                jC.d(scId).a(q.assetsPath + File.separator + "fonts")
                
                builder = ProjectBuilder(silentReceiver, context, q)
            }

            step("prepareProject", 3) {
                val fileManager = jC.b(scId)
                val dataManager = jC.a(scId)
                val libraryManager = jC.c(scId)

                q.a(libraryManager, fileManager, dataManager)
                builder.buildBuiltInLibraryInformation()
                q.b(fileManager, dataManager, libraryManager, builder.builtInLibraryManager)
                q.f()
                q.e()
                builder.maybeExtractAapt2()
            }

            step("extractBuiltInLibraries", 4) {
                BuiltInLibraries.extractCompileAssets(silentReceiver)
            }

            step(
                name = "mergeAssets",
                stepNo = 5,
                skipIf = { !hasFiles(File(q.assetsPath)) }
            ) {
                // isi jika ada penyalinan aset tambahan
            }

            step("compileResources", 6) {
                builder.compileResources()
            }

            step("generateViewBinding", 7) {
                builder.generateViewBinding()
            }

            step("compileKotlin", 8) {
                KotlinCompilerBridge.compileKotlinCodeIfPossible(silentReceiver, builder)
            }

            step("compileAidl", 9) {
                builder.compileAidl()
            }

            step("compileJavaCode", 10) {
                builder.compileJavaCode()
            }

            step("compileNativeCode", 11) {
                builder.compileNativeCode()
            }

            step("stringFog", 12) {
                StringfogHandler(scId).start(silentReceiver, builder)
            }

            step("proguard", 13) {
                ProguardHandler(scId).start(silentReceiver, builder)
            }

            step("dexBuilder$modeName", 14) {
                builder.createDexFilesFromClasses()
            }

            step("mergeDex$modeName", 15) {
                builder.getDexFilesReady()
            }

            step("package$modeName", 16) {
                builder.buildApk()
            }

            step("sign$modeName", 17) {
                if (isRelease) {
                    builder.signReleaseApk()
                } else {
                    builder.signDebugApk()
                }
            }

        } catch (e: CancelledException) {
            listener.onBuildFinished(Result.CANCELLED, System.currentTimeMillis() - totalStartTime)
            return false
        } catch (e: Exception) {
            listener.onBuildFinished(Result.FAILED, System.currentTimeMillis() - totalStartTime)
            throw e
        }

        listener.onBuildFinished(Result.SUCCESS, System.currentTimeMillis() - totalStartTime)
        return true
    }
}