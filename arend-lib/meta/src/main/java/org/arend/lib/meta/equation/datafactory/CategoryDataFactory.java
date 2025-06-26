package org.arend.lib.meta.equation.datafactory;

import org.arend.ext.ArendPrelude;
import org.arend.ext.concrete.ConcreteClause;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.ConcreteLetClause;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.util.Pair;
import org.arend.lib.meta.equation.BaseEquationMeta;
import org.arend.lib.util.Values;

import java.util.*;

public class CategoryDataFactory implements DataFactory {
  private final BaseEquationMeta meta;
  private final ArendPrelude prelude;
  private final ConcreteFactory factory;
  private final TypedExpression instance;
  private final Values<CoreExpression> homValues;
  private final Values<CoreExpression> obValues;
  private final Map<Pair<Integer,Integer>, Map<Integer,Integer>> homMap;
  private final ArendRef dataRef;

  public CategoryDataFactory(BaseEquationMeta meta, ArendPrelude prelude, ArendRef dataRef, Values<CoreExpression> homValues, Values<CoreExpression> obValues, Map<Pair<Integer,Integer>, Map<Integer,Integer>> homMap, ConcreteFactory factory, TypedExpression instance) {
    this.meta = meta;
    this.prelude = prelude;
    this.factory = factory;
    this.instance = instance;
    this.homValues = homValues;
    this.obValues = obValues;
    this.homMap = homMap;
    this.dataRef = dataRef;
  }

  protected ConcreteExpression makeLambda2() {
    ArendRef lamParam1 = factory.local("i");
    ArendRef lamParam2 = factory.local("j");
    List<CoreExpression> valueList = obValues.getValues();
    List<ConcreteClause> caseClauses = new ArrayList<>();
    for (Map.Entry<Pair<Integer, Integer>, Map<Integer, Integer>> entry : homMap.entrySet()) {
      caseClauses.add(factory.clause(Arrays.asList(factory.numberPattern(entry.getKey().proj1), factory.numberPattern(entry.getKey().proj2)), DataFactoryBase.makeFin(entry.getValue().size(), factory, prelude.getFinRef())));
    }
    if (homMap.size() < valueList.size() * valueList.size()) {
      caseClauses.add(factory.clause(Arrays.asList(factory.refPattern(null, null), factory.refPattern(null, null)), DataFactoryBase.makeFin(0, factory, prelude.getFinRef())));
    }
    return factory.lam(Arrays.asList(factory.param(lamParam1), factory.param(lamParam2)),
            factory.caseExpr(false, Arrays.asList(factory.caseArg(factory.ref(lamParam1), null, null), factory.caseArg(factory.ref(lamParam2), null, null)), null, null, caseClauses));
  }

  protected ConcreteExpression makeLambda3() {
    ArendRef lamParam1 = factory.local("i");
    ArendRef lamParam2 = factory.local("j");
    ArendRef lamParam3 = factory.local("k");
    List<ConcreteClause> caseClauses = new ArrayList<>();
    for (Map.Entry<Pair<Integer, Integer>, Map<Integer, Integer>> entry : homMap.entrySet()) {
      int i = 0;
      for (Integer index : entry.getValue().keySet()) {
        caseClauses.add(factory.clause(Arrays.asList(factory.numberPattern(entry.getKey().proj1), factory.numberPattern(entry.getKey().proj2), factory.numberPattern(i++)), factory.core(homValues.getValue(index).computeTyped())));
      }
    }
    return factory.lam(Arrays.asList(factory.param(false, lamParam1), factory.param(false, lamParam2), factory.param(lamParam3)),
            factory.caseExpr(false, Arrays.asList(factory.caseArg(factory.ref(lamParam1), null), factory.caseArg(factory.ref(lamParam2), null), factory.caseArg(factory.ref(lamParam3), null, null)), null, null, caseClauses));
  }

  @Override
  public ConcreteExpression wrapWithData(ConcreteExpression expression) {
    List<ConcreteLetClause> letClauses = new ArrayList<>();
    letClauses.add(null);
    letClauses.set(0, factory.letClause(dataRef, Collections.emptyList(), null, factory.newExpr(factory.appBuilder(factory.ref(meta.HData))
            .app(factory.core(instance), false)
            .app(DataFactoryBase.makeLambda(obValues, factory, prelude))
            .app(makeLambda2())
            .app(makeLambda3())
            .build())));
    return factory.letExpr(false, false, letClauses, expression);
  }
}
