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
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.module.error.ModuleNotFoundError;
import org.arend.naming.reference.Referable;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.DefinitionData;
import org.arend.term.concrete.Concrete;
import org.arend.term.prettyprint.PrettyPrintVisitor;
import org.arend.typechecking.error.local.GoalError;

import java.util.*;

/**
 * {@code -as MODULE:DEF GOAL_ID REPLACEMENT_EXPR} — substitute expression into
 * the goal position, re-typecheck, and return the new proof state.
 */
public final class ApplyStep {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ApplyStep() {}

  public static boolean run(CommandContext ctx, String[] args) {
    if (args == null || args.length < 3) {
      System.err.println("[ERROR] -as requires: MODULE:DEF GOAL_ID REPLACEMENT_EXPR");
      return false;
    }

    String spec = args[0];
    String goalId = args[1];
    String exprText = args[2];

    Pair<ModulePath, LongName> parsed = ctx.parseFullName(spec);
    if (parsed == null) return false;

    ModuleLocation module = ctx.server.findModule(parsed.proj1, null, false, false);
    if (module == null) {
      ctx.systemErrErrorReporter.report(new ModuleNotFoundError(parsed.proj1));
      return false;
    }

    // Phase 1: typecheck to find goals
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

    GoalError targetGoalError = goalErrors.get(targetGoal);

    // Phase 2: parse the replacement expression
    List<String> errors = new ArrayList<>();
    boolean success = false;
    String updatedProof = "";

    try {
      var parser = CommonCliRepl.createParser(exprText, module, ctx.systemErrErrorReporter);
      var buildVisitor = new org.arend.frontend.parser.BuildVisitor(module, ctx.systemErrErrorReporter);
      Concrete.Expression replacementExpr = buildVisitor.visitExpr(parser.expr());

      if (replacementExpr == null) {
        errors.add("Failed to parse replacement expression");
      } else {
        Concrete.GoalExpression goalExpr = targetGoalError.goalExpression;

        // Find the function definition containing this goal
        Concrete.GeneralDefinition targetDef = null;
        for (DefinitionData data : ctx.server.getResolvedDefinitions(module)) {
          if (parsed.proj2 == null || data.definition().getData().getRefLongName().equals(parsed.proj2)) {
            targetDef = data.definition();
            break;
          }
        }

        if (targetDef instanceof Concrete.FunctionDefinition funcDef) {
          Concrete.Expression body = funcDef.getBody().getTerm();
          if (body != null) {
            Concrete.Expression newBody = substituteGoal(body, goalExpr, replacementExpr);
            if (newBody != null) {
              StringBuilder sb = new StringBuilder();
              newBody.accept(new PrettyPrintVisitor(sb, 0),
                  new org.arend.ext.reference.Precedence(Concrete.Expression.PREC));
              updatedProof = sb.toString();
              success = true;
            } else {
              errors.add("Goal not found in function body");
            }
          } else {
            errors.add("Function has no body expression");
          }
        } else {
          errors.add("Target is not a function definition");
        }
      }
    } catch (Exception e) {
      errors.add("Exception: " + e.getMessage());
    }

    // Phase 3: if substitution succeeded, re-typecheck to find remaining goals
    List<Map<String, Object>> newGoals = new ArrayList<>();
    if (success) {
      List<GoalError> newGoalErrors = new ArrayList<>();
      org.arend.ext.error.ErrorReporter newCapture = error -> {
        if (error instanceof GoalError ge) {
          newGoalErrors.add(ge);
        }
      };

      ctx.server.getCheckerFor(Collections.singletonList(module))
          .resolveAll(ctx.cancellation, ProgressReporter.empty());
      if (parsed.proj2 != null) {
        ctx.server.getCheckerFor(Collections.singletonList(module))
            .typecheck(Collections.singletonList(new FullName(module, parsed.proj2)),
                newCapture, ctx.cancellation, ProgressReporter.empty());
      }

      int idx = 0;
      for (GoalError ge : newGoalErrors) {
        Map<String, Object> goalMap = new LinkedHashMap<>();
        goalMap.put("id", String.valueOf(idx++));
        goalMap.put("name", ge.goalName != null ? ge.goalName : "");
        goalMap.put("expectedType", ge.expectedType != null ? ge.expectedType.toString() : "");

        List<Map<String, String>> context = new ArrayList<>();
        if (ge.typecheckingContext != null) {
          for (Map.Entry<Referable, Binding> entry : ge.typecheckingContext.localContext().entrySet()) {
            if (entry.getValue().isHidden()) continue;
            String name = entry.getKey() != null ? entry.getKey().getRefName() : "_";
            Expression typeExpr = ge.bindingTypes.get(entry.getValue());
            if (typeExpr == null) typeExpr = entry.getValue().getTypeExpr();
            String type = typeExpr != null ? typeExpr.toString() : "{?}";
            context.add(Map.of("name", name, "type", type));
          }
        }
        goalMap.put("context", context);
        newGoals.add(goalMap);
      }
    }

    try {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("success", success);
      result.put("proof", updatedProof);
      result.put("goals", newGoals);
      result.put("errors", errors);
      System.out.println(MAPPER.writeValueAsString(result));
      return true;
    } catch (Exception e) {
      System.err.println("[ERROR] Failed to serialize result: " + e.getMessage());
      return false;
    }
  }

  private static Concrete.Expression substituteGoal(
      Concrete.Expression body,
      Concrete.GoalExpression targetGoal,
      Concrete.Expression replacement) {
    if (body == targetGoal) {
      return replacement;
    }
    return body.accept(new org.arend.term.concrete.BaseConcreteExpressionVisitor<Void>() {
      @Override
      public Concrete.Expression visitGoal(Concrete.GoalExpression expr, Void params) {
        if (expr == targetGoal) {
          return replacement;
        }
        return expr;
      }
    }, null);
  }
}
