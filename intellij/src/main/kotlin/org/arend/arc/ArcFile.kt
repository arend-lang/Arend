package org.arend.arc

import com.intellij.psi.FileViewProvider
import org.arend.ArendIcons
import org.arend.ext.module.ModuleLocation
import org.arend.psi.ArendFile

// The PSI of an .arc view; editing features opt out for it. Its module is generated, so that it is not sent to the server
class ArcFile(viewProvider: FileViewProvider) : ArendFile(viewProvider) {
    init {
        (viewProvider.virtualFile as? ArcVirtualFile)?.let {
            generatedModuleLocation = ModuleLocation(it.libraryName, ModuleLocation.LocationKind.GENERATED, it.modulePath)
        }
    }

    // Places that show the PSI file, like ArcFileIconProvider for those that show the virtual file
    override fun getIcon(flags: Int) = (viewProvider.virtualFile as? ArcVirtualFile)?.icon ?: ArendIcons.ARC_FILE
}
