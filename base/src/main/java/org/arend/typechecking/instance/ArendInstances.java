package org.arend.typechecking.instance;

import org.arend.extImpl.DefaultMetaDefinition;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.MetaReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.provider.ConcreteProvider;
import org.arend.util.list.ConsList;
import org.arend.util.list.PersistentList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public class ArendInstances {
  private final @NotNull PersistentList<TCDefReferable> myInstances;

  private ArendInstances(@NotNull PersistentList<TCDefReferable> instances) {
    myInstances = instances;
  }

  public ArendInstances() {
    this(PersistentList.empty());
  }

  public static TCDefReferable getClassRef(Concrete.Expression type, ConcreteProvider concreteProvider) {
    Set<MetaReferable> visited = new HashSet<>();
    while (true) {
      while (true) {
        if (type instanceof Concrete.ClassExtExpression classExt) {
          type = classExt.getBaseClassExpression();
        } else if (type instanceof Concrete.AppExpression appExpr) {
          type = appExpr.getFunction();
        } else {
          break;
        }
      }
      if (!(type instanceof Concrete.ReferenceExpression refExpr && refExpr.getReferent() instanceof TCDefReferable ref)) {
        return null;
      }
      if (ref.getKind() == GlobalReferable.Kind.CLASS) {
        return ref;
      }
      if (!(ref instanceof MetaReferable metaRef && visited.add(metaRef))) {
        return null;
      }
      if (metaRef.getDefinition() instanceof DefaultMetaDefinition metaDef) {
        type = metaDef.getConcrete().body;
      } else if (concreteProvider != null && concreteProvider.getConcrete(metaRef) instanceof Concrete.MetaDefinition concrete) {
        type = concrete.body;
      } else {
        return null;
      }
      if (type == null) {
        return null;
      }
    }
  }

  public @NotNull ArendInstances addInstance(TCDefReferable instance) {
    return new ArendInstances(new ConsList<>(instance, myInstances));
  }

  public @NotNull ArendInstances removeFirst(TCDefReferable instance) {
    return new ArendInstances(myInstances.removeFirst(instance));
  }

  public @Nullable TCDefReferable find(@NotNull Predicate<TCDefReferable> predicate) {
    return myInstances.find(predicate);
  }

  public @NotNull PersistentList<TCDefReferable> getInstances() {
    return myInstances;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    ArendInstances that = (ArendInstances) o;
    return Objects.equals(myInstances, that.myInstances);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myInstances);
  }
}
