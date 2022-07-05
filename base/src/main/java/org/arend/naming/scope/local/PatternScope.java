package org.arend.naming.scope.local;

import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.Referable;
import org.arend.naming.scope.DelegateScope;
import org.arend.naming.scope.Scope;
import org.arend.term.abs.Abstract;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public class PatternScope extends DelegateScope {
  private final List<? extends Abstract.Pattern> myPatterns;

  public PatternScope(Scope parent, List<? extends Abstract.Pattern> patterns) {
    super(parent);
    myPatterns = patterns;
  }

  private Referable find(List<? extends Abstract.Pattern> args, Predicate<Referable> pred) {
    Scope globalScope = null;
    for (int i = args.size() - 1; i >= 0; i--) {
      List<? extends Abstract.TypedReferable> asPatterns = args.get(i).getAsPatterns();
      for (int j = asPatterns.size() - 1; j >= 0; j--) {
        Referable ref = asPatterns.get(j).getReferable();
        if (ref != null && pred.test(ref)) {
          return ref;
        }
      }

      List<? extends Abstract.Pattern> argArgs = args.get(i).getArguments();
      Referable ref = find(argArgs, pred);
      if (ref != null) {
        return ref;
      }
      if (argArgs.isEmpty()) {
        ref = args.get(i).getHeadReference();
        if (ref != null && !(ref instanceof GlobalReferable)) {
          if (globalScope == null) {
            globalScope = parent.getGlobalSubscope();
          }
          Referable resolved = globalScope.resolveName(ref.textRepresentation());
          if ((resolved == null || !(resolved instanceof GlobalReferable && ((GlobalReferable) resolved).getKind().isConstructor())) && pred.test(ref)) {
            return ref;
          }
        }
      }
    }
    return null;
  }

  @Override
  public Referable find(Predicate<Referable> pred) {
    Referable ref = find(myPatterns, pred);
    return ref != null ? ref : parent.find(pred);
  }

  @Nullable
  @Override
  public Referable resolveName(String name) {
    Referable ref = find(myPatterns, ref2 -> ref2.textRepresentation().equals(name));
    return ref != null ? ref : parent.resolveName(name);
  }
}
