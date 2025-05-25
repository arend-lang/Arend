package org.arend.ext.error;

import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.arend.ext.reference.ArendRef;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

import static org.arend.ext.prettyprinting.doc.DocFactory.refDoc;

public class LocalError extends GeneralError {
  public ArendRef definition;

  public LocalError(@NotNull Level level, String message) {
    super(level, message);
  }

  @Override
  public Object getCause() {
    Object cause = super.getCause();
    return cause != null ? cause : definition;
  }

  @Override
  public LineDoc getPositionDoc(PrettyPrinterConfig ppConfig) {
    LineDoc result = super.getPositionDoc(ppConfig);
    if (definition != null && result.isEmpty()) {
      ModulePath module = definition.getModulePath();
      if (module != null) {
        return refDoc(new SourceInfoReference(module.toString(), ""));
      }
    }
    return result;
  }

  @Override
  public void setCauseSourceNode(ConcreteSourceNode sourceNode) {
    if (sourceNode.getData() instanceof ArendRef ref) {
      definition = ref;
    }
  }

  @Override
  public void forAffectedDefinitions(BiConsumer<ArendRef, GeneralError> consumer) {
    if (definition != null) {
      consumer.accept(definition, this);
    } else {
      assert false;
    }
  }

  public LocalError withDefinition(ArendRef definition) {
    this.definition = definition;
    return this;
  }
}
