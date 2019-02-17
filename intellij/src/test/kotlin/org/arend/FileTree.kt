package org.arend

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import org.arend.util.FileUtils
import org.intellij.lang.annotations.Language
import org.arend.psi.parentOfType

fun fileTree(builder: FileTreeBuilder.() -> Unit): FileTree =
        FileTree(FileTreeBuilderImpl().apply { builder() }.intoDirectory())

fun fileTreeFromText(@Language("Arend") text: String): FileTree {
    val fileSeparator = """^\s* --! (\S+)\s*$""".toRegex(RegexOption.MULTILINE)
    val fileNames = fileSeparator.findAll(text).map { it.groupValues[1] }.toList()
    val fileTexts = fileSeparator.split(text).filter(String::isNotBlank).map { it.trimIndent() }

    check(fileNames.size == fileTexts.size) { "Have you placed `--! filename.ard` markers?" }

    fun fill(dir: Entry.Directory, path: List<String>, contents: String) {
        val name = path.first()
        if (path.size == 1) {
            dir.children[name] = Entry.File(contents)
        } else {
            val childDir = dir.children.getOrPut(name) { Entry.Directory(mutableMapOf()) } as Entry.Directory
            fill(childDir, path.drop(1), contents)
        }
    }

    return FileTree(Entry.Directory(mutableMapOf()).apply {
        for ((path, contents) in fileNames.map { it.split("/") }.zip(fileTexts)) {
            fill(this, path, contents)
        }
    })
}

interface FileTreeBuilder {

    fun dir(name: String, builder: FileTreeBuilder.() -> Unit)

    fun file(name: String, code: String)

    fun arend(name: String, @Language("Arend") code: String) = file(name, code)
}

class FileTree(private val rootDirectory: Entry.Directory) {
    fun create(project: Project, directory: VirtualFile): TestProject {
        val filesWithCaret = mutableListOf<String>()

        fun go(dir: Entry.Directory, root: VirtualFile, parentComponents: List<String> = emptyList()) {
            for ((name, entry) in dir.children) {
                val components = parentComponents + name
                when (entry) {
                    is Entry.File -> {
                        val vFile = root.findChild(name) ?: root.createChildData(root, name)
                        VfsUtil.saveText(vFile, replaceCaretMarker(entry.text))
                        if (hasCaretMarker(entry.text) || "--^" in entry.text) {
                            filesWithCaret += components.joinToString("/")
                        }
                    }
                    is Entry.Directory -> {
                        go(entry, root.createChildDirectory(root, name), components)
                    }
                }
            }
        }

        runWriteAction {
            go(rootDirectory, directory)
            fullyRefreshDirectory(directory)
        }

        return TestProject(project, directory, filesWithCaret)
    }

    fun assertEquals(baseDir: VirtualFile) {
        fun go(expected: Entry.Directory, actual: VirtualFile) {
            val actualChildren = actual.children.filter { it.name != FileUtils.LIBRARY_CONFIG_FILE }.associateBy { it.name }
            check(expected.children.keys == actualChildren.keys) {
                "Mismatch in directory ${actual.path}\n" +
                        "Expected: ${expected.children.keys}\n" +
                        "Actual: ${actualChildren.keys}"
            }

            for ((name, entry) in expected.children) {
                val a = actualChildren[name]!!
                when (entry) {
                    is Entry.File -> {
                        check(!a.isDirectory)
                        val actualText = String(a.contentsToByteArray(), Charsets.UTF_8)
                        check(entry.text == actualText) {
                            "Expected:\n${entry.text}\nGot:\n$actualText"
                        }
                    }
                    is Entry.Directory -> go(entry, a)
                }
            }
        }

        go(rootDirectory, baseDir)
    }

    companion object {
        private fun fullyRefreshDirectory(directory: VirtualFile) {
            VfsUtil.markDirtyAndRefresh(false, true, true, directory)
        }
    }
}

class TestProject(
        private val project: Project,
        val root: VirtualFile,
        val filesWithCaret: List<String>
) {

    val fileWithCaret: String get() = filesWithCaret.singleOrNull()!!

    inline fun <reified T : PsiElement> findElementInFile(path: String): T {
        val element = doFindElementInFile(path)
        return element.parentOfType()
                ?: error("No parent of type ${T::class.java} for ${element.text}")
    }

    fun doFindElementInFile(path: String): PsiElement {
        val vFile = root.findFileByRelativePath(path)
                ?: error("No `$path` file in test project")
        val file = PsiManager.getInstance(project).findFile(vFile)!!
        return findElementInFile(file, "^")
    }

    fun psiFile(path: String): PsiFileSystemItem {
        val vFile = root.findFileByRelativePath(path)
                ?: error("Can't find `$path`")
        val psiManager = PsiManager.getInstance(project)
        return if (vFile.isDirectory) psiManager.findDirectory(vFile)!! else psiManager.findFile(vFile)!!
    }
}

private class FileTreeBuilderImpl(
        val directory: MutableMap<String, Entry> = mutableMapOf()
) : FileTreeBuilder {

    override fun dir(name: String, builder: FileTreeBuilder.() -> Unit) {
        check('/' !in name) { "Bad directory name `$name`" }
        directory[name] = FileTreeBuilderImpl().apply { builder() }.intoDirectory()
    }

    override fun file(name: String, code: String) {
        check('/' !in name && '.' in name) { "Bad file name `$name`" }
        directory[name] = Entry.File(code.trimIndent())
    }

    fun intoDirectory(): Entry.Directory = Entry.Directory(directory)
}

sealed class Entry {
    class File(val text: String) : Entry()
    class Directory(val children: MutableMap<String, Entry>) : Entry()
}

private fun findElementInFile(file: PsiFile, marker: String): PsiElement {
    val markerOffset = file.text.indexOf(marker)
    check(markerOffset != -1) { "No `$marker` in \n${file.text}" }

    val doc = PsiDocumentManager.getInstance(file.project).getDocument(file)!!
    val markerLine = doc.getLineNumber(markerOffset)
    val makerColumn = markerOffset - doc.getLineStartOffset(markerLine)
    val elementOffset = doc.getLineStartOffset(markerLine - 1) + makerColumn

    return file.findElementAt(elementOffset) ?:
            error { "No anchor found, offset = $elementOffset" }
}

fun replaceCaretMarker(text: String): String = text.replace("{-caret-}", "<caret>")

fun hasCaretMarker(text: String): Boolean = text.contains("{-caret-}")
