package org.arend.lib.meta.equationNew;

import org.arend.ext.concrete.ConcreteAppBuilder;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.ConcreteLetClause;
import org.arend.ext.concrete.expr.*;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreFunCallExpression;
import org.arend.ext.core.ops.CMP;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.MissingArgumentsError;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.instance.SubclassSearchParameters;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.BaseMetaDefinition;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.ext.util.Pair;
import org.arend.lib.error.equation.EquationFindError;
import org.arend.lib.error.equation.EquationSolveError;
import org.arend.lib.error.equation.EquationTypeMismatchError;
import org.arend.lib.error.equation.NFPrettyPrinter;
import org.arend.lib.meta.equationNew.term.EquationTerm;
import org.arend.lib.meta.equationNew.term.TermOperation;
import org.arend.lib.util.Lazy;
import org.arend.lib.util.Utils;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class BaseEquationMeta<NF> extends BaseMetaDefinition {
  @Dependency                                           ArendRef inv;
  @Dependency(name = "*>")                              ArendRef concat;
  @Dependency(name = "BaseSet.E")                       CoreClassField carrier;
  @Dependency(name = "SolverModel.terms-equality")      ArendRef termsEquality;
  @Dependency(name = "SolverModel.terms-equality-conv") ArendRef termsEqualityConv;

  protected abstract @NotNull CoreClassDefinition getClassDef();

  protected abstract @NotNull List<TermOperation> getOperations(TypedExpression instance, CoreClassCallExpression instanceType, ExpressionTypechecker typechecker, ConcreteFactory factory, ConcreteExpression marker);

  protected abstract @NotNull NF normalize(EquationTerm term);

  protected abstract @NotNull NFPrettyPrinter<NF> getNFPrettyPrinter();

  protected abstract @NotNull ConcreteExpression nfToConcrete(NF nf, Values<CoreExpression> values, TypedExpression instance, ConcreteFactory factory);

  protected abstract @NotNull ArendRef getSolverModel();

  protected abstract @NotNull ArendRef getVarTerm();

  protected @Nullable ArendRef getApplyAxiom() {
    return null;
  }

  protected @Nullable Pair<NF,NF> abstractNF(@NotNull Hint<NF> hint, @NotNull NF nf, int[] position) {
    return null;
  }

  protected ConcreteExpression nfToConcrete(NF nf, ConcreteFactory factory) {
    return null;
  }

  protected record HintResult<NF>(ConcreteExpression proof, NF newNF) {}

  protected @Nullable HintResult<NF> applyHint(@NotNull Hint<NF> hint, @NotNull NF current, int[] position, @NotNull Lazy<ArendRef> solverRef, @NotNull Lazy<ArendRef> envRef, @NotNull ConcreteFactory factory) {
    NF newNF, abstracted;
    ArendRef lemmaRef;
    if (hint.leftNF.equals(current)) {
      lemmaRef = termsEqualityConv;
      abstracted = null;
      newNF = hint.rightNF;
    } else {
      lemmaRef = getApplyAxiom();
      if (lemmaRef == null) return null;
      Pair<NF,NF> pair = abstractNF(hint, current, position);
      if (pair == null) return null;
      abstracted = pair.proj1;
      newNF = pair.proj2;
    }

    ConcreteAppBuilder builder = factory.appBuilder(factory.ref(lemmaRef))
        .app(factory.ref(solverRef.get()), false)
        .app(factory.ref(envRef.get()))
        .app(hint.left.generateReflectedTerm(factory, getVarTerm()))
        .app(hint.right.generateReflectedTerm(factory, getVarTerm()))
        .app(factory.core(hint.typed));
    if (abstracted != null) {
      builder.app(nfToConcrete(abstracted, factory));
    }
    return new HintResult<>(builder.build(), newNF);
  }

  protected @Nullable Pair<HintResult<NF>, HintResult<NF>> applyHints(@NotNull List<Hint<NF>> hints, @NotNull NF left, @NotNull NF right, @NotNull Lazy<ArendRef> solverRef, @NotNull Lazy<ArendRef> envRef, @NotNull Values<CoreExpression> values, @NotNull ExpressionTypechecker typechecker, @NotNull ConcreteFactory factory) {
    ConcreteExpression proofLeft = null;
    ConcreteExpression proofRight = null;
    NF currentLeft = left;
    NF currentRight = right;

    for (Hint<NF> hint : hints) {
      if (currentLeft.equals(currentRight)) {
        typechecker.getErrorReporter().report(new TypecheckingError(GeneralError.Level.WARNING_UNUSED, "Argument is ignored", hint.originalExpression));
        continue;
      }

      int[] position = new int[] { 0 };
      HintResult<NF> resultLeft = applyHint(hint, currentLeft, position, solverRef, envRef, factory);
      HintResult<NF> resultRight = applyHint(hint, currentRight, position, solverRef, envRef, factory);
      if (resultLeft == null && resultRight == null) {
        typechecker.getErrorReporter().report(getApplyAxiom() == null
            ? new EquationTypeMismatchError<>(getNFPrettyPrinter(), currentLeft, currentRight, hint.leftNF, hint.rightNF, values.getValues(), hint.originalExpression)
            : new EquationFindError<>(getNFPrettyPrinter(), currentLeft, currentRight, hint.leftNF, values.getValues(), hint.originalExpression));
        return null;
      }

      if (hint.positions != null) {
        for (Integer allowedPos : hint.positions) {
          if (allowedPos > position[0]) {
            typechecker.getErrorReporter().report(new TypecheckingError(GeneralError.Level.WARNING_UNUSED, "Positions >" + position[0] + " are ignored", hint.originalExpression instanceof ConcreteAppExpression appExpr ? appExpr.getFunction() : hint.originalExpression));
            break;
          }
        }
      }

      if (resultLeft != null) {
        proofLeft = proofLeft == null ? resultLeft.proof : factory.app(factory.ref(concat), true, proofLeft, resultLeft.proof);
        currentLeft = resultLeft.newNF;
      }
      if (resultRight != null) {
        proofRight = proofRight == null ? resultRight.proof : factory.app(factory.ref(concat), true, proofRight, resultRight.proof);
        currentRight = resultRight.newNF;
      }
    }

    return new Pair<>(new HintResult<>(proofLeft, currentLeft), new HintResult<>(proofRight, currentRight));
  }

  /**
   * Applies a hint to an argument in the forward mode.
   */
  protected @Nullable ConcreteExpression applyForward(@NotNull List<Hint<NF>> hints, @NotNull NF left, @NotNull NF right, @NotNull ConcreteExpression proof, @NotNull Values<CoreExpression> values, @NotNull TypedExpression instance, @NotNull Lazy<ArendRef> solverRef, @NotNull Lazy<ArendRef> envRef, @NotNull ExpressionTypechecker typechecker, @NotNull ConcreteFactory factory) {
    var pair = applyHints(hints, left, right, solverRef, envRef, values, typechecker, factory);
    if (pair == null) return null;
    if (pair.proj2.proof != null) {
      proof = factory.app(factory.ref(concat), true, proof, pair.proj2.proof);
    }
    if (pair.proj1.proof != null) {
      proof = factory.app(factory.ref(concat), true, factory.app(factory.ref(inv), true, pair.proj1.proof), proof);
    }
    return factory.typed(proof, factory.app(factory.ref(typechecker.getPrelude().getEqualityRef()), true, nfToConcrete(pair.proj1.newNF, values, instance, factory), nfToConcrete(pair.proj2.newNF, values, instance, factory)));
  }

  /**
   * Solves the goal.
   */
  protected @Nullable ConcreteExpression solve(@NotNull List<Hint<NF>> hints, @Nullable ConcreteExpression proof, @NotNull NF left, @NotNull NF right, @NotNull Values<CoreExpression> values, @Nullable TypedExpression instance, @NotNull Lazy<ArendRef> solverRef, @NotNull Lazy<ArendRef> envRef, @NotNull ExpressionTypechecker typechecker, @NotNull ConcreteExpression marker) {
    ConcreteFactory factory = typechecker.getFactory().withData(marker);
    var pair = applyHints(hints, left, right, solverRef, envRef, values, typechecker, factory);
    if (pair == null) return null;

    if (pair.proj1.newNF.equals(pair.proj2.newNF)) {
      if (proof != null) {
        typechecker.getErrorReporter().report(new TypecheckingError(GeneralError.Level.WARNING_UNUSED, "Argument is ignored", proof));
      }
      return pair.proj1.proof == null && pair.proj2.proof == null ? factory.ref(typechecker.getPrelude().getIdpRef()) : pair.proj2.proof == null ? pair.proj1.proof : pair.proj1.proof == null ? pair.proj2.proof : factory.app(factory.ref(concat), true, pair.proj1.proof, factory.app(factory.ref(inv), true, pair.proj2.proof));
    }

    if (proof == null) {
      typechecker.getErrorReporter().report(new EquationSolveError<>(getNFPrettyPrinter(), left, right, pair.proj1.newNF, pair.proj2.newNF, values.getValues(), marker));
      return null;
    } else {
      ConcreteExpression result = checkProof(proof, pair.proj1.newNF, pair.proj2.newNF, values, instance, typechecker, factory);
      if (result == null) return null;
      if (pair.proj2.proof != null) {
        result = factory.app(factory.ref(concat), true, result, factory.app(factory.ref(inv), true, pair.proj2.proof));
      }
      if (pair.proj1.proof != null) {
        result = factory.app(factory.ref(concat), true, pair.proj1.proof, result);
      }
      return result;
    }
  }

  protected @Nullable ConcreteExpression checkProof(@NotNull ConcreteExpression proof, @NotNull NF left, @NotNull NF right, @NotNull Values<CoreExpression> values, @Nullable TypedExpression instance, @NotNull ExpressionTypechecker typechecker, @NotNull ConcreteFactory factory) {
    TypedExpression type = typechecker.typecheckType(factory.app(factory.ref(typechecker.getPrelude().getEqualityRef()), true, nfToConcrete(left, values, instance, factory), nfToConcrete(right, values, instance, factory)));
    if (type == null) {
      return null;
    }

    TypedExpression proofCore = typechecker.typecheck(proof, type.getExpression());
    return proofCore == null ? null : factory.core(proofCore);
  }

  public record Hint<NF>(@Nullable Set<Integer> positions, TypedExpression typed, EquationTerm left, EquationTerm right, NF leftNF, NF rightNF, ConcreteExpression originalExpression) {}

  protected @Nullable Set<Integer> getPositions(ConcreteExpression expression) {
    if (expression instanceof ConcreteNumberExpression) {
      Integer position = Utils.getNumber(expression, null, true);
      return position == null ? null : Collections.singleton(position);
    } else if (expression instanceof ConcreteTupleExpression tupleExpr && !tupleExpr.getFields().isEmpty()) {
      Set<Integer> result = new HashSet<>();
      for (ConcreteExpression field : tupleExpr.getFields()) {
        Integer position = Utils.getNumber(field, null, true);
        if (position == null) return null;
        result.add(position);
      }
      return result;
    } else {
      return null;
    }
  }

  protected @Nullable Hint<NF> parseHint(@NotNull ConcreteExpression hint, @NotNull CoreExpression hintType, @NotNull List<TermOperation> operations, @NotNull Values<CoreExpression> values, @NotNull ExpressionTypechecker typechecker) {
    Set<Integer> positions;
    if (getApplyAxiom() != null && hint instanceof ConcreteAppExpression appExpr && appExpr.getArguments().getFirst().isExplicit()) {
      positions = getPositions(appExpr.getFunction());
      if (positions != null) {
        hint = appExpr.getArguments().getFirst().getExpression();
        if (appExpr.getArguments().size() > 1) {
          hint = typechecker.getFactory().withData(hint).app(hint, appExpr.getArguments().subList(1, appExpr.getArguments().size()));
        }
      }
    } else {
      positions = null;
    }

    TypedExpression typed = Utils.typecheckWithAdditionalArguments(hint, typechecker, 0, false);
    if (typed == null) {
      return null;
    }

    CoreFunCallExpression equality = Utils.toEquality(typed.getType().normalize(NormalizationMode.WHNF), typechecker.getErrorReporter(), hint);
    if (equality == null || !typechecker.compare(equality.getDefCallArguments().getFirst(), hintType, CMP.LE, hint, true, true, false)) {
      return null;
    }

    EquationTerm left = EquationTerm.match(equality.getDefCallArguments().get(1), operations, values);
    EquationTerm right = EquationTerm.match(equality.getDefCallArguments().get(2), operations, values);
    return new Hint<>(positions, typed, left, right, normalize(left), normalize(right), hint);
  }

  protected @Nullable List<Hint<NF>> parseHints(@Nullable ConcreteExpression hints, @NotNull CoreExpression hintType, @NotNull List<TermOperation> operations, @NotNull Values<CoreExpression> values, @NotNull ExpressionTypechecker typechecker) {
    if (hints == null) return Collections.emptyList();
    List<Hint<NF>> result = new ArrayList<>();
    for (ConcreteExpression hintExpr : Utils.getArgumentList(hints)) {
      Hint<NF> hint = parseHint(hintExpr, hintType, operations, values, typechecker);
      if (hint == null) return null;
      result.add(hint);
    }
    if (result.isEmpty()) {
      typechecker.getErrorReporter().report(new TypecheckingError(GeneralError.Level.WARNING_UNUSED, "Argument is redundant", hints));
    }
    return result;
  }

  @Override
  public boolean @Nullable [] argumentExplicitness() {
    return new boolean[] { false, true };
  }

  @Override
  public int numberOfOptionalExplicitArguments() {
    return 1;
  }

  @Override
  public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    ConcreteExpression marker = contextData.getMarker();
    CoreFunCallExpression equality;
    boolean isForward = contextData.getExpectedType() == null;
    TypedExpression argTyped;

    List<? extends ConcreteArgument> arguments = contextData.getArguments();
    ConcreteExpression hint = arguments.isEmpty() || arguments.getFirst().isExplicit() ? null : arguments.getFirst().getExpression();
    ConcreteExpression argument = arguments.isEmpty() || !arguments.getLast().isExplicit() ? null : arguments.getLast().getExpression();

    if (isForward) {
      if (argument == null) {
        typechecker.getErrorReporter().report(new MissingArgumentsError(1, marker));
        return null;
      }

      argTyped = typechecker.typecheck(argument, null);
      if (argTyped == null) {
        return null;
      }

      equality = Utils.toEquality(argTyped.getType().normalize(NormalizationMode.WHNF), typechecker.getErrorReporter(), marker);
    } else {
      argTyped = null;
      equality = Utils.toEquality(contextData.getExpectedType().normalize(NormalizationMode.WHNF), typechecker.getErrorReporter(), marker);
    }

    if (equality == null) {
      return null;
    }

    Pair<TypedExpression, CoreClassCallExpression> instance = Utils.findInstanceWithClassCall(new SubclassSearchParameters(getClassDef()), carrier, equality.getDefCallArguments().getFirst(), typechecker, marker, getClassDef());
    if (instance == null) {
      return null;
    }

    Values<CoreExpression> values = new Values<>(typechecker,  marker);
    ConcreteFactory factory = contextData.getFactory();

    List<TermOperation> operations = getOperations(instance.proj1, instance.proj2, typechecker, factory, marker);
    EquationTerm left = EquationTerm.match(equality.getDefCallArguments().get(1), operations, values);
    EquationTerm right = EquationTerm.match(equality.getDefCallArguments().get(2), operations, values);
    NF leftNF = normalize(left);
    NF rightNF = normalize(right);

    Lazy<ArendRef> solverRef = new Lazy<>(() -> factory.local("solver"));
    Lazy<ArendRef> envRef = new Lazy<>(() -> factory.local("env"));

    ConcreteExpression proof;
    if (argTyped != null) {
      List<Hint<NF>> hints = parseHints(hint, equality.getDefCallArguments().getFirst(), operations, values, typechecker);
      if (hints == null) return null;
      ConcreteExpression argConcrete = factory.appBuilder(factory.ref(termsEqualityConv))
          .app(factory.ref(solverRef.get()), false)
          .app(factory.ref(envRef.get()))
          .app(left.generateReflectedTerm(factory, getVarTerm()))
          .app(right.generateReflectedTerm(factory, getVarTerm()))
          .app(factory.core(argTyped))
          .build();
      proof = applyForward(hints, leftNF, rightNF, argConcrete, values, instance.proj1, solverRef, envRef, typechecker, factory);
      if (proof == null) {
        return null;
      }
    } else if (leftNF.equals(rightNF)) {
      proof = factory.ref(typechecker.getPrelude().getIdpRef());
      if (hint != null) {
        typechecker.getErrorReporter().report(new TypecheckingError(GeneralError.Level.WARNING_UNUSED, "Argument is ignored", hint));
      }
      if (argument != null) {
        typechecker.getErrorReporter().report(new TypecheckingError(GeneralError.Level.WARNING_UNUSED, "Argument is ignored", argument));
      }
    } else {
      List<Hint<NF>> hints = parseHints(hint, equality.getDefCallArguments().getFirst(), operations, values, typechecker);
      if (hints == null) return null;
      proof = solve(hints, argument, leftNF, rightNF, values, instance.proj1, solverRef, envRef, typechecker, marker);
      if (proof == null) {
        return null;
      }
    }

    ConcreteExpression solver = factory.app(factory.ref(getSolverModel()), true, factory.core(instance.proj1));
    ConcreteExpression env = Utils.makeArray(values.getValues().stream().map(it -> factory.core(it.computeTyped())).toList(), factory, typechecker.getPrelude());
    ConcreteExpression result = isForward ? proof : factory.appBuilder(factory.ref(termsEquality))
        .app(solverRef.isUsed() ? factory.ref(solverRef.get()) : solver, false)
        .app(envRef.isUsed() ? factory.ref(envRef.get()) : env)
        .app(left.generateReflectedTerm(factory, getVarTerm()))
        .app(right.generateReflectedTerm(factory, getVarTerm()))
        .app(proof)
        .build();
    if (solverRef.isUsed() || envRef.isUsed()) {
      List<ConcreteLetClause> clauses = new ArrayList<>(2);
      if (solverRef.isUsed()) {
        clauses.add(factory.letClause(solverRef.get(), Collections.emptyList(), null, solver));
      }
      if (envRef.isUsed()) {
        clauses.add(factory.letClause(envRef.get(), Collections.emptyList(), null, env));
      }
      result = factory.letExpr(false, false, clauses, result);
    }
    return typechecker.typecheck(result, null);
  }
}
