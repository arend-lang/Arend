package org.arend.lib.meta.equation.binop_matcher;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.core.definition.CoreConstructor;
import org.arend.ext.core.expr.CoreConCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreFunCallExpression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.util.Names;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ListFunctionMatcher implements FunctionMatcher {
  private final ExpressionTypechecker typechecker;
  private final ConcreteFactory factory;

  public ListFunctionMatcher(ExpressionTypechecker typechecker, ConcreteFactory factory) {
    this.typechecker = typechecker;
    this.factory = factory;
  }

  @Override
  public List<CoreExpression> match(CoreExpression expr) {
    if (expr instanceof CoreFunCallExpression && ((CoreFunCallExpression) expr).getDefinition().getRef().checkName(Names.APPEND)) {
      List<? extends CoreExpression> defCallArgs = ((CoreFunCallExpression) expr).getDefCallArguments();
      List<CoreExpression> args = new ArrayList<>(2);
      args.add(defCallArgs.get(1));
      args.add(defCallArgs.get(2));
      return args;
    } else if (expr instanceof CoreConCallExpression cons && cons.getDefinition().getRef().checkName(Names.CONS)) {
      List<? extends CoreExpression> defCallArgs = cons.getDefCallArguments();
      CoreExpression tail = defCallArgs.get(1).normalize(NormalizationMode.WHNF);
      if (!(tail instanceof CoreConCallExpression && ((CoreConCallExpression) tail).getDefinition().getRef().checkName(Names.NIL))) {
        CoreConstructor nil = cons.getDefinition().getDataType().findConstructor(Names.getNil());
        if (nil != null) {
          TypedExpression result = typechecker.typecheck(factory.app(factory.ref(cons.getDefinition().getRef()), true, Arrays.asList(factory.core(defCallArgs.getFirst().computeTyped()), factory.ref(nil.getRef()))), null);
          if (result != null) {
            List<CoreExpression> args = new ArrayList<>(2);
            args.add(result.getExpression());
            args.add(tail);
            return args;
          }
        }
      }
    }
    return null;
  }
}
