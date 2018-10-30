package org.arend.module

import org.arend.psi.ArendFile
import org.arend.source.Source
import org.arend.source.SourceLoader


class ArendRawSource(private val file: ArendFile): Source {
    override fun getModulePath() = file.modulePath

    override fun load(sourceLoader: SourceLoader) = true

    override fun getTimeStamp() = file.virtualFile?.timeStamp ?: -1

    override fun isAvailable() = file.isValid

    override fun toString() = file.fullName
}