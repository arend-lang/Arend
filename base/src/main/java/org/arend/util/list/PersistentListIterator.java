package org.arend.util.list;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class PersistentListIterator<T> implements Iterator<T> {
  private PersistentList<T> myCurrent;

  public PersistentListIterator(PersistentList<T> list) {
    myCurrent = list;
  }

  @Override
  public boolean hasNext() {
    return myCurrent instanceof ConsList<T>;
  }

  @Override
  public T next() {
    if (!(myCurrent instanceof ConsList<T> cons)) throw new NoSuchElementException();
    myCurrent = cons.getTail();
    return cons.getValue();
  }
}
