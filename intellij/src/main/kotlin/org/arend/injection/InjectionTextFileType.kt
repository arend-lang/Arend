package org.arend.injection

import com.intellij.openapi.fileTypes.LanguageFileType
import org.arend.InjectionTextLanguage
import javax.swing.Icon


object InjectionTextFileType : LanguageFileType(InjectionTextLanguage.INSTANCE) {
    override fun getName() = "INJECTION_TEXT"

    override fun getDefaultExtension() = "itxt"

    override fun getDescription() = "Injection text"

    override fun getIcon(): Icon? = null
}