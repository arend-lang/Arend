package org.arend.lib.meta.equation.datafactory;

import org.arend.ext.ArendPrelude;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.meta.equation.BaseEquationMeta;
import org.arend.lib.util.Values;

import java.util.Arrays;

public class RingDataFactory extends DataFactoryBase {
  private final BaseEquationMeta meta;
  private final boolean isLattice;
  private final boolean isRing;
  private final boolean isCommutative;

  public RingDataFactory(BaseEquationMeta meta, ArendPrelude prelude, ArendRef dataRef, Values<CoreExpression> values, ConcreteFactory factory, TypedExpression instance, boolean isLattice, boolean isRing, boolean isCommutative) {
    super(prelude, dataRef, values, factory, instance);
    this.meta = meta;
    this.isLattice = isLattice;
    this.isRing = isRing;
    this.isCommutative = isCommutative;
  }

  @Override
  protected ConcreteExpression getDataClass(ConcreteExpression instanceArg, ConcreteExpression dataArg) {
    ConcreteExpression data = factory.ref(isLattice ? meta.LatticeData : (isRing ? (isCommutative ? meta.CRingData : meta.RingData) : (isCommutative ? meta.CSemiringData : meta.SemiringData)));
    return factory.classExt(data, Arrays.asList(factory.implementation(isLattice ? meta.LatticeDataCarrier : meta.RingDataCarrier, instanceArg), factory.implementation(meta.DataFunction, dataArg)));
  }
}
