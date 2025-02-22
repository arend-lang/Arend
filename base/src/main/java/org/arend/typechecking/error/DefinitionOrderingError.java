package org.arend.typechecking.error;

import org.arend.ext.error.GeneralError;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.arend.ext.reference.ArendRef;
import org.arend.naming.reference.TCDefReferable;
import org.arend.term.concrete.Concrete;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;

public class DefinitionOrderingError extends GeneralError {
  private final Concrete.ResolvableDefinition myDefinition;
  public final List<Concrete.ResolvableDefinition> definitions;

  private DefinitionOrderingError(Concrete.ResolvableDefinition definition, List<Concrete.ResolvableDefinition> definitions) {
    super(Level.ERROR, "Cannot order definitions");
    myDefinition = definition;
    this.definitions = definitions;
  }

  public DefinitionOrderingError(List<Concrete.ResolvableDefinition> definitions) {
    this(definitions.get(0), definitions);
  }

  @Override
  public LineDoc getShortHeaderDoc(PrettyPrinterConfig ppConfig) {
    List<LineDoc> refs = new ArrayList<>(definitions.size());
    for (Concrete.ResolvableDefinition definition : definitions) {
      refs.add(refDoc(definition.getData()));
    }
    return hList(text(message + ": "), hSep(text(", "), refs));
  }

  @Override
  public TCDefReferable getCause() {
    return myDefinition.getData();
  }

  @NotNull
  @Override
  public Stage getStage() {
    return Stage.TYPECHECKER;
  }

  @Override
  public void forAffectedDefinitions(BiConsumer<ArendRef, GeneralError> consumer) {
    for (Concrete.ResolvableDefinition def : definitions) {
      consumer.accept(def.getData(), new DefinitionOrderingError(def, definitions));
    }
  }
}
