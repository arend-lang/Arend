package org.arend.lib.meta.simplify.field;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.meta.equation.binop_matcher.FunctionMatcher;
import org.arend.lib.meta.equationNew.term.*;
import org.arend.lib.util.Utils;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.arend.lib.meta.simplify.SimplifyMeta;

/**
 * Reflects field expressions into the terms of {@code RingSolverModel} and collects what is needed to apply the
 * lemmas of {@code Algebra.Solver.Field}.
 *
 * <p>An inverse is an atom of the environment. That it is the inverse of its denominator is recorded
 * together with the witness, which is the guard of the solver model.</p>
 */
public final class FieldReifier {
  /** The Java counterpart of {@code InverseOf}: the atom {@code atom} is the inverse of {@code denominator}, and {@code witness} proves that. */
  private record InverseOf(EquationTerm denominator, int atom, CoreExpression witness) {}

  /** A ring operation: how it is matched and how it is reflected. */
  private record Op(CoreClassField projection, int arity, ArendRef constructor, List<TermType> argTypes) {}

  private final SimplifyMeta meta;
  private final ExpressionTypechecker typechecker;
  private final ConcreteFactory factory;
  private final CRingInstance instance;
  private final Values<CoreExpression> values;
  private final TermMatcher matcher;
  private final List<InverseOf> inverses = new ArrayList<>();
  private final Set<Integer> inverseAtoms = new HashSet<>();

  public FieldReifier(SimplifyMeta meta, ExpressionTypechecker typechecker, ConcreteFactory factory, ConcreteSourceNode marker, CRingInstance instance, FieldInverses.Matcher inverseMatcher, Values<CoreExpression> values) {
    this.meta = meta;
    this.typechecker = typechecker;
    this.factory = factory;
    this.instance = instance;
    this.values = values;

    List<TermType> unary = List.of(new TermType.OpType(null));
    List<TermType> binary = List.of(new TermType.OpType(null), new TermType.OpType(null));
    List<TermOperation> operations = new ArrayList<>();
    for (Op op : List.of(
      new Op(meta.zro, 0, meta.ringZro, List.of()),
      new Op(meta.ide, 0, meta.ringIde, List.of()),
      new Op(meta.plus, 2, meta.ringAdd, binary),
      new Op(meta.mul, 2, meta.ringMul, binary),
      new Op(meta.negative, 1, meta.ringNegative, unary),
      new Op(meta.natCoef, 1, meta.ringCoef, List.of(new TermType.NatType())))) {
      operations.add(operation(op, marker));
    }

    matcher = new TermMatcher(operations, values) {
      // Inspect the unreduced expression first: after reduction, operations of a concrete structure unfold into
      // their components and can no longer be recognized as ring operations.
      @Override
      protected @NotNull EquationTerm match(@NotNull CoreExpression expression, @NotNull List<TermOperation> operations) {
        EquationTerm term = matchOperation(expression, operations);
        return term != null ? term : super.match(expression, operations);
      }

      @Override
      protected @Nullable EquationTerm matchOperation(@NotNull CoreExpression expression, @NotNull List<TermOperation> operations) {
        var inverse = inverseMatcher.match(expression);
        if (inverse == null) return super.matchOperation(expression, operations);
        // The denominator goes first: its atoms must be introduced before the atom of the inverse.
        EquationTerm denominator = match(inverse.denominator(), operations);
        int atom = values.addValue(expression);
        if (inverseAtoms.add(atom)) inverses.add(new InverseOf(denominator, atom, inverse.witness()));
        return new VarTerm(atom);
      }
    };
  }

  private TermOperation operation(Op op, ConcreteSourceNode marker) {
    FunctionMatcher fallback = FunctionMatcher.makeFieldMatcher(instance.asRing().classCall(), instance.asRing().expression(), op.projection(), typechecker, factory, marker, op.arity());
    // The operation is matched without unfolding its implementation first.
    FunctionMatcher syntactic = expression -> {
      List<CoreExpression> arguments = FieldOperationMatcher.match(expression, op.projection(), instance.asRing().expression(), op.arity(), typechecker);
      return arguments != null ? arguments : fallback.match(expression);
    };
    return new TermOperation(op.constructor(), syntactic, op.argTypes());
  }

  public EquationTerm reify(@NotNull CoreExpression expression) {
    return matcher.match(expression);
  }

  public boolean hasInverses() {
    return !inverses.isEmpty();
  }

  /** The array of atoms; it is typechecked once so that types that mention it do not depend on a local binding. */
  public @Nullable TypedExpression environment(CoreExpression carrier) {
    ConcreteExpression arrayType = factory.app(factory.ref(factory.getPrelude().getArrayRef()), true, Utils.concrete(factory, carrier), factory.number(values.getValues().size()));
    TypedExpression type = typechecker.typecheck(arrayType, null);
    if (type == null) return null;
    return typechecker.typecheck(Utils.makeArray(values.getValues().stream().map(value -> Utils.concrete(factory, value)).toList(), factory), type.getExpression());
  }

  /**
   * Applies a lemma of {@code Algebra.Solver.Field} to everything except the proof: the ring, the environment,
   * the inverses with their witnesses, and the reflected terms.
   */
  public ConcreteExpression apply(ArendRef lemma, ConcreteExpression env, EquationTerm left, EquationTerm right) {
    // The first inverse is the last one that was introduced.
    List<ConcreteExpression> inverseTerms = new ArrayList<>();
    ConcreteExpression witnesses = factory.ref(meta.witnessesNil);
    for (InverseOf inverse : inverses) {
      inverseTerms.addFirst(factory.app(factory.ref(meta.inverseOf), true, reflect(inverse.denominator()), factory.number(inverse.atom())));
      witnesses = factory.app(factory.ref(meta.witnessesCons), true, Utils.concrete(factory, inverse.witness()), factory.ref(typechecker.getPrelude().getIdpRef()), witnesses);
    }
    return factory.appBuilder(factory.ref(lemma))
      .app(factory.core(instance.asRing().expression()), false)
      .app(env)
      .app(Utils.makeArray(inverseTerms, factory))
      .app(witnesses)
      .app(reflect(left))
      .app(reflect(right))
      .build();
  }

  private ConcreteExpression reflect(EquationTerm term) {
    return term.generateReflectedTerm(factory, meta.ringVar);
  }
}
