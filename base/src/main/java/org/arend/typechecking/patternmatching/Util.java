package org.arend.typechecking.patternmatching;

import org.arend.core.constructor.ArrayConstructor;
import org.arend.core.constructor.SingleConstructor;
import org.arend.core.context.binding.Binding;
import org.arend.core.context.param.DependentLink;
import org.arend.core.definition.Constructor;
import org.arend.core.definition.DConstructor;
import org.arend.core.elimtree.BranchKey;
import org.arend.core.expr.*;
import org.arend.core.pattern.BindingPattern;
import org.arend.core.pattern.ConstructorExpressionPattern;
import org.arend.core.pattern.ExpressionPattern;
import org.arend.core.subst.Levels;
import org.arend.prelude.Prelude;

import java.util.*;

public class Util {
  public interface ClauseElem {
  }

  public record PatternClauseElem(ExpressionPattern pattern) implements ClauseElem {}

  public interface DataClauseElem extends ClauseElem {
    DependentLink getParameters();
    ConstructorExpressionPattern getPattern(List<ExpressionPattern> subPatterns);
  }

  public static DataClauseElem makeDataClauseElem(BranchKey branchKey, ConstructorExpressionPattern pattern) {
    return switch (branchKey) {
      case SingleConstructor ignored -> new TupleClauseElem(pattern);
      case Constructor constructor -> new ConstructorClauseElem(constructor, pattern.getLevels(), pattern.getDataTypeArguments());
      case ArrayConstructor arrayConstructor -> new ArrayClauseElem(arrayConstructor.getConstructor(), pattern.getArrayLength(), pattern.getArrayThisBinding(), pattern.getArrayElementsType(), pattern.isArrayEmpty());
      case null, default -> throw new IllegalStateException();
    };
  }

  public record TupleClauseElem(ConstructorExpressionPattern pattern) implements DataClauseElem {
    @Override
    public DependentLink getParameters() {
      return pattern.getParameters();
    }

    @Override
    public ConstructorExpressionPattern getPattern(List<ExpressionPattern> subPatterns) {
      return new ConstructorExpressionPattern(pattern, subPatterns);
    }
  }

  public static class ConstructorClauseElem implements DataClauseElem {
    public final List<Expression> dataArguments;
    public final Constructor constructor;
    public final Levels levels;

    public ConstructorClauseElem(Constructor constructor, Levels levels, List<? extends Expression> dataArguments) {
      this.dataArguments = new ArrayList<>(dataArguments);
      this.constructor = constructor;
      this.levels = levels;
    }

    @Override
    public DependentLink getParameters() {
      return constructor.getParameters();
    }

    @Override
    public ConstructorExpressionPattern getPattern(List<ExpressionPattern> subPatterns) {
      return new ConstructorExpressionPattern(new ConCallExpression(constructor, levels, dataArguments, Collections.emptyList()), subPatterns);
    }
  }

  public static class ArrayClauseElem implements DataClauseElem {
    private final DConstructor myConstructor;
    private final Expression myLength;
    private final Binding myThisBinding;
    private final Expression myElementsType;
    private final Boolean myEmpty;

    public ArrayClauseElem(DConstructor constructor, Expression length, Binding thisBinding, Expression elementsType, Boolean isEmpty) {
      myConstructor = constructor;
      myLength = length;
      myThisBinding = thisBinding;
      myElementsType = elementsType;
      myEmpty = isEmpty;
    }

    @Override
    public DependentLink getParameters() {
      return myConstructor.getArrayParameters(myLength, myThisBinding, myElementsType);
    }

    @Override
    public ConstructorExpressionPattern getPattern(List<ExpressionPattern> subPatterns) {
      return new ConstructorExpressionPattern(new FunCallExpression(myConstructor, myConstructor.makeIdLevels(), myLength, myElementsType), myThisBinding, myEmpty, subPatterns);
    }
  }

  public static void unflattenClauses(List<ClauseElem> clauseElems, List<? super ExpressionPattern> result) {
    for (int i = clauseElems.size() - 1; i >= 0; i--) {
      if (clauseElems.get(i) instanceof DataClauseElem dataClauseElem) {
        DependentLink parameters = dataClauseElem.getParameters();
        int size = DependentLink.Helper.size(parameters);
        List<ExpressionPattern> patterns = new ArrayList<>(size);
        for (int j = i + 1; j < clauseElems.size() && patterns.size() < size; j++) {
          patterns.add(((PatternClauseElem) clauseElems.get(j)).pattern);
        }
        if (patterns.size() < size) {
          for (DependentLink link = DependentLink.Helper.get(parameters, clauseElems.size() - i - 1); link.hasNext(); link = link.getNext()) {
            patterns.add(new BindingPattern(link));
          }
        }
        if (dataClauseElem instanceof ArrayClauseElem arrayClauseElem && !patterns.isEmpty() && patterns.getFirst() instanceof ConstructorExpressionPattern && patterns.getFirst().getDefinition() == Prelude.ZERO && patterns.getLast() instanceof BindingPattern) {
          patterns.set(patterns.size() - 1, new ConstructorExpressionPattern(new FunCallExpression(Prelude.EMPTY_ARRAY, Levels.EMPTY, null, arrayClauseElem.myElementsType != null ? arrayClauseElem.myElementsType : FieldCallExpression.make(Prelude.ARRAY_ELEMENTS_TYPE, new ReferenceExpression(arrayClauseElem.myThisBinding))), arrayClauseElem.myThisBinding, (Boolean) null, Collections.emptyList()));
        }
        clauseElems.subList(i, Math.min(i + size + 1, clauseElems.size())).clear();
        clauseElems.add(i, new PatternClauseElem(dataClauseElem.getPattern(patterns)));
      }
    }

    for (ClauseElem clauseElem : clauseElems) {
      result.add(((PatternClauseElem) clauseElem).pattern);
    }
  }

  public static List<ExpressionPattern> unflattenClauses(List<ClauseElem> clauseElems) {
    List<ExpressionPattern> result = new ArrayList<>(clauseElems.size());
    unflattenClauses(clauseElems, result);
    return result;
  }

  static void removeArguments(List<?> clauseElems, DependentLink parameters, List<DependentLink> elimParams) {
    if (parameters != null && elimParams != null && !elimParams.isEmpty()) {
      DependentLink link = parameters;
      for (int i = 0; i < elimParams.size(); i++, link = link.getNext()) {
        while (link != elimParams.get(i)) {
          clauseElems.remove(i);
          link = link.getNext();
        }
      }
      clauseElems.subList(elimParams.size(), clauseElems.size()).clear();
    }
  }

  static void addArguments(List<ExpressionPattern> patterns, DependentLink parameters) {
    for (DependentLink link = DependentLink.Helper.get(parameters, patterns.size()); link.hasNext(); link = link.getNext()) {
      patterns.add(new BindingPattern(link));
    }
  }
}
