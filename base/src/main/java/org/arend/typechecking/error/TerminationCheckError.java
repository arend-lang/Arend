package org.arend.typechecking.error;

import org.arend.core.definition.Definition;
import org.arend.core.definition.FunctionDefinition;
import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.error.GeneralError;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.Doc;
import org.arend.ext.reference.ArendRef;
import org.arend.naming.reference.GlobalReferable;
import org.jetbrains.annotations.NotNull;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;

public class TerminationCheckError extends GeneralError {
  public GlobalReferable definition;
  public final Set<Definition> definitions;
  public final FunctionDefinition functionDefinition;

  public TerminationCheckError(Definition def, Set<Definition> definitions, FunctionDefinition functionDefinition) {
    super(Level.ERROR, "Termination check failed");
    definition = def.getReferable();
    this.definitions = definitions;
    if (functionDefinition != null) {
      this.functionDefinition = functionDefinition;
      return;
    }
    if (!(def instanceof FunctionDefinition))  {
      this.functionDefinition = null;
      return;
    }
    this.functionDefinition = new FunctionDefinition((FunctionDefinition) def);
  }

  @Override
  public Object getCause() {
    return definition;
  }

  @Override
  public void setCauseSourceNode(ConcreteSourceNode sourceNode) {
    if (sourceNode.getData() instanceof GlobalReferable referable) {
      definition = referable;
    }
  }

  @Override
  public Doc getBodyDoc(PrettyPrinterConfig ppConfig) {
    return vList(definitions.stream().map(rb -> text(rb.getName())).collect(Collectors.toList()));
  }

  @NotNull
  @Override
  public Stage getStage() {
    return Stage.TYPECHECKER;
  }

  @Override
  public boolean hasExpressions() {
    return true;
  }

  @Override
  public void forAffectedDefinitions(BiConsumer<ArendRef, GeneralError> consumer) {
    for (Definition def : definitions) {
      if (def.getReferable() != definition) {
        consumer.accept(def.getReferable(), new TerminationCheckError(def, definitions, null));
      } else {
        consumer.accept(def.getReferable(), new TerminationCheckError(def, definitions, functionDefinition));
      }
    }
  }
}
