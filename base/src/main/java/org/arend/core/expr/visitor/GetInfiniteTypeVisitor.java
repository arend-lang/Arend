package org.arend.core.expr.visitor;

import org.arend.core.context.param.DependentLink;
import org.arend.core.definition.ClassField;
import org.arend.core.definition.DataDefinition;
import org.arend.core.expr.*;
import org.arend.core.sort.SortExpression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GetInfiniteTypeVisitor extends GetTypeVisitor {
  private final Map<DependentLink, Integer> myParameters;
  private final DataDefinition myThisData;

  public GetInfiniteTypeVisitor(Map<DependentLink, Integer> parameters, DataDefinition thisData) {
    myParameters = parameters;
    myThisData = thisData;
  }

  public GetInfiniteTypeVisitor(Map<DependentLink, Integer> parameters) {
    this(parameters, null);
  }

  @Override
  public Expression visitReference(ReferenceExpression expr, Void params) {
    if (expr.getBinding() instanceof DependentLink param) {
      Integer index = myParameters.get(param);
      if (index != null) {
        Expression result = param.getType().replaceInfinityLevel(index, Collections.emptyList());
        if (result != null) return result;
      }
    }
    return super.visitReference(expr, params);
  }

  @Override
  public Expression visitApp(AppExpression expr, Void params) {
    Expression fun = expr.getFunction();
    while (fun instanceof AppExpression appExpr) {
      fun = appExpr.getFunction();
    }
    if (fun instanceof ReferenceExpression refExpr && refExpr.getBinding() instanceof DependentLink param) {
      Integer index = myParameters.get(param);
      if (index != null) {
        Expression result = super.visitApp(expr, params).replaceInfinityLevel(index, Collections.emptyList());
        if (result != null) return result;
      }
    }

    return super.visitApp(expr, params);
  }

  @Override
  public UniverseExpression visitDataCall(DataCallExpression expr, Void params) {
    return myThisData == expr.getDefinition() ? new UniverseExpression(new SortExpression.RecursiveData()) : super.visitDataCall(expr, params);
  }

  @Override
  public Expression visitFieldCall(FieldCallExpression expr, Void params) {
    Expression result = super.visitFieldCall(expr, params);
    if (!result.isPiInfinityLevel()) return result;

    List<ClassField> fields = new ArrayList<>();
    fields.add(expr.getDefinition());
    Expression arg = expr.getArgument();
    while (true) {
      if (arg instanceof FieldCallExpression fieldCall) {
        fields.add(fieldCall.getDefinition());
        arg = fieldCall.getArgument();
      } else if (arg instanceof AppExpression appExpr) {
        arg = appExpr.getFunction();
      } else {
        break;
      }
    }

    if (arg instanceof ReferenceExpression refExpr && refExpr.getBinding() instanceof DependentLink param) {
      Integer index = myParameters.get(param);
      if (index != null) {
        Collections.reverse(fields);
        return result.replaceInfinityLevel(index, fields);
      }
    }

    return result;
  }
}
