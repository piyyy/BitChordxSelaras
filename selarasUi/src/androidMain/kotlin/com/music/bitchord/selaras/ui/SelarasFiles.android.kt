package com.music.bitchord.selaras.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberSelarasFiles(): SelarasFiles {
    val context = LocalContext.current.applicationContext
    val pending = remember { Pending() }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val job = pending.save ?: return@rememberLauncherForActivityResult
        pending.save = null
        if (uri == null) return@rememberLauncherForActivityResult
        job.second(
            runCatching {
                context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(job.first.toByteArray(Charsets.UTF_8)) }
                displayName(context, uri) ?: "preset"
            },
        )
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val job = pending.open ?: return@rememberLauncherForActivityResult
        pending.open = null
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull() ?: return@rememberLauncherForActivityResult
        job(displayName(context, uri) ?: "Imported", text)
    }

    return remember(saver, opener) {
        object : SelarasFiles {
            override fun save(suggestedName: String, text: String, onResult: (Result<String>) -> Unit) {
                pending.save = text to onResult
                saver.launch(suggestedName)
            }

            override fun open(kind: SelarasFileKind, onText: (name: String, text: String) -> Unit) {
                pending.open = onText
                // Many file managers label AutoEq's .txt as plain text and Selaras' .json as JSON
                // or as nothing at all, so offer both rather than hide the file the user wants.
                opener.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
            }
        }
    }
}

private class Pending {
    var save: Pair<String, (Result<String>) -> Unit>? = null
    var open: ((String, String) -> Unit)? = null
}

private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()
