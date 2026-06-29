package org.arend.frontend.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.arend.core.context.binding.Binding;
import org.arend.core.expr.Expression;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.FullName;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.util.Pair;
import org.arend.frontend.cli.CommandContext;
import org.arend.module.error.ModuleNotFoundError;
import org.arend.naming.reference.*;
import org.arend.naming.scope.Scope;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.DefinitionData;
import org.arend.typechecking.error.local.GoalError;

import java.util.*;

/**
 * {@code -gs MODULE:DEF GOAL_ID} — return available definitions in scope
 * and local bindings at a goal position as JSON.
 */
public final class GetScope {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private GetScope() {}

  public static boolean run(CommandContext ctx, String[] args) {
    if (args == null || args.length < 2) {
      System.err.println("[ERROR] -gs requires: MODULE:DEF GOAL_ID");
      return false;
    }

    String spec = args[0];
    String goalId = args[1];

    Pair<ModulePath, LongName> parsed = ctx.parseFullName(spec);
    if (parsed == null) return false;

    ModuleLocation module = ctx.server.findModule(parsed.proj1, null, false, false);
    if (module == null) {
      ctx.systemErrErrorReporter.report(new ModuleNotFoundError(parsed.proj1));
      return false;
    }

    // Typecheck to capture goal contexts
    List<GoalError> goalErrors = new ArrayList<>();
    org.arend.ext.error.ErrorReporter goalCapture = error -> {
      if (error instanceof GoalError ge) {
        goalErrors.add(ge);
      }
    };

    ctx.server.getCheckerFor(Collections.singletonList(module))
        .resolveAll(ctx.cancellation, ProgressReporter.empty());

    if (parsed.proj2 != null) {
      ctx.server.getCheckerFor(Collections.singletonList(module))
          .typecheck(Collections.singletonList(new FullName(module, parsed.proj2)),
              goalCapture, ctx.cancellation, ProgressReporter.empty());
    } else {
      ctx.server.getCheckerFor(Collections.singletonList(module))
          .typecheck(ctx.cancellation, ProgressReporter.empty());
    }

    int targetGoal;
    try {
      targetGoal = Integer.parseInt(goalId);
    } catch (NumberFormatException e) {
      System.err.println("[ERROR] Invalid goal ID: " + goalId);
      return false;
    }

    if (targetGoal < 0 || targetGoal >= goalErrors.size()) {
      System.err.println("[ERROR] Goal ID " + goalId + " out of range (found " + goalErrors.size() + " goals)");
      return false;
    }

    GoalError goal = goalErrors.get(targetGoal);

    // Get ambient scope
    List<Map<String, String>> scopeEntries = new ArrayList<>();
    Scope scope = null;

    // Try to get scope from the definition
    if (parsed.proj2 != null) {
      for (DefinitionData data : ctx.server.getResolvedDefinitions(module)) {
        if (data.definition().getData().getRefLongName().equals(parsed.proj2)) {
          scope = ctx.server.getReferableScope(data.definition().getData());
          break;
        }
      }
    }

    if (scope != null) {
      scope.find(ref -> {
        if (ref instanceof GlobalReferable gr) {
          String name = gr.getRefLongName() != null ? gr.getRefLongName().toString() : gr.getRefName();
          String kind = gr.getKind().name();
          scopeEntries.add(Map.of("name", name, "kind", kind));
        }
        return false;
      });
    }

    // Get local bindings from goal context
    List<Map<String, String>> locals = new ArrayList<>();
    if (goal.typecheckingContext != null) {
      for (Map.Entry<Referable, Binding> entry : goal.typecheckingContext.localContext().entrySet()) {
        if (entry.getValue().isHidden()) continue;
        String name = entry.getKey() != null ? entry.getKey().getRefName() : "_";
        Expression typeExpr = goal.bindingTypes.get(entry.getValue());
        if (typeExpr == null) typeExpr = entry.getValue().getTypeExpr();
        String type = typeExpr != null ? typeExpr.toString() : "{?}";
        locals.add(Map.of("name", name, "type", type));
      }
    }

    try {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("scope", scopeEntries);
      result.put("locals", locals);
      System.out.println(MAPPER.writeValueAsString(result));
      return true;
    } catch (Exception e) {
      System.err.println("[ERROR] Failed to serialize scope: " + e.getMessage());
      return false;
    }
  }
}
