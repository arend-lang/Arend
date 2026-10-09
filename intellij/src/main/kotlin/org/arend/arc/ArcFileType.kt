package org.arend.arc

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import org.arend.ArendIcons
import org.arend.ArendLanguage
import org.arend.util.FileUtils

// An .arc file on disk: a binary cache, which is not opened; its module is shown by an .arc view (ArcViewFileType)
open class ArcFileType : FileType {
    override fun getName(): String = "Arc"

    override fun getDescription(): String = "Arc files"

    override fun getDefaultExtension(): String = FileUtils.SERIALIZED_EXTENSION.drop(1)

    override fun getIcon() = ArendIcons.AREND_FILE

    override fun isBinary(): Boolean = true

    companion object {
        val INSTANCE = ArcFileType()
    }
}

// The file type of the files of ArcFileSystem: read-only Arend text
class ArcViewFileType private constructor() : LanguageFileType(ArendLanguage.INSTANCE, true) {
    override fun getName(): String = "Arc view"

    override fun getDescription(): String = "The modules shown as .arc files"

    override fun getDefaultExtension(): String = ""

    // Editor tabs of .arc views, like the views in the project view
    override fun getIcon() = ArendIcons.ARC_FILE

    override fun isReadOnly(): Boolean = true

    companion object {
        @JvmField
        val INSTANCE = ArcViewFileType()
    }
}
