package org.arend.typechecking.instance;

import org.arend.core.definition.FunctionDefinition;
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

import java.util.*;
import java.util.function.Predicate;

/**
 * Global definitions collected from the scope of a definition: instances and definitions marked with attributes.
 */
public class ArendInstances {
  private final @NotNull PersistentList<TCDefReferable> myInstances;
  private final @NotNull PersistentList<TCDefReferable> myAttributed;

  private ArendInstances(@NotNull PersistentList<TCDefReferable> instances, @NotNull PersistentList<TCDefReferable> attributed) {
    myInstances = instances;
    myAttributed = attributed;
  }

  public ArendInstances() {
    this(PersistentList.empty(), PersistentList.empty());
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
    return new ArendInstances(new ConsList<>(instance, myInstances), myAttributed);
  }

  public @NotNull ArendInstances addAttributed(TCDefReferable definition) {
    return new ArendInstances(myInstances, new ConsList<>(definition, myAttributed));
  }

  /**
   * Adds {@code definition} as an instance and/or as an attributed definition, depending on its properties.
   */
  public @NotNull ArendInstances addDefinition(TCDefReferable definition) {
    ArendInstances result = this;
    if (definition.getKind() == GlobalReferable.Kind.INSTANCE) {
      result = result.addInstance(definition);
    }
    if (definition.hasAttributes()) {
      result = result.addAttributed(definition);
    }
    return result;
  }

  public static boolean isAppropriate(TCDefReferable definition) {
    return definition.getKind() == GlobalReferable.Kind.INSTANCE || definition.hasAttributes();
  }

  public @NotNull ArendInstances removeFirst(TCDefReferable definition) {
    return new ArendInstances(myInstances.removeFirst(definition), myAttributed.removeFirst(definition));
  }

  public @Nullable TCDefReferable find(@NotNull Predicate<TCDefReferable> predicate) {
    return myInstances.find(predicate);
  }

  public @NotNull PersistentList<TCDefReferable> getInstances() {
    return myInstances;
  }

  /**
   * @return definitions marked with attributes; the most recently added definitions come first.
   */
  public @NotNull PersistentList<TCDefReferable> getAttributed() {
    return myAttributed;
  }

  public @NotNull List<FunctionDefinition> getInstancesList() {
    List<FunctionDefinition> result = new ArrayList<>();
    for (TCDefReferable instance : myInstances) {
      if (instance.getTypechecked() instanceof FunctionDefinition function) {
        result.add(function);
      }
    }
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    ArendInstances that = (ArendInstances) o;
    return Objects.equals(myInstances, that.myInstances) && Objects.equals(myAttributed, that.myAttributed);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myInstances, myAttributed);
  }
}
