package org.arend.lib.meta.exists;

import org.arend.ext.FreeBindingsModifier;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.ConcreteParameter;
import org.arend.ext.concrete.expr.*;
import org.arend.ext.core.context.CoreBinding;
import org.arend.ext.core.context.CoreParameter;
import org.arend.ext.core.expr.*;
import org.arend.ext.error.TypeMismatchError;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.prettyprinting.doc.DocFactory;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.*;
import org.arend.ext.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GivenMeta implements MetaDefinition {
  private final Kind kind;

  public GivenMeta(Kind kind) {
    this.kind = kind;
  }

  public enum Kind { TRUNCATED, SIGMA, PI }

  protected ConcreteExpression truncate(ConcreteExpression expression, ConcreteFactory factory) {
    return expression;
  }

  private class Processor {
    private final ConcreteFactory factory;
    private final List<ConcreteExpression> arguments = new ArrayList<>();
    private final List<ConcreteParameter> sigmaParams = new ArrayList<>();
    private int arrayIndex = 0;

    private Processor(ConcreteFactory factory) {
      this.factory = factory;
    }

    private ConcreteExpression processParameters(List<? extends ConcreteParameter> parameters, int abstracted, ExpressionTypechecker typechecker) {
      if (parameters.isEmpty()) {
        return getResult();
      }

      ConcreteParameter param = parameters.getFirst();
      ConcreteExpression cType = Objects.requireNonNull(param.getType());
      ConcreteExpression aType;
      List<ConcreteParameter> varParams;
      TypedExpression typedType;
      CoreExpression type = null;
      if (cType instanceof ConcreteHoleExpression) {
        typedType = typechecker.typecheckType(cType);
        if (typedType == null) return null;
        aType = factory.core(typedType);
      } else {
        CoreExpression[] types = new CoreExpression[] { null };
        Pair<AbstractedExpression, TypedExpression> pair = typechecker.typecheckAbstracted(cType, null, abstracted, typed -> {
          typed = typed.normalizeType();
          types[0] = typed.getType();
          if (!(types[0] instanceof CoreUniverseExpression || types[0] instanceof CorePiExpression || types[0] instanceof CoreClassCallExpression && ((CoreClassCallExpression) types[0]).getDefinition() == typechecker.getPrelude().getDArray())) {
            typed = typechecker.coerceToType(typed, cType);
            if (typed == null) {
              if (!types[0].reportIfError(typechecker.getErrorReporter(), cType)) {
                typechecker.getErrorReporter().report(new TypeMismatchError(DocFactory.text("\\Type"), types[0], cType));
              }
              return null;
            }
          }
          return typed;
        });
        if (pair == null) return null;
        aType = factory.withData(cType.getData()).abstracted(pair.proj1, new ArrayList<>(arguments));
        typedType = pair.proj2;
        type = types[0];
      }

      List<ArendRef> refs;
      if (type instanceof CorePiExpression) {
        List<CoreParameter> piParams = new ArrayList<>();
        CoreExpression cod = type.getPiParameters(piParams);
        if (!(cod instanceof CoreUniverseExpression)) {
          typechecker.getErrorReporter().report(new TypeMismatchError(DocFactory.text("_ -> \\Type"), type, cType));
          return null;
        }
        refs = null;
        List<ArendRef> sigmaRefs = new ArrayList<>(param.getRefList().size());
        List<? extends ArendRef> paramRefList = param.getRefList();
        if (param.getRefList().isEmpty() || param.getRefList().size() == 1 && param.getRefList().getFirst() == null) {
          paramRefList = new ArrayList<>(piParams.size());
          for (CoreParameter ignored : piParams) {
            paramRefList.add(null);
          }
        }
        int i = 0;
        for (ArendRef ref : paramRefList) {
          sigmaRefs.add(ref != null ? ref : factory.local(typechecker.getVariableRenameFactory().getNameFromType(piParams.get(i % piParams.size()).getTypeExpr(), null)));
          i++;
        }
        if (sigmaRefs.size() % piParams.size() != 0) {
          typechecker.getErrorReporter().report(new TypecheckingError("Expected (" + piParams.size() + " * n) parameters", param));
          return null;
        }

        varParams = new ArrayList<>(piParams.size());
        int j = 0;
        for (CoreParameter piParam : piParams) {
          List<ArendRef> curRef = new ArrayList<>();
          for (i = j; i < sigmaRefs.size(); i += piParams.size()) {
            curRef.add(sigmaRefs.get(i));
          }
          ConcreteParameter varParam = produceParam(param.isExplicit(), curRef, factory.withData(cType.getData()).core(piParam.getTypedType()), null);
          sigmaParams.add(varParam);
          varParams.add(varParam);
          j++;
        }
        i = 0;
        while (i < sigmaRefs.size()) {
          List<ConcreteArgument> args = new ArrayList<>(piParams.size());
          for (CoreParameter piParam : piParams) {
            args.add(factory.arg(factory.ref(sigmaRefs.get(i++)), piParam.isExplicit()));
          }
          sigmaParams.add(produceParam(true, null, factory.app(aType, args), null));
        }

        j = 0;
        for (CoreParameter ignored : piParams) {
          for (i = j; i < paramRefList.size(); i += piParams.size()) {
            ArendRef ref = paramRefList.get(i);
            arguments.add(ref == null ? null : factory.ref(ref));
          }
          j++;
        }
      } else if (type instanceof CoreClassCallExpression && ((CoreClassCallExpression) type).getDefinition() == typechecker.getPrelude().getDArray()) {
        refs = new ArrayList<>(param.getRefList().size());
        for (ArendRef ignored : param.getRefList()) {
          refs.add(factory.local("j" + (arrayIndex == 0 ? "" : arrayIndex)));
          arrayIndex++;
        }
        ConcreteParameter varParam = produceParam(param.isExplicit(), refs, factory.app(factory.ref(typechecker.getPrelude().getFinRef()), true, Collections.singletonList(factory.app(factory.ref(typechecker.getPrelude().getArrayLengthRef()), false, Collections.singletonList(aType)))), param.getData());
        varParams = Collections.singletonList(varParam);
        sigmaParams.add(varParam);

        for (ArendRef ref : refs) {
          arguments.add(factory.ref(ref));
        }
      } else {
        refs = null;
        varParams = Collections.singletonList(factory.withData(param.getData()).param(param.isExplicit(), param.getRefList(), factory.withData(cType.getData()).core(typedType)));
        sigmaParams.add(produceParam(param.isExplicit(), param.getRefList(), aType, null));

        for (ArendRef ref : param.getRefList()) {
          arguments.add(ref == null ? null : factory.ref(ref));
        }
      }

      if (parameters.size() == 1) {
        return getResult();
      }

      return typechecker.withFreeBindings(new FreeBindingsModifier().addParams(varParams), tc -> {
        List<? extends ConcreteParameter> newParams = parameters.subList(1, parameters.size());
        int newAbstracted = abstracted + param.getNumberOfParameters();
        if (refs == null) {
          return processParameters(newParams, newAbstracted, tc);
        }
        List<Pair<ArendRef, CoreBinding>> list = new ArrayList<>(refs.size());
        for (int i = 0; i < refs.size(); i++) {
          TypedExpression result = tc.typecheck(factory.app(factory.ref(typechecker.getPrelude().getArrayIndexRef()), true, Arrays.asList(factory.withData(cType.getData()).core(typedType), factory.ref(refs.get(i)))), null);
          if (result == null) return null;
          ArendRef ref = param.getRefList().get(i);
          list.add(new Pair<>(ref, result.makeEvaluatingBinding(ref != null ? ref.getRefName() : typechecker.getVariableRenameFactory().getNameFromType(result.getType(), null))));
        }
        return tc.withFreeBindings(new FreeBindingsModifier().addRef(list), tc2 -> processParameters(newParams, newAbstracted, tc2));
      });
    }

    private ConcreteExpression getResult() {
      if (kind != Kind.PI) {
        return truncate(factory.sigma(sigmaParams), factory);
      }
      ConcreteExpression codomain = sigmaParams.getLast().getType();
      assert codomain != null;
      return factory.pi(sigmaParams.subList(0, sigmaParams.size() - 1), codomain);
    }

    private ConcreteParameter produceParam(boolean explicit, Collection<? extends ArendRef> refs, ConcreteExpression type, Object data) {
      ConcreteFactory actualFactory = data == null ? factory : factory.withData(data);
      return refs == null ? actualFactory.param(explicit, type) : actualFactory.param(explicit, refs, type);
    }
  }

  @Override
  public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    ConcreteFactory factory = contextData.getFactory();
    ConcreteExpression arg = contextData.getArguments().getFirst().getExpression();
    List<? extends ConcreteParameter> params;
    if (arg instanceof ConcretePiExpression) {
      List<ConcreteParameter> params1 = new ArrayList<>(((ConcretePiExpression) arg).getParameters());
      params1.add(factory.param(true, ((ConcretePiExpression) arg).getCodomain()));
      params = params1;
    } else if (arg instanceof ConcreteSigmaExpression) {
      params = ((ConcreteSigmaExpression) arg).getParameters();
    } else {
      params = Collections.singletonList(factory.param(true, arg));
    }
    ConcreteExpression result = new Processor(factory).processParameters(params, 0, typechecker);
    return result == null ? null : typechecker.typecheck(result, contextData.getExpectedType());
  }
}
