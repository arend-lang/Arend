package org.arend.core.expr.visitor;

import org.arend.core.definition.Definition;
import org.arend.core.expr.DefCallExpression;
import org.arend.ext.core.definition.CoreDefinition;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.DefinitionRenamer;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.ModuleReferable;

import java.util.*;

public class DefCallRenamer extends VoidExpressionVisitor<Void> implements DefinitionRenamer {
  // Names that we already met
  private final Set<String> myNames;

  // If this map contains a pair (s,d), then s is in myNames and d is not in myDefLongNames.
  // If s is in myNames, but not in this map, then we already found a conflict.
  private final Map<String, CoreDefinition> myNotRenamedDefs;

  // The result map
  private final Map<CoreDefinition, LongName> myDefLongNames;

  public DefCallRenamer(DefinitionRenamer renamer) {
    myNames = renamer == null ? new HashSet<>() : renamer.getNames();
    myNotRenamedDefs = renamer == null ? new HashMap<>() : renamer.getNotRenamedDefs();
    myDefLongNames = renamer == null ? new HashMap<>() : renamer.getDefLongNames();
  }

  LongName getDefLongName(DefCallExpression defCall) {
    return myDefLongNames.get(defCall.getDefinition());
  }

  private void rename(Definition definition) {
    LocatedReferable ref = definition.getRef().getLocatedReferableParent();
    if (ref == null || ref instanceof ModuleReferable) {
      ModulePath modulePath = ref != null ? ((ModuleReferable) ref).path : definition.getRef().getLocation();
      if (modulePath != null) {
        myDefLongNames.put(definition, modulePath);
      }
    } else {
      List<String> list = new ArrayList<>();
      while (ref != null && !(ref instanceof ModuleReferable)) {
        list.add(ref.getRefName());
        ref = ref.getLocatedReferableParent();
      }
      Collections.reverse(list);
      myDefLongNames.put(definition, new LongName(list));
    }
  }

  @Override
  public Void visitDefCall(DefCallExpression expr, Void params) {
    String name = expr.getDefinition().getName();
    if (!myNames.add(name)) {
      CoreDefinition definition = myNotRenamedDefs.get(name);
      if (definition != expr.getDefinition()) { // if expr.getDefinition() is not yet renamed, we shouldn't rename it now since there are no conflicts yet
        if (definition != null) { // if definition is not null, then we found a conflict
          myNotRenamedDefs.remove(name);
          if (definition instanceof Definition) {
            rename((Definition) definition);
          }
        }
        if (!myDefLongNames.containsKey(expr.getDefinition())) {
          rename(expr.getDefinition());
        }
      }
    } else {
      myNotRenamedDefs.put(name, expr.getDefinition());
    }
    return super.visitDefCall(expr, params);
  }

  @Override
  public Set<String> getNames() {
    return myNames;
  }

  @Override
  public Map<String, CoreDefinition> getNotRenamedDefs() {
    return myNotRenamedDefs;
  }

  @Override
  public Map<CoreDefinition, LongName> getDefLongNames() {
    return myDefLongNames;
  }

  @Override
  public void clear() {
    myNames.clear();
    myNotRenamedDefs.clear();
    myDefLongNames.clear();
  }
}
