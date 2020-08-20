package org.arend.toolWindow.errors.tree

import org.arend.typechecking.error.ArendError

class ArendErrorTreeElement(val errors: MutableList<ArendError>) {
    constructor() : this(ArrayList())

    constructor(error: ArendError) : this(mutableListOf(error))

    val sampleError: ArendError
        get() = errors.first()

    val highestError: ArendError
        get() {
            var result = errors.first()
            for (arendError in errors) {
                if (arendError.error.level > result.error.level) {
                    result = arendError
                }
            }
            return result
        }

    fun add(arendError: ArendError) {
        errors.add(arendError)
    }
}