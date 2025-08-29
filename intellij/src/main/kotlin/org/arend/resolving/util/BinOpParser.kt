package org.arend.resolving.util

import org.arend.error.DummyErrorReporter
import org.arend.ext.error.ErrorReporter
import org.arend.naming.binOp.ExpressionBinOpEngine
import org.arend.naming.resolving.typing.TypingInfo
import org.arend.naming.scope.Scope
import org.arend.term.Fixity
import org.arend.term.abs.Abstract
import org.arend.term.concrete.Concrete

fun parseBinOp(left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>): Concrete.Expression =
        parseBinOp(null, left, sequence)

fun parseBinOp(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, errorReporter: ErrorReporter = DummyErrorReporter.INSTANCE, scope: Scope? = null): Concrete.Expression {
    val concreteSeq = mutableListOf<Concrete.BinOpSequenceElem<Concrete.Expression>>()
    concreteSeq.add(Concrete.BinOpSequenceElem(left as Concrete.Expression))
    for (elem in sequence) {
        concreteSeq.add(Concrete.BinOpSequenceElem(elem.expression as Concrete.Expression, if (elem.isVariable) Fixity.UNKNOWN else Fixity.NONFIX, elem.isExplicit))
    }
    return ExpressionBinOpEngine.parse(Concrete.BinOpSequenceExpression(data, concreteSeq, null), errorReporter, TypingInfo.EMPTY)
}