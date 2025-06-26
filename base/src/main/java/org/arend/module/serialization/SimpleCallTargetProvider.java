package org.arend.module.serialization;

import org.arend.core.definition.Definition;
import org.arend.ext.module.LongName;
import org.arend.ext.serialization.DeserializationException;
import org.arend.ext.module.ModuleLocation;
import org.arend.naming.reference.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleCallTargetProvider implements CallTargetProvider {
  private final Map<Integer, LocatedReferable> myMap = new HashMap<>();

  public boolean contains(int index) {
    return myMap.containsKey(index);
  }

  @Override
  public LocatedReferable getRef(int index) throws DeserializationException {
    LocatedReferable ref = myMap.get(index);
    if (ref == null) {
      throw new DeserializationException("Wrong index");
    }
    return ref;
  }

  @Override
  public TCDefReferable getTCRef(int index) throws DeserializationException {
    LocatedReferable ref = getRef(index);
    if (!(ref instanceof TCDefReferable)) {
      throw new DeserializationException("Wrong index");
    }
    return (TCDefReferable) ref;
  }

  @Override
  public Definition getCallTarget(int index) throws DeserializationException {
    TCDefReferable ref = getTCRef(index);
    Definition def = ref.getTypechecked();
    if (def == null) {
      List<String> longName = new ArrayList<>();
      ModuleLocation location = LocatedReferable.Helper.getLocation(ref, longName);
      throw new DeserializationException("Definition " + location.getModulePath() + ":" + new LongName(longName) + " is not loaded");
    }
    return def;
  }

  @Override
  public MetaReferable getMetaCallTarget(int index) throws DeserializationException {
    Referable ref = getRef(index);
    if (!(ref instanceof MetaReferable)) {
      throw new DeserializationException("Not a meta");
    }
    return (MetaReferable) ref;
  }

  public void putCallTarget(int index, TCDefReferable callTarget) {
    myMap.putIfAbsent(index, callTarget);
  }

  public void putCallTarget(int index, Definition callTarget) {
    myMap.putIfAbsent(index, callTarget.getRef());
  }
}
