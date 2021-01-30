package org.arend.lib.meta;

import org.arend.ext.concrete.ConcreteAppBuilder;
import org.arend.ext.concrete.ConcreteClause;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.*;
import org.arend.ext.core.body.CoreExpressionPattern;
import org.arend.ext.core.context.CoreParameter;
import org.arend.ext.core.definition.CoreConstructor;
import org.arend.ext.core.expr.CoreDataCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreReferenceExpression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.core.ops.SubstitutionPair;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.NameResolverError;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.reference.ExpressionResolver;
import org.arend.ext.reference.Precedence;
import org.arend.ext.typechecking.*;
import org.arend.lib.StdExtension;
import org.arend.lib.pattern.ArendPattern;
import org.arend.lib.pattern.PatternUtils;
import org.arend.lib.util.NamedParameter;
import org.arend.lib.util.Pair;
import org.arend.lib.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CasesMeta extends BaseMetaDefinition implements MetaResolver {
  private final StdExtension ext;
  final NamedParameter parameter;
  final ArendRef argRef;
  final ArendRef nameRef;
  final ArendRef addPathRef;

  public CasesMeta(StdExtension ext) {
    this.ext = ext;
    parameter = new NamedParameter(ext.factory);
    argRef = ext.factory.global("arg", new Precedence(Precedence.Associativity.NON_ASSOC, (byte) 0, true));

    nameRef = parameter.add("name", (resolver, args) -> {
      if (args.size() == 1 && args.get(0).isExplicit() && args.get(0).getExpression() instanceof ConcreteReferenceExpression) {
        ConcreteFactory factory = ext.factory.withData(args.get(0).getExpression().getData());
        ArendRef ref = factory.localDeclaration(((ConcreteReferenceExpression) args.get(0).getExpression()).getReferent());
        resolver.registerDeclaration(ref);
        return factory.ref(ref);
      } else {
        resolver.getErrorReporter().report(new NameResolverError("Expected an identifier", args.get(0).getExpression()));
        return null;
      }
    });

    addPathRef = parameter.addFlag("addPath");
  }

  @Override
  public @Nullable boolean[] argumentExplicitness() {
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

  private ConcreteExpression resolveArgRef(ExpressionResolver resolver, ConcreteExpression expression) {
    return expression instanceof ConcreteReferenceExpression ? resolver.useRefs(Collections.singletonList(argRef), true).resolve(expression) : null;
  }

  private ConcreteExpression resolveArgument(ExpressionResolver resolver, ConcreteExpression argument) {
    if (argument instanceof ConcreteTupleExpression) {
      List<ConcreteExpression> newFields = new ArrayList<>();
      for (ConcreteExpression field : ((ConcreteTupleExpression) argument).getFields()) {
        ConcreteExpression newField = resolveArgument1(resolver, field);
        newFields.add(newField);
        if (newField instanceof ConcreteAppExpression && ((ConcreteAppExpression) newField).getFunction() instanceof ConcreteReferenceExpression && ((ConcreteReferenceExpression) ((ConcreteAppExpression) newField).getFunction()).getReferent() == argRef) {
          ConcreteExpression name = parameter.getValue(((ConcreteAppExpression) newField).getArguments().get(1).getExpression(), nameRef);
          if (name instanceof ConcreteReferenceExpression) {
            resolver = resolver.useRefs(Collections.singletonList(((ConcreteReferenceExpression) name).getReferent()), true);
          }
        }
      }
      return ext.factory.withData(argument.getData()).tuple(newFields);
    } else {
      return resolveArgument1(resolver, argument);
    }
  }

  private ConcreteExpression addType(ConcreteExpression expr, ConcreteExpression type, ConcreteExpression opArg) {
    if (type == null) return expr;
    ConcreteFactory factory = ext.factory.withData(expr.getData());
    return factory.app(opArg != null ? opArg : factory.ref(argRef), true, Arrays.asList(expr, factory.tuple(), type));
  }

  private ConcreteExpression resolveArgument1(ExpressionResolver resolver, ConcreteExpression argument) {
    ConcreteExpression type = argument instanceof ConcreteTypedExpression ? resolver.resolve(((ConcreteTypedExpression) argument).getType()) : null;
    if (argument instanceof ConcreteTypedExpression) argument = ((ConcreteTypedExpression) argument).getExpression();

    List<ConcreteArgument> sequence = argument.getArgumentsSequence();
    if (sequence.size() >= 3 && sequence.get(sequence.size() - 1).isExplicit() && sequence.get(sequence.size() - 2).isExplicit()) {
      ConcreteExpression opArg = resolveArgRef(resolver, sequence.get(sequence.size() - 2).getExpression());
      if (opArg instanceof ConcreteReferenceExpression && ((ConcreteReferenceExpression) opArg).getReferent() == argRef) {
        ConcreteExpression result = resolver.resolve(argument.getData(), sequence.subList(0, sequence.size() - 2));
        ConcreteExpression params = parameter.resolve(resolver, sequence.get(sequence.size() - 1).getExpression(), false, true);
        if (params == null && type == null) return result;
        ConcreteFactory factory = ext.factory.withData(argument.getData());
        ConcreteAppBuilder builder = factory.appBuilder(opArg).app(result).app(params != null ? params : factory.tuple());
        if (type != null) builder.app(type);
        return builder.build();
      }

      if (opArg != null) {
        sequence.set(sequence.size() - 2, ext.factory.arg(opArg, true));
        return addType(resolver.resolve(argument.getData(), sequence), type, opArg);
      }
    }

    if (sequence.size() >= 2 && sequence.get(sequence.size() - 1).isExplicit()) {
      ConcreteExpression lastArg = resolveArgRef(resolver, sequence.get(sequence.size() - 1).getExpression());
      if (lastArg instanceof ConcreteReferenceExpression && ((ConcreteReferenceExpression) lastArg).getReferent() == argRef) {
        resolver.getErrorReporter().report(new NameResolverError("Expected an argument after '" + argRef.getRefName() + "'", lastArg));
      }

      if (lastArg != null) {
        sequence.set(sequence.size() - 1, ext.factory.arg(lastArg, true));
        return addType(resolver.resolve(argument.getData(), sequence), type, lastArg);
      }
    }

    return addType(resolver.resolve(argument), type, null);
  }

  @Override
  public @Nullable ConcreteExpression resolvePrefix(@NotNull ExpressionResolver resolver, @NotNull ContextData contextData) {
    List<? extends ConcreteArgument> args = contextData.getArguments();
    if (args.size() > 2 || args.isEmpty() || !args.get(0).isExplicit() || args.size() > 1 && !args.get(1).isExplicit()) {
      resolver.getErrorReporter().report(new NameResolverError("Expected at least 1 and at most 2 explicit arguments", contextData.getMarker()));
      return null;
    }

    ConcreteFactory factory = ext.factory.withData(contextData.getMarker());
    ConcreteAppBuilder builder = factory.appBuilder(contextData.getReferenceExpression())
      .app(resolveArgument(resolver, args.get(0).getExpression()), args.get(0).isExplicit())
      .app(resolver.resolve(factory.caseExpr(false, Collections.emptyList(), null, null, contextData.getClauses() == null ? Collections.emptyList() : contextData.getClauses().getClauseList())));
    if (args.size() > 1) {
      builder.app(resolver.resolve(args.get(1).getExpression()));
    }
    return builder.build();
  }

  @Override
  public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    List<? extends ConcreteArgument> args = contextData.getArguments();
    List<? extends ConcreteExpression> caseArgExprs = Utils.getArgumentList(args.get(0).getExpression());
    ConcreteExpression defaultExpr = args.size() > 2 ? args.get(2).getExpression() : null;
    List<TypedExpression> typedArgs;
    if (defaultExpr != null) {
      typedArgs = new ArrayList<>();
      for (ConcreteExpression caseArgExpr : caseArgExprs) {
        TypedExpression typed = typechecker.typecheck(caseArgExpr, null);
        if (typed == null) return null;
        typedArgs.add(typed);
      }
    } else {
      typedArgs = null;
    }

    ConcreteFactory factory = ext.factory.withData(contextData.getMarker());
    List<ConcreteCaseArgument> caseArgs = new ArrayList<>(caseArgExprs.size());
    List<Pair<CoreExpression,ArendRef>> searchPairs = new ArrayList<>();
    for (int i = 0; i < caseArgExprs.size(); i++) {
      ConcreteExpression caseArgExpr = caseArgExprs.get(i);
      ConcreteExpression nameExpr;
      boolean addPath;
      ConcreteExpression argExpr;
      ConcreteExpression argType;
      if (caseArgExpr instanceof ConcreteAppExpression && ((ConcreteAppExpression) caseArgExpr).getFunction() instanceof ConcreteReferenceExpression && ((ConcreteReferenceExpression) ((ConcreteAppExpression) caseArgExpr).getFunction()).getReferent() == argRef) {
        List<? extends ConcreteArgument> parameters = ((ConcreteAppExpression) caseArgExpr).getArguments();
        Map<ArendRef, ConcreteExpression> params = new HashMap<>();
        Set<ArendRef> flags = new HashSet<>();
        parameter.getAllValues(parameters.get(1).getExpression(), params, flags, null, typechecker.getErrorReporter());
        nameExpr = params.get(nameRef);
        addPath = flags.contains(addPathRef);
        argExpr = parameters.get(0).getExpression();
        argType = parameters.size() > 2 ? parameters.get(2).getExpression() : null;
      } else {
        nameExpr = null;
        addPath = false;
        argExpr = caseArgExpr;
        argType = null;
      }

      boolean isElim = !addPath && !(nameExpr instanceof ConcreteReferenceExpression) && argExpr instanceof ConcreteReferenceExpression && ((ConcreteReferenceExpression) argExpr).getReferent().isLocalRef();
      ArendRef caseArgRef = nameExpr instanceof ConcreteReferenceExpression ? ((ConcreteReferenceExpression) nameExpr).getReferent() : !isElim ? factory.local("x") : null;
      if (!isElim || argType == null && !searchPairs.isEmpty()) {
        TypedExpression typed = typedArgs != null ? typedArgs.get(i) : typechecker.typecheck(argExpr, null);
        if (typed == null) return null;
        if (!(argExpr instanceof ConcreteReferenceExpression)) {
          argExpr = factory.core(typed);
        }
        if (argType == null && !searchPairs.isEmpty()) {
          argType = factory.meta("case_arg_type", new ReplaceSubexpressionsMeta(typed.getType(), searchPairs));
        }
        if (!isElim) {
          searchPairs.add(new Pair<>(typed.getExpression(), caseArgRef));
        }
      }
      caseArgs.add(isElim ? factory.caseArg((ConcreteReferenceExpression) argExpr, argType) : factory.caseArg(argExpr, caseArgRef, argType));
      if (addPath) {
        caseArgs.add(factory.caseArg(factory.ref(ext.prelude.getIdp().getRef()), null, factory.app(factory.ref(ext.prelude.getEquality().getRef()), true, Arrays.asList(factory.hole(), factory.ref(caseArgRef)))));
      }
    }

    List<? extends ConcreteClause> clauses = ((ConcreteCaseExpression) args.get(1).getExpression()).getClauses();
    if (defaultExpr != null) {
      List<ConcreteClause> newClauses = new ArrayList<>(clauses);
      List<List<ArendPattern>> patternLists = new ArrayList<>();
      patternLists.add(Collections.emptyList());

      List<CoreExpression> types = new ArrayList<>(typedArgs.size());
      for (TypedExpression typedArg : typedArgs) {
        types.add(typedArg.getType());
      }
      CoreParameter parameters = typechecker.makeParameters(types, contextData.getMarker());

      CoreParameter parameter = parameters;
      for (TypedExpression typedArg : typedArgs) {
        CoreExpression type = typedArg.getType().normalize(NormalizationMode.WHNF);
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
                substitution.add(new SubstitutionPair(((CoreReferenceExpression) expr).getBinding(), PatternUtils.toExpression(patternList.get(i), ext, factory, null)));
              }
            }
            if (!substitution.isEmpty()) {
              type1 = typechecker.substitute(type1, null, substitution);
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
      }

      if (patternLists.isEmpty()) {
        typechecker.getErrorReporter().report(new TypecheckingError(GeneralError.Level.WARNING_UNUSED, "Argument is ignored", defaultExpr));
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
            newClauses.add(factory.clause(PatternUtils.toConcrete(patternList, ext.renamerFactory, factory, null), defaultExpr));
          }
        }
      }

      clauses = newClauses;
    }

    return typechecker.typecheck(factory.caseExpr(false, caseArgs, searchPairs.isEmpty() ? null : factory.meta("return_expr", new ReplaceSubexpressionsMeta(contextData.getExpectedType(), searchPairs)), null, clauses), searchPairs.isEmpty() ? contextData.getExpectedType() : null);
  }

  private List<ArendPattern> getPatterns(CoreExpression type, CoreParameter parameter) {
    if (type instanceof CoreDataCallExpression && ((CoreDataCallExpression) type).getDefinition() == ext.prelude.getPath()) {
      return Collections.singletonList(new ArendPattern(parameter.getBinding(), null, Collections.emptyList(), parameter, ext.renamerFactory));
    }
    List<CoreConstructor> constructors = type instanceof CoreDataCallExpression ? ((CoreDataCallExpression) type).computeMatchedConstructors() : null;
    if (constructors == null) return null;

    List<ArendPattern> patterns = new ArrayList<>(constructors.size());
    for (CoreConstructor constructor : constructors) {
      List<ArendPattern> subpatterns = new ArrayList<>();
      for (CoreParameter param = constructor.getParameters(); param.hasNext(); param = param.getNext()) {
        subpatterns.add(new ArendPattern(param.getBinding(), null, Collections.emptyList(), param, ext.renamerFactory));
      }
      patterns.add(new ArendPattern(null, constructor, subpatterns, null, ext.renamerFactory));
    }
    return patterns;
  }
}
