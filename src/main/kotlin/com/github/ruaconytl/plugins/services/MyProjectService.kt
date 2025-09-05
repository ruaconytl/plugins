package com.github.ruaconytl.plugins.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.github.ruaconytl.plugins.MyBundle
import com.github.ruaconytl.plugins.model.ImageRow
import com.intellij.openapi.application.ApplicationManager
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.Collections
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO

@Service(Service.Level.PROJECT)
class MyProjectService(private val project: Project) {

    init {
        thisLogger().info(MyBundle.message("projectService", project.name))
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    fun getRandomNumber() = (1..100).random()

    /** Quét ảnh trong thư mục res (giống CheckImageBitDepthTask.groovy) */
    fun scanImages(): List<ImageRow> {
        val results = mutableListOf<ImageRow>()
        val baseDir = File(project.basePath ?: return results)
        val basePath = baseDir.absolutePath

        baseDir.walkTopDown().forEach { f ->
            if (f.isFile && f.extension.equals("png", ignoreCase = true) && f.length() > 1024) {
                try {
                    val img = ImageIO.read(f)
                    if (img != null) {
                        val depth = img.colorModel.pixelSize
                        if (depth == 32) {
                            // Lấy relative path
                            val relPath = f.absolutePath.removePrefix(basePath).trimStart(File.separatorChar)
                            val parts = relPath.split(File.separator)

                            val module = parts.firstOrNull() ?: "unknown"
                            // tìm folder gốc (res/drawable, assets…)
                            val folder = parts.find { it.startsWith("drawable") || it == "assets" || it.startsWith("mipmap") }
                                ?: "unknown"

                            results.add(
                                ImageRow(
                                    selected = false,
                                    path = f.absolutePath,
                                    module = module,
                                    folder = folder,
                                    name = f.name,
                                    sizeKb = f.length() / 1024,
                                    bitDepth = depth
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        return results.sortedByDescending { it.sizeKb}
    }

//    logger("✅ Start Optimize total : ${files.size} files")
//
//    logger("✅ Optimizing: ${f.name}")

    fun optimizeImages(files: List<String>, logger: (String) -> Unit,onProgress: (Int, Int) -> Unit, onComplete: () -> Unit) {
        val executor = Executors.newFixedThreadPool(5)
        val filesSizeOld = AtomicReference(BigDecimal.ZERO)
        val filesSizeNew = AtomicReference(BigDecimal.ZERO)
        val fileCount = AtomicInteger(0)
        val doneCount = AtomicInteger(0)
        val total = files.size
        val mapper = jacksonObjectMapper()
        logger("✅ Start Optimize total : ${files.size} files")
        files.forEach { path ->
            val f = File(path)
            executor.submit {
                try {
                    logger("✅ Optimizing: ${f.name}")
                    val bytes = f.readBytes()
                    val base64 = Base64.getEncoder().encodeToString(bytes)

                    val responseText = postToApi( base64, f.name)
                    val json = mapper.readTree(responseText)

                    val outputUrl = json.at("/output/url").asText()
                    if (!outputUrl.isNullOrEmpty()) {
                        val base64Data = outputUrl.replaceFirst("^data:image/[^;]+;base64,".toRegex(), "")
                        val decodedBytes = Base64.getDecoder().decode(base64Data)

                        val oldSize = BigDecimal(f.length().toDouble() / 1024.0).setScale(2, BigDecimal.ROUND_HALF_UP)
                        filesSizeOld.getAndUpdate { v -> v + oldSize }

                        FileOutputStream(f).use { it.write(decodedBytes) }

                        val newSize = BigDecimal(f.length().toDouble() / 1024.0).setScale(2, BigDecimal.ROUND_HALF_UP)
                        filesSizeNew.getAndUpdate { v -> v + newSize }
                        fileCount.incrementAndGet()

                        // log ngay khi xong 1 file
                        ApplicationManager.getApplication().invokeLater {
                            logger("✅ Optimized: ${f.name} ($oldSize KB → $newSize KB)")
                        }
                    } else {
                        ApplicationManager.getApplication().invokeLater {
                            logger("⚠ ${f.name} -> No output.url in response")
                        }
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        logger("❌ ${f.name} -> ${e::class.java.name} ${e.message}")
                    }
                } finally {
                    val current = doneCount.incrementAndGet()
                    ApplicationManager.getApplication().invokeLater {
                        onProgress(current, total)
                    }
                }
            }
        }

        executor.shutdown()

        // báo cáo tổng sau khi tất cả hoàn tất
        Thread {
            executor.awaitTermination(10, TimeUnit.MINUTES)
            ApplicationManager.getApplication().invokeLater {
                if (fileCount.get() > 0) {
                    val ratio = (BigDecimal.ONE - (filesSizeNew.get().divide(filesSizeOld.get(), 4, BigDecimal.ROUND_HALF_UP))) * BigDecimal(100)
                    logger("==> Done Tiny: ${fileCount.get()} file(s) (${filesSizeOld.get()} KB → ${filesSizeNew.get()} KB, saved $ratio%)")
                } else {
                    logger("❌ Không tối ưu được file nào.")
                }
                onComplete()
            }
        }.start()
    }
   private fun postToApi( base64: String, fileName: String, connectTimeoutMs: Int = 10000): String {
        val isJpeg = fileName.lowercase(Locale.getDefault()).endsWith(".jpg") ||
                fileName.lowercase(Locale.getDefault()).endsWith(".jpeg")
        val baseUrl = "https://egxycyv2l7vhenjqtcha5q77ty0xyvym.lambda-url.ap-southeast-1.on.aws/"
        val url = URL(baseUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = connectTimeoutMs
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")

        val body = """{"input":"$base64","is_jpeg":$isJpeg}"""
        conn.outputStream.use { os ->
            OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                writer.write(body)
            }
        }

        val responseCode = conn.responseCode
        if (responseCode != 200) {
            val errorMsg = conn.errorStream?.bufferedReader()?.readText()
            throw RuntimeException("Server returned $responseCode, msg=$errorMsg in file $fileName")
        }
        return conn.inputStream.bufferedReader().readText()
    }

   private fun formattedFileSizeKB(f: File): String {
        val sizeKb = BigDecimal(f.length().toDouble() / 1024.0)
            .setScale(2, BigDecimal.ROUND_HALF_UP)
        return "$sizeKb KB"
    }

}
