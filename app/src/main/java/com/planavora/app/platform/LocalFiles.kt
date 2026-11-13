package com.planavora.app.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

object LocalFiles {
    private val types = mapOf("application/pdf" to "pdf", "image/jpeg" to "jpg", "image/png" to "png",
        "image/webp" to "webp", "image/gif" to "gif", "text/plain" to "txt")
    private const val LIMIT = 20 * 1024 * 1024
    private val filename = Regex("[a-f0-9-]{36}\\.(pdf|jpg|png|webp|gif|txt)")

    fun import(context: Context, uri: Uri, photo: Boolean): String {
        val resolver = context.contentResolver
        val type = resolver.getType(uri)
        require(type in types && (!photo || type?.startsWith("image/") == true))
        val destination = File(File(context.filesDir, "attachments").apply { mkdirs() },
            "${UUID.randomUUID()}.${types.getValue(type!!)}")
        try {
            requireNotNull(resolver.openInputStream(uri)).use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(16_384)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= LIMIT)
                        output.write(buffer, 0, count)
                    }
                    require(total > 0)
                }
            }
        } catch (failure: Exception) {
            destination.delete()
            throw failure
        }
        return destination.name
    }

    fun file(context: Context, name: String): File {
        require(filename.matches(name))
        return File(File(context.filesDir, "attachments"), name)
    }

    fun open(context: Context, name: String) {
        val file = file(context, name)
        require(file.isFile)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.attachments", file)
        val type = types.entries.first { it.value == file.extension }.key
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
