package org.arend.frontend.cli.commands;

import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.util.Pair;
import org.arend.frontend.cli.CommandContext;
import org.arend.module.error.ModuleNotFoundError;
import org.arend.naming.reference.TCDefReferable;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.term.prettyprint.PrettyPrintVisitor;

public final class Signature {

  private Signature() {}

  public static boolean run(CommandContext ctx, String[] args) {
    if (args == null || args.length == 0) {
      System.err.println("[ERROR] -sg requires MODULE:DEF argument");
      return false;
    }

    String spec = args[0];
    Pair<ModulePath, LongName> parsed = ctx.parseFullName(spec);
    if (parsed == null) return false;
    if (parsed.proj2 == null) {
      System.err.println("[ERROR] -sg requires MODULE:DEF (definition name required)");
      return false;
    }

    ModuleLocation module = ctx.server.findModule(parsed.proj1, null, false, false);
    if (module == null) {
      ctx.systemErrErrorReporter.report(new ModuleNotFoundError(parsed.proj1));
      return false;
    }

    ConcreteGroup group = ctx.server.getRawGroup(module);
    if (group == null) {
      System.err.println("[ERROR] Could not load module " + parsed.proj1);
      return false;
    }

    Concrete.ResolvableDefinition def = findDefinition(group, parsed.proj2);
    if (def == null) {
      System.err.println("[ERROR] Definition " + parsed.proj2 + " not found in " + parsed.proj1);
      return false;
    }

    if (!(def instanceof Concrete.Definition concreteDef)) {
      System.err.println("[ERROR] " + parsed.proj2 + " is not a printable definition");
      return false;
    }

    StringBuilder sb = new StringBuilder();
    SignatureOnlyVisitor visitor = new SignatureOnlyVisitor(sb, 0, true);
    concreteDef.accept(visitor, null);
    System.out.println(sb.toString().strip());
    return true;
  }

  private static Concrete.ResolvableDefinition findDefinition(ConcreteGroup group, LongName targetName) {
    if (group.referable() instanceof TCDefReferable tcRef) {
      if (tcRef.getRefLongName() != null && tcRef.getRefLongName().equals(targetName)) {
        return group.definition();
      }
    }
    for (ConcreteStatement stmt : group.statements()) {
      if (stmt.group() != null) {
        Concrete.ResolvableDefinition found = findDefinition(stmt.group(), targetName);
        if (found != null) return found;
      }
    }
    for (ConcreteGroup dyn : group.dynamicGroups()) {
      Concrete.ResolvableDefinition found = findDefinition(dyn, targetName);
      if (found != null) return found;
    }
    return null;
  }

  private static final class SignatureOnlyVisitor extends PrettyPrintVisitor {
    SignatureOnlyVisitor(StringBuilder builder, int indent, boolean doIndent) {
      super(builder, indent, doIndent);
    }

    @Override
    protected PrettyPrintVisitor copy(StringBuilder builder, int indent, boolean doIndent) {
      return new SignatureOnlyVisitor(builder, indent, doIndent);
    }

    @Override
    public void prettyPrintBody(Concrete.FunctionBody body, boolean isFunction) {
    }
  }
}
