package org.arend.lib.meta.cases;

import org.arend.ext.concrete.ConcreteClause;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.ConcreteParameter;
import org.arend.ext.concrete.expr.*;
import org.arend.ext.core.body.CoreExpressionPattern;
import org.arend.ext.core.context.CoreEvaluatingBinding;
import org.arend.ext.core.context.CoreParameter;
import org.arend.ext.core.expr.CoreDataCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreReferenceExpression;
import org.arend.ext.core.level.LevelSubstitution;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.core.ops.SubstitutionPair;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.*;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.ext.util.Pair;
import org.arend.lib.StdExtension;
import org.arend.lib.error.IgnoredArgumentError;
import org.arend.lib.meta.util.ReplaceSubexpressionsMeta;
import org.arend.lib.pattern.ArendPattern;
import org.arend.lib.pattern.PatternUtils;
import org.arend.lib.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CasesMeta extends BaseMetaDefinition {
  private final StdExtension ext;
  private final CasesMetaResolver resolver;
  @Dependency private ArendRef constructor;

  public CasesMeta(StdExtension ext, CasesMetaResolver resolver) {
    this.ext = ext;
    this.resolver = resolver;
  }

  @Override
  public boolean @Nullable [] argumentExplicitness() {
    return new boolean[] { true, true, true };
  }

  @Override
  public int numberOfOptionalExplicitArguments() {
    return 1;
  }

  @Override
  public boolean requireExpectedType() {
    return true;
  }

  @Override
  public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    List<? extends ConcreteArgument> args = contextData.getArguments();
    List<? extends ConcreteExpression> caseArgExprs = Utils.getArgumentList(args.get(0).getExpression());
    ConcreteExpression defaultExpr = args.size() > 2 ? args.get(2).getExpression() : null;

    List<CasesMetaResolver.ArgParameters> argParametersList = new ArrayList<>(args.size());
    for (ConcreteExpression caseArgExpr : caseArgExprs) {
      argParametersList.add(resolver.new ArgParameters(caseArgExpr, typechecker.getErrorReporter(), true));
    }

    List<TypedExpression> typedArgs;
    if (defaultExpr != null) {
      typedArgs = new ArrayList<>();
      for (CasesMetaResolver.ArgParameters argParameters : argParametersList) {
        TypedExpression typed = typechecker.typecheck(argParameters.expression, null);
        if (typed == null) return null;
        typedArgs.add(typed);
      }
    } else {
      typedArgs = null;
    }

    ConcreteFactory factory = ext.factory.withData(contextData.getMarker());
    List<ConcreteCaseArgument> caseArgs = new ArrayList<>();
    List<Pair<TypedExpression,Object>> searchPairs = new ArrayList<>();
    List<ConcreteParameter> concreteParameters = defaultExpr == null ? null : new ArrayList<>();
    for (int i = 0; i < argParametersList.size(); i++) {
      CasesMetaResolver.ArgParameters argParams = argParametersList.get(i);
      ConcreteExpression argExpr = argParams.expression;
      ConcreteExpression argType = argParams.type;
      boolean isLocalRef = argParams.expression instanceof ConcreteReferenceExpression && ((ConcreteReferenceExpression) argParams.expression).getReferent().isLocalRef();
      boolean isElim = isLocalRef && !argParams.addPath && argParams.name == null && defaultExpr == null && !(typechecker.getFreeBinding(((ConcreteReferenceExpression) argParams.expression).getReferent()) instanceof CoreEvaluatingBinding) && argType == null && searchPairs.isEmpty();
      ArendRef caseArgRef = argParams.name != null ? argParams.name : !isElim ? factory.local("x") : null;
      if (!isElim) {
        TypedExpression typed = typedArgs != null ? typedArgs.get(i) : typechecker.typecheck(argParams.expression, null);
        if (typed == null) return null;
        if (!isLocalRef) {
          argExpr = factory.core(typed);
        }
        if (argType == null && !searchPairs.isEmpty()) {
          argType = factory.meta("case_arg_type", new ReplaceSubexpressionsMeta(typed.getType().normalize(NormalizationMode.RNF), searchPairs));
        }
        searchPairs.add(new Pair<>(typed, caseArgRef));
      }
      caseArgs.add(isElim ? factory.caseArg((ConcreteReferenceExpression) argExpr, null) : factory.caseArg(argExpr, caseArgRef, argType));
      if (concreteParameters != null) {
        concreteParameters.add(factory.param(Collections.singletonList(caseArgRef), argType != null ? argType : factory.core(typedArgs.get(i).getType().computeTyped())));
      }
      if (argParams.addPath) {
        ConcreteExpression type = factory.app(factory.ref(ext.prelude.getEqualityRef()), true, Arrays.asList(factory.hole(), factory.ref(caseArgRef)));
        caseArgs.add(factory.caseArg(factory.ref(ext.prelude.getIdpRef()), null, type));
        if (concreteParameters != null) {
          concreteParameters.add(factory.param(Collections.singletonList(caseArgRef), type));
        }
      }
    }

    List<? extends ConcreteClause> clauses = ((ConcreteCaseExpression) args.get(1).getExpression()).getClauses();
    if (concreteParameters != null) {
      List<ConcreteClause> newClauses = new ArrayList<>(clauses);
      List<List<ArendPattern>> patternLists = new ArrayList<>();
      patternLists.add(Collections.emptyList());

      CoreParameter parameters = typechecker.typecheckParameters(concreteParameters);
      if (parameters == null) {
        return null;
      }

      CoreParameter parameter = parameters;
      for (int j = 0; j < typedArgs.size(); j++) {
        CoreExpression type = typedArgs.get(j).getType().normalize(NormalizationMode.WHNF);
        List<ArendPattern> patterns = getPatterns(type, parameter);
        if (patterns != null && patterns.isEmpty()) {
          patternLists = Collections.emptyList();
          break;
        }

        List<List<ArendPattern>> newPatternLists = new ArrayList<>();
        for (List<ArendPattern> patternList : patternLists) {
          List<ArendPattern> patterns1 = patterns;
          CoreExpression type1 = type;
          if (patterns1 == null) {
            List<SubstitutionPair> substitution = new ArrayList<>(patternList.size());
            for (int i = 0; i < patternList.size(); i++) {
              CoreExpression expr = typedArgs.get(i).getExpression();
              if (expr instanceof CoreReferenceExpression) {
                substitution.add(new SubstitutionPair(((CoreReferenceExpression) expr).getBinding(), PatternUtils.toExpression(patternList.get(i), constructor, factory, null)));
              }
            }
            if (!substitution.isEmpty()) {
              type1 = typechecker.substitute(type1, LevelSubstitution.EMPTY, substitution);
              if (type1 != null) {
                patterns1 = getPatterns(type1.normalize(NormalizationMode.WHNF), parameter);
              }
            }
            if (patterns1 == null) {
              patterns1 = Collections.singletonList(new ArendPattern(parameter.getBinding(), null, Collections.emptyList(), parameter, ext.renamerFactory));
            }
          }
          for (ArendPattern pattern : patterns1) {
            List<ArendPattern> newPatternList = new ArrayList<>(patternList.size() + 1);
            newPatternList.addAll(patternList);
            newPatternList.add(pattern);
            newPatternLists.add(newPatternList);
          }
        }

        patternLists = newPatternLists;
        parameter = parameter.getNext();

        if (argParametersList.get(j).addPath) {
          for (List<ArendPattern> patternList : patternLists) {
            patternList.add(new ArendPattern(parameter.getBinding(), null, Collections.emptyList(), parameter, ext.renamerFactory));
          }
          parameter = parameter.getNext();
        }
      }

      if (patternLists.isEmpty()) {
        typechecker.getErrorReporter().report(new IgnoredArgumentError(defaultExpr));
      } else {
        List<List<CoreExpressionPattern>> actualRows = new ArrayList<>();
        for (ConcreteClause clause : clauses) {
          if (clause.getPatterns().size() == typedArgs.size()) {
            List<CoreExpressionPattern> row = typechecker.typecheckPatterns(clause.getPatterns(), parameters, clause);
            if (row != null) {
              actualRows.add(row);
            }
          }
        }

        for (List<ArendPattern> patternList : patternLists) {
          if (PatternUtils.computeCovering(actualRows, patternList) == null) {
            List<ArendRef> asRefs = new ArrayList<>();
            for (CasesMetaResolver.ArgParameters argParameters : argParametersList) {
              argParameters.addAsRef(asRefs);
            }
            newClauses.add(factory.clause(PatternUtils.toConcrete(patternList, asRefs, ext.renamerFactory, factory, null, null), defaultExpr));
          }
        }
      }

      clauses = newClauses;
    }

    return typechecker.typecheck(factory.caseExpr(false, caseArgs, searchPairs.isEmpty() ? null : factory.meta("return_expr", new ReplaceSubexpressionsMeta(contextData.getExpectedType().normalize(NormalizationMode.RNF), searchPairs)), null, clauses), searchPairs.isEmpty() ? contextData.getExpectedType() : null);
  }

  private List<ArendPattern> getPatterns(CoreExpression type, CoreParameter parameter) {
    if (type instanceof CoreDataCallExpression && ((CoreDataCallExpression) type).getDefinition() == ext.prelude.getPath()) {
      return Collections.singletonList(new ArendPattern(parameter.getBinding(), null, Collections.emptyList(), parameter, ext.renamerFactory));
    }
    List<CoreExpression.ConstructorWithDataArguments> constructors = type instanceof CoreDataCallExpression ? type.computeMatchedConstructorsWithDataArguments() : null;
    if (constructors == null) return null;

    List<ArendPattern> patterns = new ArrayList<>(constructors.size());
    for (CoreExpression.ConstructorWithDataArguments constructor : constructors) {
      List<ArendPattern> subpatterns = new ArrayList<>();
      for (CoreParameter param = constructor.getParameters(); param.hasNext(); param = param.getNext()) {
        subpatterns.add(new ArendPattern(param.getBinding(), null, Collections.emptyList(), param, ext.renamerFactory));
      }
      patterns.add(new ArendPattern(null, constructor.getConstructor(), subpatterns, null, ext.renamerFactory));
    }
    return patterns;
  }
}
