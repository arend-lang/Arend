package org.arend.typechecking.error

import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import org.arend.ext.error.ErrorReporter
import org.arend.ext.error.GeneralError
import org.arend.ext.error.GeneralError.Level
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.doc.DocStringBuilder
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer


class NotificationErrorReporter(private val project: Project, private val ppConfig: PrettyPrinterConfig = PrettyPrinterConfig.DEFAULT): ErrorReporter {
    companion object {
        val ERROR_NOTIFICATIONS = NotificationGroup("Arend Error Messages", NotificationDisplayType.STICKY_BALLOON, true)
        val WARNING_NOTIFICATIONS = NotificationGroup("Arend Warning Messages", NotificationDisplayType.BALLOON, true)
        val INFO_NOTIFICATIONS = NotificationGroup("Arend Info Messages", NotificationDisplayType.NONE, true)
    }

    override fun report(error: GeneralError) {
        val group = when (error.level) {
            Level.ERROR -> ERROR_NOTIFICATIONS
            Level.WARNING, Level.WARNING_UNUSED, Level.GOAL -> WARNING_NOTIFICATIONS
            Level.INFO -> INFO_NOTIFICATIONS
        }
        val type = when (error.level) {
            Level.ERROR -> NotificationType.ERROR
            Level.WARNING, Level.WARNING_UNUSED, Level.GOAL -> NotificationType.WARNING
            Level.INFO -> NotificationType.INFORMATION
        }
        val newPPConfig = PrettyPrinterConfigWithRenamer(ppConfig)
        val title = DocStringBuilder.build(error.getHeaderDoc(newPPConfig))
        val content = DocStringBuilder.build(error.getBodyDoc(newPPConfig))
        group.createNotification(title, content, type, null).notify(project)
    }

    fun info(msg: String) {
        INFO_NOTIFICATIONS.createNotification(msg, NotificationType.INFORMATION).notify(project)
    }

    fun warn(msg: String) {
        WARNING_NOTIFICATIONS.createNotification(msg, NotificationType.WARNING).notify(project)
    }
}