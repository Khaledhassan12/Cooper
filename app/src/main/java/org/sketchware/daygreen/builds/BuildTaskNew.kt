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
        fun onTaskFailed(taskName: String, error: Throwable)
        fun onBuildFinished(result: Result, totalMillis: Long)
    }

    private fun step(name: String, stepNo: Int, body: Step) {
        if (canceled.asBoolean) throw CancelledException()
        receiver.onProgress(name, stepNo)
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

    private fun interface Step {
        @Throws(Exception::class)
        fun run()
    }

    private class CancelledException : Exception()

    @Throws(Exception::class)
    fun run(): Boolean {
        val totalStartTime = System.currentTimeMillis()
        try {
            val builder = ProjectBuilder(receiver, context, q)

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
                BuiltInLibraries.extractCompileAssets(receiver)
            }

            step("compileResources", 5) {
                builder.compileResources()
            }

            step("generateViewBinding", 6) {
                builder.generateViewBinding()
            }

            step("compileKotlin", 7) {
                KotlinCompilerBridge.compileKotlinCodeIfPossible(receiver, builder)
            }

            step("compileAidl", 8) {
                builder.compileAidl()
            }

            step("compileJavaCode", 9) {
                builder.compileJavaCode()
            }

            step("compileNativeCode", 10) {
                builder.compileNativeCode()
            }

            step("stringFog", 11) {
                StringfogHandler(scId).start(receiver, builder)
            }

            step("proguard", 12) {
                ProguardHandler(scId).start(receiver, builder)
            }

            step("dexBuilderDebug", 13) {
                builder.createDexFilesFromClasses()
            }

            step("mergeDexDebug", 14) {
                builder.getDexFilesReady()
            }

            step("packageDebug", 15) {
                builder.buildApk()
            }

            step("signDebug", 16) {
                builder.signDebugApk()
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