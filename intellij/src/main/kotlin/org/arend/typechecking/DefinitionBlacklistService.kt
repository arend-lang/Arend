package org.arend.typechecking

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import org.arend.settings.ArendSettings
import org.arend.typechecking.computation.CancellationIndicator
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class DefinitionBlacklistService {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val blackList = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())

    fun runTimed(any: Any, cancellationIndicator: CancellationIndicator, action: () -> Unit): Boolean {
        var timedOut = false
        val arendSettings = service<ArendSettings>()
        val handler = if (arendSettings.withTimeLimit) {
            scheduler.schedule({
                cancellationIndicator.cancel()
                timedOut = true
            }, arendSettings.typecheckingTimeLimit.toLong(), TimeUnit.SECONDS)
        } else null
        action()
        handler?.cancel(true)
        if (timedOut) {
            blackList.add(any)
        }
        return !timedOut
    }

    fun isBlacklisted(any: Any) = blackList.contains(any)

    fun removeFromBlacklist(any: Any, time: Int) =
        time < service<ArendSettings>().typecheckingTimeLimit && blackList.remove(any)
}