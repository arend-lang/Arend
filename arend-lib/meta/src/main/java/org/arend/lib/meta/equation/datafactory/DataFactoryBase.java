package org.arend.lib.meta.equation.datafactory;

import org.arend.ext.ArendPrelude;
import org.arend.ext.concrete.ConcreteClause;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.ConcreteLetClause;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreFunCallExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.util.Values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.singletonList;

public abstract class DataFactoryBase implements DataFactory {
  private final ArendPrelude prelude;
  protected final ConcreteFactory factory;
  protected CoreFunCallExpression equality;
  protected final TypedExpression instance;
  protected final Values<CoreExpression> values;
  protected final ArendRef dataRef;
  protected final List<ConcreteLetClause> letClauses;

  public DataFactoryBase(ArendPrelude prelude, ArendRef dataRef, Values<CoreExpression> values, ConcreteFactory factory, TypedExpression instance) {
    this.prelude = prelude;
    this.factory = factory;
    this.instance = instance;
    //dataRef = factory.local("d");
    this.dataRef = dataRef;
    letClauses = new ArrayList<>();
    letClauses.add(null);
    this.values = values;
  }

  protected abstract ConcreteExpression getDataClass(ConcreteExpression instanceArg, ConcreteExpression dataArg);

  public static ConcreteExpression makeFin(int n, ConcreteFactory factory, ArendRef fin) {
    return factory.app(factory.ref(fin), true, factory.number(n));
  }

  public static ConcreteExpression makeLambda(Values<CoreExpression> values, ConcreteFactory factory, ArendPrelude prelude) {
    ArendRef lamParam = factory.local("j");
    List<CoreExpression> valueList = values.getValues();
    ConcreteClause[] caseClauses = new ConcreteClause[valueList.size()];
    for (int i = 0; i < valueList.size(); i++) {
      caseClauses[i] = factory.clause(singletonList(factory.numberPattern(i)), factory.core(valueList.get(i).computeTyped()));
    }
    return factory.lam(singletonList(factory.param(singletonList(lamParam), makeFin(valueList.size(), factory, prelude.getFinRef()))),
            factory.caseExpr(false, singletonList(factory.caseArg(factory.ref(lamParam), null, null)), null, null, caseClauses));
  }

  @Override
  public ConcreteExpression wrapWithData(ConcreteExpression expression) {
    List<CoreExpression> valueList = values.getValues();

    ConcreteExpression instanceArg = factory.core(instance);
    ConcreteExpression dataArg = factory.ref(prelude.getEmptyArrayRef());
    for (int i = valueList.size() - 1; i >= 0; i--) {
      dataArg = factory.app(factory.ref(prelude.getArrayConsRef()), true, factory.core(null, valueList.get(i).computeTyped()), dataArg);
    }

    letClauses.set(0, factory.letClause(dataRef, Collections.emptyList(), null, factory.newExpr(getDataClass(instanceArg, dataArg))));
    return factory.letExpr(false, false, letClauses, expression);
  }
}
