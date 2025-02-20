package org.arend.typechecking.computation;

import org.arend.naming.reference.TCReferable;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.order.listener.CollectingOrderingListener;

import java.util.List;
import java.util.Set;

public class TypecheckingCancellationIndicator extends BooleanCancellationIndicator {
  private final CancellationIndicator myIndicator;
  private List<CollectingOrderingListener.Element> myElements;

  public TypecheckingCancellationIndicator(CancellationIndicator indicator) {
    myIndicator = indicator;
  }

  public void setElements(List<CollectingOrderingListener.Element> elements) {
    myElements = elements;
  }

  @Override
  public boolean isCanceled() {
    return super.isCanceled() || myIndicator.isCanceled();
  }

  @Override
  public void cancel() {
    super.cancel();
    myIndicator.cancel();
  }

  @Override
  public void cancel(Set<? extends TCReferable> definitions) {
    if (myElements == null) return;
    for (CollectingOrderingListener.Element element : myElements) {
      for (Concrete.ResolvableDefinition definition : element.getAllDefinitions()) {
        if (definitions.contains(definition.getData())) {
          cancel();
          return;
        }
      }
    }
  }
}
