package org.arend.util

import org.arend.naming.reference.Referable
import org.arend.term.concrete.Concrete
import org.arend.term.concrete.Concrete.ResolvableDefinition
import org.arend.term.concrete.SearchConcreteVisitor

class LocalVariablesScopeCollector(private val myAnchor: Any?) : SearchConcreteVisitor<Void, Boolean>() {

    private val myContext = mutableSetOf<Referable>()

    fun getContext(): Set<Referable> = myContext

    override fun visitReferable(referable: Referable, params: Void?) {
        myContext.add(referable)
    }

    override fun checkSourceNode(sourceNode: Concrete.SourceNode, params: Void?): Boolean? {
        if (myAnchor == sourceNode.getData()) {
            return true
        }
        return null
    }

    companion object {
        fun getLocalVariables(definition: ResolvableDefinition?, anchor: Any?): MutableList<Referable> {
            val localReferables = ArrayList<Referable>()
            if (definition != null) {
                val collector = LocalVariablesScopeCollector(anchor)
                definition.accept(collector, null)
                localReferables.addAll(collector.getContext())
            }
            return localReferables
        }
    }
}
