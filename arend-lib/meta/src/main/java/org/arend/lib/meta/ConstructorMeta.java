package org.arend.lib.meta;

import org.arend.ext.concrete.ConcreteClassElement;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteArgument;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.context.CoreParameter;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.definition.CoreConstructor;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.core.expr.CoreDataCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreSigmaExpression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.error.ArgumentExplicitnessError;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.typechecking.BaseMetaDefinition;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.StdExtension;
import org.arend.lib.error.TypeError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ConstructorMeta extends BaseMetaDefinition {
  private final StdExtension ext;
  private final boolean withImplicit;

  public ConstructorMeta(StdExtension ext, boolean withImplicit) {
    this.ext = ext;
    this.withImplicit = withImplicit;
  }

  @Override
  public boolean requireExpectedType() {
    return true;
  }

  @Override
  public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    ConcreteFactory factory = ext.factory.withData(contextData.getMarker());
    CoreExpression type = contextData.getExpectedType().normalize(NormalizationMode.WHNF);

    if (type instanceof CoreSigmaExpression) {
      List<ConcreteExpression> args = new ArrayList<>();
      for (ConcreteArgument argument : contextData.getArguments()) {
        if (!argument.isExplicit()) {
          typechecker.getErrorReporter().report(new ArgumentExplicitnessError(true, argument.getExpression()));
        }
        args.add(argument.getExpression());
      }
      return typechecker.typecheck(factory.tuple(args), type);
    }

    if (type instanceof CoreClassCallExpression) {
      CoreClassCallExpression classCall = (CoreClassCallExpression) type;
      List<? extends ConcreteArgument> args = contextData.getArguments();
      if (withImplicit) {
        return typechecker.typecheck(factory.newExpr(factory.app(factory.ref(classCall.getDefinition().getRef()), args)), type);
      }

      List<ConcreteClassElement> elements = new ArrayList<>();
      int i = 0;
      for (Iterator<? extends CoreClassField> iterator = classCall.getDefinition().getFields().iterator(); iterator.hasNext(); ) {
        CoreClassField field = iterator.next();
        if (!classCall.isImplemented(field)) {
          if (i >= args.size()) {
            int c = 1;
            while (iterator.hasNext()) {
              if (!classCall.isImplemented(iterator.next())) {
                c++;
              }
            }
            typechecker.getErrorReporter().report(new TypecheckingError("Not enough arguments. Expected " + c + " more.", contextData.getMarker()));
            return null;
          }
          if (!args.get(i).isExplicit()) {
            typechecker.getErrorReporter().report(new ArgumentExplicitnessError(true, args.get(i).getExpression()));
          }
          elements.add(factory.implementation(field.getRef(), args.get(i).getExpression()));
          i++;
        }
      }
      if (i < args.size()) {
        typechecker.getErrorReporter().report(new TypecheckingError("Too many arguments", args.get(i).getExpression()));
        return null;
      }
      return typechecker.typecheck(factory.newExpr(factory.classExt(factory.ref(classCall.getDefinition().getRef()), elements)), type);
    }

    if (type instanceof CoreDataCallExpression) {
      List<CoreConstructor> constructors = ((CoreDataCallExpression) type).computeMatchedConstructors();
      if (constructors == null) {
        typechecker.getErrorReporter().report(new TypeError("Cannot compute constructors of data type", type, contextData.getMarker()));
        return null;
      }

      if (constructors.size() == 1) {
        List<? extends ConcreteArgument> args = contextData.getArguments();
        if (!withImplicit) {
          List<ConcreteArgument> newArgs = new ArrayList<>();
          CoreParameter param = constructors.get(0).getParameters();
          for (ConcreteArgument arg : args) {
            if (!param.hasNext()) {
              typechecker.getErrorReporter().report(new TypecheckingError("Too many arguments", arg.getExpression()));
              return null;
            }
            if (!arg.isExplicit()) {
              typechecker.getErrorReporter().report(new ArgumentExplicitnessError(true, arg.getExpression()));
            }
            newArgs.add(factory.arg(arg.getExpression(), param.isExplicit()));
            param = param.getNext();
          }
          args = newArgs;
        }

        if (!args.isEmpty() && !args.get(0).isExplicit()) {
          CoreParameter dataParam = ((CoreDataCallExpression) type).getDefinition().getParameters();
          if (dataParam.hasNext()) {
            List<ConcreteArgument> newArgs = new ArrayList<>();
            for (; dataParam.hasNext(); dataParam = dataParam.getNext()) {
              newArgs.add(factory.arg(factory.hole(), false));
            }
            newArgs.addAll(args);
            args = newArgs;
          }
        }

        return typechecker.typecheck(factory.app(factory.ref(constructors.get(0).getRef()), args), type);
      }
    }

    typechecker.getErrorReporter().report(new TypeError("Expected a type with 1 constructor", type, contextData.getMarker()));
    return null;
  }
}
