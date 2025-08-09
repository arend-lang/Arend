package org.arend.typechecking.visitor;

import org.arend.core.definition.ClassDefinition;
import org.arend.core.definition.ClassField;
import org.arend.core.definition.Definition;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.typechecking.MetaDefinition;
import org.arend.naming.reference.*;
import org.arend.term.concrete.BaseConcreteExpressionVisitor;
import org.arend.term.concrete.Concrete;
import org.arend.ext.error.LocalError;
import org.arend.typechecking.provider.ConcreteProvider;

import java.util.*;

public class ClassFieldChecker extends BaseConcreteExpressionVisitor<Void> {
  private Referable myThisParameter;
  private final ConcreteProvider myConcreteProvider;
  private final Set<TCDefReferable> myRecursiveDefs;
  private final TCDefReferable myClassReferable;
  private final Collection<CoreClassDefinition> mySuperClasses;
  private final Set<? extends LocatedReferable> myFields;
  private final Set<TCDefReferable> myFutureFields;
  private final ErrorReporter myErrorReporter;
  private int myClassCallNumber;

  ClassFieldChecker(Referable thisParameter, ConcreteProvider concreteProvider, Set<TCDefReferable> recursiveDefs, TCDefReferable classReferable, Collection<CoreClassDefinition> superClasses, Set<? extends LocatedReferable> fields, Set<TCDefReferable> futureFields, ErrorReporter errorReporter) {
    myThisParameter = thisParameter;
    myConcreteProvider = concreteProvider;
    myRecursiveDefs = recursiveDefs;
    myClassReferable = classReferable;
    mySuperClasses = superClasses;
    myFields = fields;
    myFutureFields = futureFields;
    myErrorReporter = errorReporter;
  }

  void setThisParameter(Referable thisParameter) {
    myThisParameter = thisParameter;
  }

  private Concrete.Expression makeErrorExpression(Concrete.ReferenceExpression expr) {
    LocalError error = new TypecheckingError("Fields may refer only to previous fields", expr);
    myErrorReporter.report(error);
    return new Concrete.ErrorHoleExpression(expr.getData(), error);
  }

  private Concrete.Expression getThisExpression(Object data) {
    return myFutureFields == null ? new Concrete.ReferenceExpression(data, myThisParameter) : new Concrete.ThisExpression(data, myThisParameter);
  }

  @Override
  public Concrete.Expression visitReference(Concrete.ReferenceExpression expr, Void params) {
    Referable ref = expr.getReferent();
    if (ref instanceof TCDefReferable defRef) {
      if (myFields.contains(defRef)) {
        if (myFutureFields != null && myFutureFields.contains(defRef)) {
          return makeErrorExpression(expr);
        } else {
          return Concrete.AppExpression.make(expr.getData(), expr, getThisExpression(expr.getData()), false);
        }
      } else {
        ClassDefinition enclosingClass = null;
        if (myRecursiveDefs.contains(defRef)) {
          if (myConcreteProvider.getConcrete(defRef) instanceof Concrete.Definition def && def.enclosingClass != null && def.enclosingClass.getTypechecked() instanceof ClassDefinition classDef) {
            enclosingClass = classDef;
          }
        } else {
          Definition def = defRef.getTypechecked();
          if (def != null && !(def instanceof ClassField)) {
            enclosingClass = def.getEnclosingClass();
          }
        }
        if (enclosingClass != null) {
          if (myFutureFields != null && myClassReferable.equals(enclosingClass.getReferable())) {
            return makeErrorExpression(expr);
          }
          if (ClassDefinition.findAncestor(new ArrayDeque<>(mySuperClasses), enclosingClass::equals) != null) {
            return Concrete.AppExpression.make(expr.getData(), expr, getThisExpression(expr.getData()), false);
          }
        }
      }
    }
    return expr;
  }

  @Override
  public Concrete.Expression visitThis(Concrete.ThisExpression expr, Void params) {
    return myClassCallNumber == 0 ? getThisExpression(expr.getData()) : expr;
  }

  @Override
  public Concrete.Expression visitTyped(Concrete.TypedExpression expr, Void params) {
    if (expr.expression instanceof Concrete.ThisExpression && expr.type instanceof Concrete.ReferenceExpression && ((Concrete.ReferenceExpression) expr.type).getReferent().equals(myClassReferable)) {
      ((Concrete.ThisExpression) expr.expression).setReferent(myThisParameter);
      return expr.expression;
    } else {
      return super.visitTyped(expr, null);
    }
  }

  @Override
  public Concrete.Expression visitApp(Concrete.AppExpression expr, Void params) {
    if (expr.getFunction() instanceof Concrete.ReferenceExpression && ((Concrete.ReferenceExpression) expr.getFunction()).getReferent() instanceof MetaReferable) {
      MetaDefinition meta = ((MetaReferable) ((Concrete.ReferenceExpression) expr.getFunction()).getReferent()).getDefinition();
      if (meta != null) {
        int[] indices = meta.desugarArguments(expr.getArguments());
        if (indices != null) {
          List<Concrete.Argument> arguments = expr.getArguments();
          for (int index : indices) {
            Concrete.Argument arg = arguments.get(index);
            arg.expression = arg.expression.accept(this, params);
          }
          return expr;
        }
      }
    } else if (expr.getFunction() instanceof Concrete.ReferenceExpression && !expr.getArguments().getFirst().isExplicit() && !(expr.getArguments().getFirst() instanceof Concrete.GeneratedArgument)) {
      for (Concrete.Argument argument : expr.getArguments()) {
        argument.expression = argument.expression.accept(this, params);
      }
      return expr;
    }

    return super.visitApp(expr, params);
  }

  @Override
  public Concrete.Expression visitClassExt(Concrete.ClassExtExpression expr, Void params) {
    expr.setBaseClassExpression(expr.getBaseClassExpression().accept(this, params));
    myClassCallNumber++;
    for (Concrete.ClassFieldImpl element : expr.getStatements()) {
      if (myClassCallNumber == 1 && element.implementation instanceof Concrete.ThisExpression && element.getImplementedRef() instanceof TCDefReferable && ((TCDefReferable) element.getImplementedRef()).getTypechecked() instanceof ClassDefinition) {
        ((Concrete.ThisExpression) element.implementation).setReferent(myThisParameter);
      }
      visitClassElement(element, params);
    }
    myClassCallNumber--;
    return expr;
  }

  @Override
  protected void visitClassFieldImpl(Concrete.ClassFieldImpl classFieldImpl, Void params) {
    if (!(classFieldImpl instanceof Concrete.CoClauseFunctionReference)) {
      super.visitClassFieldImpl(classFieldImpl, params);
    }
  }

  @Override
  public Concrete.Expression visitNumericLiteral(Concrete.NumericLiteral expr, Void params) {
    return expr;
  }

  @Override
  public Concrete.Expression visitStringLiteral(Concrete.StringLiteral expr, Void params) {
    return expr;
  }
}
