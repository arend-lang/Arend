package org.arend.frontend.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.arend.core.context.binding.Binding;
import org.arend.core.expr.Expression;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.FullName;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.util.Pair;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.parser.BuildVisitor;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.module.error.ModuleNotFoundError;
import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.resolving.typing.TypingInfo;
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor;
import org.arend.naming.scope.Scope;
import org.arend.naming.scope.local.ListScope;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.ArendServerImpl;
import org.arend.server.impl.DefinitionData;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.error.local.GoalError;

import java.lang.reflect.Field;
import java.util.*;

/**
 * {@code -as MODULE:DEF FULL_BODY} — substitute the full body expression into
 * the definition, typecheck, and return remaining goals.
 */
public final class ApplyStep {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ApplyStep() {}

  public static boolean run(CommandContext ctx, String[] args) {
    if (args == null || args.length < 2) {
      System.err.println("[ERROR] -as requires: MODULE:DEF FULL_BODY");
      return false;
    }

    String spec = args[0];
    String bodyText = args[1];

    Pair<ModulePath, LongName> parsed = ctx.parseFullName(spec);
    if (parsed == null) return false;

    ModuleLocation module = ctx.server.findModule(parsed.proj1, null, false, false);
    if (module == null) {
      ctx.systemErrErrorReporter.report(new ModuleNotFoundError(parsed.proj1));
      return false;
    }

    // Reset typechecked state
    ConcreteGroup group = ctx.server.getRawGroup(module);
    if (group != null && parsed.proj2 != null) {
      resetDefinition(group, parsed.proj2);
    }

    // Resolve so we can find the definition
    ctx.server.getCheckerFor(Collections.singletonList(module))
        .resolveAll(ctx.cancellation, ProgressReporter.empty());

    boolean isElimBody = bodyText.stripLeading().startsWith("\\elim");

    List<String> errors = new ArrayList<>();
    List<Map<String, Object>> newGoals = new ArrayList<>();
    boolean success = false;

    try {
      List<String> parseErrors = new ArrayList<>();
      org.arend.ext.error.ErrorReporter parseCapture = error -> {
        if (error instanceof org.arend.frontend.parser.ParserError) {
          parseErrors.add(error.toString());
        }
      };

      Concrete.Expression bodyExpr = null;
      Concrete.ElimFunctionBody elimBody = null;

      if (isElimBody) {
        var parser = CommonCliRepl.createParser(bodyText, module, parseCapture);
        var buildVisitor = new BuildVisitor(module, parseCapture);
        var bodyCtx = parser.functionBody();

        if (!parseErrors.isEmpty()) {
          errors.addAll(parseErrors);
        } else if (parser.getCurrentToken().getType() != org.antlr.v4.runtime.Token.EOF) {
          int pos = parser.getCurrentToken().getStartIndex();
          String remaining = bodyText.substring(pos);
          // Find the clause that failed to parse and report a more specific error
          int pipeIdx = remaining.indexOf("=>");
          if (pipeIdx >= 0 && remaining.startsWith("|")) {
            String clauseExpr = remaining.substring(pipeIdx + 2).trim();
            errors.add("Parse error in clause expression: '" + clauseExpr + "' (check syntax — \\let uses =>, not =)");
          } else {
            errors.add("Parse error: unexpected input starting at '" + remaining + "'");
          }
        } else if (bodyCtx instanceof org.arend.frontend.parser.ArendParser.WithElimContext elimCtx) {
          List<Concrete.ReferenceExpression> elimRefs = buildVisitor.visitElim(elimCtx.elim());
          var clausesCtx = elimCtx.clauses();
          List<Concrete.FunctionClause> clauses = new ArrayList<>();
          var clauseList = clausesCtx instanceof org.arend.frontend.parser.ArendParser.ClausesWithBracesContext cb
              ? cb.clause()
              : ((org.arend.frontend.parser.ArendParser.ClausesWithoutBracesContext) clausesCtx).clause();
          for (var clauseCtx : clauseList) {
            clauses.add(buildVisitor.visitClause(clauseCtx));
          }
          elimBody = new Concrete.ElimFunctionBody(null, elimRefs, clauses);
        } else {
          errors.add("Expected \\elim function body");
        }
      } else {
        var parser = CommonCliRepl.createParser(bodyText, module, parseCapture);
        var buildVisitor = new BuildVisitor(module, parseCapture);
        bodyExpr = buildVisitor.visitExpr(parser.expr());

        if (!parseErrors.isEmpty()) {
          errors.addAll(parseErrors);
          bodyExpr = null;
        } else if (parser.getCurrentToken().getType() != org.antlr.v4.runtime.Token.EOF) {
          String remaining = bodyText.substring(parser.getCurrentToken().getStartIndex());
          errors.add("Parse error: unexpected input starting at '" + remaining + "'");
          bodyExpr = null;
        }
      }

      if (bodyExpr == null && elimBody == null) {
        if (errors.isEmpty()) errors.add("Failed to parse body");
      } else {
        Concrete.GeneralDefinition targetDef = null;
        Scope scope = null;
        for (DefinitionData data : ctx.server.getResolvedDefinitions(module)) {
          if (parsed.proj2 == null || data.definition().getData().getRefLongName().equals(parsed.proj2)) {
            targetDef = data.definition();
            scope = ctx.server.getReferableScope(data.definition().getData());
            break;
          }
        }

        if (scope != null && targetDef instanceof Concrete.BaseFunctionDefinition funcDef0) {
          List<ArendRef> params = new ArrayList<>();
          for (Concrete.Parameter param : funcDef0.getParameters()) {
            for (Referable ref : param.getReferableList()) {
              if (ref != null) params.add(ref);
            }
          }
          if (!params.isEmpty()) {
            scope = new ListScope(scope, params);
          }

          TypingInfo typingInfo = ctx.server instanceof ArendServerImpl si
              ? si.getTypingInfo() : TypingInfo.EMPTY;

          List<String> resolveErrors = new ArrayList<>();
          org.arend.ext.error.ErrorReporter resolveCapture = error -> {
            if (error.level == GeneralError.Level.ERROR) {
              resolveErrors.add(error.toString());
            }
          };

          if (bodyExpr != null) {
            bodyExpr = org.arend.typechecking.visitor.SyntacticDesugarVisitor.desugar(
                bodyExpr.accept(
                    new ExpressionResolveNameVisitor(scope, new ArrayList<>(), typingInfo,
                        resolveCapture, null, null),
                    null),
                resolveCapture);
          } else {
            var exprVisitor = new ExpressionResolveNameVisitor(scope, new ArrayList<>(), typingInfo,
                resolveCapture, null, null);
            for (int i = 0; i < elimBody.getEliminatedReferences().size(); i++) {
              Concrete.Expression resolved = elimBody.getEliminatedReferences().get(i).accept(exprVisitor, null);
              if (resolved instanceof Concrete.ReferenceExpression resolvedRef) {
                elimBody.getEliminatedReferences().set(i, resolvedRef);
              }
            }
            exprVisitor.visitClauses(elimBody.getClauses(), null);
            for (Concrete.FunctionClause clause : elimBody.getClauses()) {
              if (clause.expression != null) {
                clause.expression = org.arend.typechecking.visitor.SyntacticDesugarVisitor.desugar(
                    clause.expression, resolveCapture);
              }
            }
          }

          if (!resolveErrors.isEmpty()) {
            errors.addAll(resolveErrors);
          }
        }

        if (targetDef instanceof Concrete.FunctionDefinition funcDef) {
          Concrete.FunctionBody origBody = funcDef.getBody();
          // TermFunctionBody is mutated in place below, so snapshot the term itself.
          // Restoring origBody alone would be a no-op and would leak the attempted
          // body into subsequent commands on this warm (daemon) context.
          Concrete.Expression origTerm =
              origBody instanceof Concrete.TermFunctionBody tb ? tb.getTerm() : null;

          boolean substituted = false;
          if (bodyExpr != null) {
            if (origBody instanceof Concrete.TermFunctionBody termBody) {
              termBody.setTerm(bodyExpr);
              substituted = true;
            } else {
              errors.add("Unsupported function body type for expression");
            }
          } else {
            setBody(funcDef, elimBody);
            substituted = true;
          }

          if (errors.isEmpty()) {
            funcDef.getData().setTypechecked(null);

            List<GoalError> newGoalErrors = new ArrayList<>();
            List<GeneralError> tcErrors = new ArrayList<>();
            org.arend.ext.error.ErrorReporter retypeCapture = error -> {
              if (error instanceof GoalError ge) {
                newGoalErrors.add(ge);
              } else if (error.level == GeneralError.Level.ERROR) {
                tcErrors.add(error);
              }
            };
            ctx.server.addErrorReporter(retypeCapture);
            try {
              ctx.server.getCheckerFor(Collections.singletonList(module))
                  .typecheck(Collections.singletonList(new FullName(module, parsed.proj2)),
                      retypeCapture, ctx.cancellation, ProgressReporter.empty());
            } finally {
              // The daemon reuses this server across commands; don't leak the capture.
              ctx.server.removeErrorReporter(retypeCapture);
            }

            if (tcErrors.isEmpty()) {
              success = true;

              ArendRef targetRef = funcDef.getData();
              newGoalErrors.removeIf(ge -> ge.definition != null && ge.definition != targetRef);

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
                    if (typeExpr == null) typeExpr = entry.getValue().getType();
                    String type = typeExpr != null ? typeExpr.toString() : "{?}";
                    context.add(Map.of("name", name, "type", type));
                  }
                }
                goalMap.put("context", context);
                newGoals.add(goalMap);
              }
            } else {
              for (GeneralError e : tcErrors) {
                errors.add(e.toString());
              }
            }

            // Drop the typechecked state produced for the substituted body;
            // the next command will re-typecheck from Concrete.
            funcDef.getData().setTypechecked(null);
          }

          // Restore the original body whenever it was substituted — even when
          // resolution failed before typechecking — otherwise the attempted body
          // leaks into subsequent commands on this warm (daemon) context.
          if (substituted) {
            if (origBody instanceof Concrete.TermFunctionBody termBody && origTerm != null) {
              termBody.setTerm(origTerm);
            } else {
              setBody(funcDef, origBody);
            }
          }
        } else {
          errors.add("Target is not a function definition");
        }
      }
    } catch (Exception e) {
      errors.add("Exception: " + e.getMessage());
    }

    try {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("success", success);
      result.put("proof", bodyText);
      result.put("goals", newGoals);
      result.put("errors", errors);
      System.out.println(MAPPER.writeValueAsString(result));
      System.err.println(errors);
      return true;
    } catch (Exception e) {
      System.err.println("[ERROR] Failed to serialize result: " + e.getMessage());
      return false;
    }
  }

  private static void setBody(Concrete.FunctionDefinition funcDef, Concrete.FunctionBody body) {
    try {
      Field bodyField = Concrete.BaseFunctionDefinition.class.getDeclaredField("myBody");
      bodyField.setAccessible(true);
      bodyField.set(funcDef, body);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Failed to set function body", e);
    }
  }

  private static boolean resetDefinition(ConcreteGroup group, LongName targetName) {
    if (group.referable() instanceof TCDefReferable tcRef && tcRef.getKind().isTypecheckable()) {
      if (tcRef.getRefLongName().equals(targetName)) {
        tcRef.setTypechecked(null);
        return true;
      }
    }
    for (ConcreteStatement stmt : group.statements()) {
      if (stmt.group() != null && resetDefinition(stmt.group(), targetName)) return true;
    }
    for (ConcreteGroup dyn : group.dynamicGroups()) {
      if (resetDefinition(dyn, targetName)) return true;
    }
    return false;
  }
}
