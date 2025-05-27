package org.arend.lib.meta.equation.datafactory;

import org.arend.ext.ArendPrelude;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.meta.simplify.SimplifyMeta;
import org.arend.lib.util.Values;

import java.util.Arrays;

public class GroupDataFactory extends DataFactoryBase {
  private final ArendRef groupData;

  public GroupDataFactory(SimplifyMeta meta, ArendPrelude prelude, ArendRef dataRef, Values<CoreExpression> values, ConcreteFactory factory, TypedExpression instance, boolean isCommutative) {
    super(prelude, dataRef, values, factory, instance);
    this.groupData = isCommutative ? meta.CGroupData : meta.GroupData;
  }

  @Override
  protected ConcreteExpression getDataClass(ConcreteExpression instanceArg, ConcreteExpression dataArg) {
    ConcreteExpression data = factory.ref(groupData);
    return factory.app(data, Arrays.asList(factory.arg(instanceArg, false), factory.arg(dataArg, true)));
  }
}
