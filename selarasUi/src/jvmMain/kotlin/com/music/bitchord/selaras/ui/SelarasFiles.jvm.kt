package com.music.bitchord.selaras.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
internal actual fun rememberSelarasFiles(): SelarasFiles = remember { SwingSelarasFiles }

private object SwingSelarasFiles : SelarasFiles {
    private val home = File(System.getProperty("user.home"))

    override fun save(suggestedName: String, text: String, onResult: (Result<String>) -> Unit) {
        val c = JFileChooser(home).apply {
            dialogTitle = "Export preset"
            fileFilter = FileNameExtensionFilter("Selaras preset (*.json)", "json")
            selectedFile = File(currentDirectory, suggestedName)
        }
        if (c.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return
        onResult(runCatching { c.selectedFile.writeText(text, Charsets.UTF_8); c.selectedFile.name })
    }

    override fun open(kind: SelarasFileKind, onText: (name: String, text: String) -> Unit) {
        val c = when (kind) {
            SelarasFileKind.PRESET -> JFileChooser(home).apply {
                dialogTitle = "Import preset"
                fileFilter = FileNameExtensionFilter("Selaras preset (*.json)", "json")
            }
            SelarasFileKind.PROFILE -> JFileChooser(File(home, "AutoEq").takeIf { it.isDirectory } ?: home).apply {
                dialogTitle = "Import AutoEq ParametricEQ.txt"
                fileFilter = FileNameExtensionFilter("AutoEq parametric EQ (*.txt)", "txt")
            }
        }
        if (c.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
        val file = c.selectedFile
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return
        onText(file.name, text)
    }
}
