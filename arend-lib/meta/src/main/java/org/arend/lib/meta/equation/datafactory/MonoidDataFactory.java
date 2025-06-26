package org.arend.lib.meta.equation.datafactory;

import org.arend.ext.ArendPrelude;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.ConcreteLetClause;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.meta.equation.BaseEquationMeta;
import org.arend.lib.util.Values;

import java.util.Arrays;
import java.util.List;

public class MonoidDataFactory extends DataFactoryBase {
  private final BaseEquationMeta meta;
  private final boolean isSemilattice;
  private final boolean isCommutative;

  public MonoidDataFactory(BaseEquationMeta meta, ArendPrelude prelude, ArendRef dataRef, Values<CoreExpression> values, List<ConcreteLetClause> letClauses, ConcreteFactory factory, TypedExpression instance, boolean isSemilattice, boolean isCommutative) {
    super(prelude, dataRef, values, factory, instance);
    this.meta = meta;
    this.letClauses.addAll(letClauses);
    this.isSemilattice = isSemilattice;
    this.isCommutative = isCommutative;
  }

  @Override
  protected ConcreteExpression getDataClass(ConcreteExpression instanceArg, ConcreteExpression dataArg) {
    ConcreteExpression data = factory.ref(isSemilattice ? meta.LData : isCommutative ? meta.CMonoidData : meta.MonoidData);
    return isSemilattice
            ? factory.classExt(data, Arrays.asList(factory.implementation(meta.SemilatticeDataCarrier, instanceArg), factory.implementation(meta.DataFunction, dataArg)))
            : factory.app(data, Arrays.asList(factory.arg(instanceArg, false), factory.arg(dataArg, true)));
  }
}
