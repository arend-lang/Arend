package org.arend.typechecking.error;

import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.Doc;
import org.arend.ext.prettyprinting.doc.DocFactory;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.module.ModuleLocation;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.term.concrete.Concrete;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiConsumer;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;

public class CycleError extends GeneralError {
  public final List<? extends GlobalReferable> cycle;
  public Concrete.SourceNode cause;
  private final GlobalReferable myCauseReferable;
  private final Map<GlobalReferable, List<TCDefReferable>> myInstances;

  private CycleError(String message, List<? extends GlobalReferable> cycle, Map<GlobalReferable, List<TCDefReferable>> instances, GlobalReferable causeReferable, Concrete.SourceNode cause) {
    super(Level.ERROR, message);
    this.cycle = cycle;
    this.cause = cause;
    myInstances = instances;
    myCauseReferable = causeReferable;
  }

  public CycleError(String message, List<? extends GlobalReferable> cycle, Map<GlobalReferable, List<TCDefReferable>> instances) {
    this(message, cycle, instances, null, null);
  }

  public CycleError(List<? extends GlobalReferable> cycle, Map<GlobalReferable, List<TCDefReferable>> instances) {
    this("Dependency cycle", cycle, instances);
  }

  @Override
  public Concrete.SourceNode getCauseSourceNode() {
    return cause;
  }

  @Override
  public void setCauseSourceNode(ConcreteSourceNode sourceNode) {
    if (!(sourceNode instanceof Concrete.SourceNode)) throw new IllegalArgumentException();
    cause = (Concrete.SourceNode) sourceNode;
  }

  @Override
  public Object getCause() {
    if (cause != null) {
      Object data = cause.getData();
      if (data != null) {
        return data;
      }
    }
    return myCauseReferable != null ? myCauseReferable : cycle;
  }

  @Override
  public Doc getCauseDoc(PrettyPrinterConfig ppConfig) {
    Doc causeDoc = super.getCauseDoc(ppConfig);
    return causeDoc != null ? causeDoc : refDoc(cycle.getFirst());
  }

  @Override
  public Doc getBodyDoc(PrettyPrinterConfig src) {
    Set<ModulePath> modules = new LinkedHashSet<>();
    for (GlobalReferable referable : cycle) {
      if (referable instanceof LocatedReferable) {
        ModuleLocation location = ((LocatedReferable) referable).getLocation();
        if (location != null) {
          modules.add(location.getModulePath());
        }
      }
    }

    List<LineDoc> docs = new ArrayList<>(cycle.size() + 1);
    for (GlobalReferable definition : cycle) {
      docs.add(refDoc(definition));
    }
    Doc result = hSep(text(" - "), docs);
    if (modules.size() > 1) {
      List<LineDoc> modulesDocs = new ArrayList<>(modules.size());
      for (ModulePath module : modules) {
        modulesDocs.add(text(module.toString()));
      }
      result = vList(result, hList(text("Located in modules: "), hSep(text(", "), modulesDocs)));
    }

    if (!myInstances.isEmpty()) {
      List<Doc> list = new ArrayList<>();
      for (Map.Entry<GlobalReferable, List<TCDefReferable>> entry : myInstances.entrySet()) {
        list.add(hList(entry.getKey() instanceof LocatedReferable ref ? text((modules.size() > 1 ? ref.getModulePath() + ":" : "") + ref.getRefLongName()) : refDoc(entry.getKey()), text(": "), hSep(text(", "), entry.getValue().stream().map(DocFactory::refDoc).toList())));
      }
      result = vList(result, vHang(text("Instance dependencies:"), vList(list)));
    }

    return result;
  }

  @Override
  public void forAffectedDefinitions(BiConsumer<ArendRef, GeneralError> consumer) {
    Object causeData = cause != null ? cause.getData() : null;
    if (causeData instanceof GlobalReferable) {
      consumer.accept((GlobalReferable) causeData, this);
    } else {
      for (GlobalReferable ref : cycle) {
        consumer.accept(ref, new CycleError(message, cycle, myInstances, ref, cause));
      }
    }
  }

  @NotNull
  @Override
  public Stage getStage() {
    return Stage.TYPECHECKER;
  }
}
