package org.arend.module

import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.impl.ZipHandler
import org.arend.module.config.ArendModuleConfigService
import org.arend.prelude.Prelude
import org.arend.settings.ArendProjectSettings
import org.arend.settings.ArendSettings
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.util.*
import java.io.*
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption


const val AREND_REPO = "arend-lang/Arend"
const val AREND_LIB = "arend-lib"

@Throws(IOException::class)
internal fun getVersion(): String? {
    val versionsConn = URL("https://raw.githubusercontent.com/$AREND_REPO/master/$AREND_LIB/versions").openConnection()
    BufferedReader(InputStreamReader(versionsConn.getInputStream())).use { reader ->
        while (true) {
            val str = reader.readLine() ?: break
            val index = str.indexOf("->")
            if (index < 0) continue
            val range = VersionRange.parseVersionRange(str.take(index)) ?: continue
            if (!range.inRange(Prelude.VERSION)) continue
            val result = str.substring(index + 2, str.length).trim()
            if (result.isNotEmpty()) return@getVersion result
        }
        return Prelude.VERSION.longString
    }
}

/**
 * Copies [input] into [target], via a temporary file beside it that is moved into place only once
 * the copy is complete.
 *
 * Writing straight to [target] would truncate it before the first byte arrived, so a cancelled or
 * failed transfer would leave a corrupt file where a usable one had been -- and on Windows the
 * truncated file could not even be deleted afterwards, because the output stream was still open.
 * Here [target] is untouched unless the whole copy succeeded.
 *
 * The partial file outlives the call only if the JVM itself does not, and nothing picks one up:
 * `VirtualFile.configFile` reports a library for a directory, an `arend.yaml` or a `*.zip`, and for
 * nothing else.
 *
 * Returns false if [isCanceled] reported cancellation, in which case nothing was written to
 * [target]. Progress is reported to [onFraction] only when [size] is known, i.e. not negative, and
 * is clamped to 1.0 in case the stream turns out to be longer than [size] claimed.
 */
@Throws(IOException::class)
internal fun downloadTo(input: InputStream, target: Path, size: Long, isCanceled: () -> Boolean, onFraction: (Double) -> Unit): Boolean {
    // Not a plain `!!`: this is the one precondition of a function that otherwise reports every
    // failure as an IOException, and its callers already handle that.
    val dir = target.parent ?: throw IOException("Cannot download to '$target': it has no parent directory")
    val temp = Files.createTempFile(dir, target.fileName.toString() + "-", ".part")
    try {
        Files.newOutputStream(temp).use { output ->
            val buffer = ByteArray(8 * 1024)
            var totalRead: Long = 0
            while (true) {
                if (isCanceled()) {
                    return false
                }

                val s = input.read(buffer, 0, buffer.size)
                if (s < 0) {
                    break
                }
                output.write(buffer, 0, s)

                if (size >= 0) {
                    totalRead += s
                    onFraction(minOf(1.0, totalRead.toDouble() / size))
                }
            }
        }

        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        return true
    } finally {
        // A no-op once the move above has renamed it away.
        try {
            Files.deleteIfExists(temp)
        } catch (_: IOException) {
        }
    }
}

private fun downloadArendLib(project: Project, indicator: ProgressIndicator, path: Path, ver: String?): Boolean {
    try {
        val version = ver ?: getVersion()
        if (version == null) {
            NotificationErrorReporter.errorNotifications.createNotification("$AREND_LIB does not support language version ${Prelude.VERSION}", NotificationType.ERROR).notify(project)
            return false
        }

        val conn = URL("https://github.com/$AREND_REPO/releases/download/v${version}/$AREND_LIB.zip").openConnection()
        val size = conn.contentLengthLong
        if (size < 0) {
            indicator.isIndeterminate = true
        }

        return BufferedInputStream(conn.getInputStream()).use { input ->
            downloadTo(input, path, size, { indicator.isCanceled }, { indicator.fraction = it })
        }
    } catch (e: IOException) {
        NotificationErrorReporter.errorNotifications.createNotification("An exception happened during downloading of $AREND_LIB", e.toString(), NotificationType.ERROR).notify(project)
        return false
    }
}

fun checkForUpdates(project: Project, actualVersion: Version?) {
    ApplicationManager.getApplication().executeOnPooledThread {
        val newVersion = try { Version.fromString(getVersion()) } catch (e: IOException) { null }
        if (newVersion != null && (actualVersion == null || newVersion > actualVersion)) {
            showDownloadNotification(project, Reason.UPDATE, newVersion.longString)
        }
    }
}

enum class Reason { WRONG_VERSION, MISSING, UPDATE }

fun showDownloadNotification(project: Project, reason: Reason, version: String? = null, details: String = "") {
    val libRoot = project.service<ArendProjectSettings>().librariesRoot.let { if (it.isNotEmpty()) Paths.get(it) else FileUtils.defaultLibrariesRoot() }
    val message = when (reason) {
        Reason.WRONG_VERSION -> "'$AREND_LIB' has a wrong version"
        Reason.MISSING -> "'$AREND_LIB' is missing"
        Reason.UPDATE -> "A newer version of '$AREND_LIB' is available"
    }
    val notification = NotificationGroupManager.getInstance().getNotificationGroup("Arend Library Update")
        .createNotification(message, details, if (reason == Reason.UPDATE) NotificationType.INFORMATION else NotificationType.ERROR)

    notification.addAction(object : NotificationAction("Download $AREND_LIB") {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
            notification.expire()
            if (Files.exists(libRoot) || libRoot.toFile().mkdirs()) {
                val zipFile = libRoot.resolve(AREND_LIB + FileUtils.ZIP_EXTENSION)
                if (!Files.exists(zipFile) || Files.isRegularFile(zipFile)) {
                    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Downloading $AREND_LIB", true) {
                        override fun run(indicator: ProgressIndicator) {
                            if (downloadArendLib(project, indicator, zipFile, version)) {
                                // The archive was replaced at a path the platform has already read. Its jar-fs
                                // view is served from ZipHandler's file-accessor cache, which a VFS refresh does
                                // not invalidate, so the cache is dropped first and the VFS refreshed after --
                                // otherwise the config, the sources and the extension classes can all still come
                                // from the archive that was just overwritten.
                                ZipHandler.clearFileAccessorCache()
                                refreshLibrariesDirectory(project.service<ArendProjectSettings>().librariesRoot)
                                project.findExternalLibrary(libRoot, AREND_LIB)?.root?.let {
                                    VfsUtil.markDirtyAndRefresh(false, true, true, it)
                                    for (module in project.arendModules) {
                                        ArendModuleConfigService.getInstance(module)?.synchronizeDependencies(false)
                                    }
                                    project.service<ReloadLibrariesService>().reload(onlyInternal = false, refresh = false)
                                }
                            }
                        }
                    })
                } else {
                    NotificationErrorReporter.errorNotifications.createNotification("", "Cannot open $zipFile", NotificationType.ERROR).notify(project)
                }
            } else {
                NotificationErrorReporter.errorNotifications.createNotification("", "Cannot create directory $libRoot", NotificationType.ERROR).notify(project)
            }
        }
    })

    notification.addAction(object : NotificationAction("Dismiss") {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
            notification.expire()
        }
    })

    if (reason == Reason.UPDATE) {
        notification.addAction(object : NotificationAction("Do not show again") {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                service<ArendSettings>().checkForUpdates = false
                notification.expire()
            }
        })
    }

    Notifications.Bus.notify(notification, project)
}
