package org.arend.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil


@State(
    name = "ArendSettings",
    storages = [Storage("arend.xml")]
)
class ArendSettings : PersistentStateComponent<ArendSettings> {
    enum class MatchingCommentStyle {
        DO_NOTHING { override fun toString() = "Do nothing" },
        INSERT_MINUS { override fun toString() = "Insert another '-'" },
        REPLACE_BRACE { override fun toString() = "Replace '}' with '-'" }
    }

    enum class TypecheckingMode {
        SMART { override fun toString() = "Smart" },
        DUMB { override fun toString() = "Dumb" },
        OFF { override fun toString() = "Off" }
    }

    var matchingCommentStyle = MatchingCommentStyle.REPLACE_BRACE
    var autoImportOnTheFly = false

    // Background typechecking
    var typecheckingMode = TypecheckingMode.SMART
    var withTimeLimit = true
    var typecheckingTimeLimit = 5

    // Other settings
    var withClauseLimit = true
    var clauseLimit = 10

    val clauseActualLimit: Int?
        get() = if (withClauseLimit) clauseLimit else null

    override fun getState() = this

    override fun loadState(state: ArendSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}