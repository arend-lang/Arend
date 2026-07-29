package org.arend.frontend.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.arend.core.definition.DataDefinition;
import org.arend.core.expr.DefCallExpression;
import org.arend.core.expr.Expression;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.FullName;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.util.Pair;
import org.arend.frontend.cli.CommandContext;
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
import org.arend.typechecking.visitor.CheckTypeVisitor;
import org.arend.typechecking.visitor.SyntacticDesugarVisitor;

import java.util.*;

/**
 * {@code -te MODULE:DEF GOAL_ID EXPRESSION [PROOF_BODY]} — infer the type of EXPRESSION
 * in the context of the specified goal, and return it as JSON.
 *
 * <p>When the optional PROOF_BODY (the caller's current full proof body) is given,
 * goals are taken from that body instead of the on-disk one, so GOAL_ID and the
 * goal's local context (e.g. case-branch bindings) match the caller's proof state.
 * The on-disk body is restored before the command returns.
 *
 * Output:
 * <pre>
 * {"type": "Dec (x = y)"}
 * </pre>
 */
public final class TypeExpr {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private TypeExpr() {}

  public static boolean run(CommandContext ctx, String[] args) {
    if (args == null || args.length < 3) {
      System.err.println("[ERROR] -te requires: MODULE:DEF GOAL_ID EXPRESSION [PROOF_BODY]");
      return false;
    }

    String spec = args[0];
    String goalId = args[1];
    String exprText = args[2];
    String bodyText = args.length > 3 ? args[3] : null;

    Pair<ModulePath, LongName> parsed = ctx.parseFullName(spec);
    if (parsed == null) return false;

    ModuleLocation module = ctx.server.findModule(parsed.proj1, null, false, false);
    if (module == null) {
      ctx.systemErrErrorReporter.report(new ModuleNotFoundError(parsed.proj1));
      return false;
    }

    int targetGoal;
    try {
      targetGoal = Integer.parseInt(goalId);
    } catch (NumberFormatException e) {
      System.err.println("[ERROR] Invalid goal ID: " + goalId);
      return false;
    }

    Concrete.FunctionDefinition funcDef = null;
    ArendRef targetRef = null;
    for (DefinitionData data : ctx.server.getResolvedDefinitions(module)) {
      if (parsed.proj2 == null || data.definition().getData().getRefLongName().equals(parsed.proj2)) {
        targetRef = data.definition().getData();
        if (data.definition() instanceof Concrete.FunctionDefinition fd) funcDef = fd;
        break;
      }
    }

    // When the caller passes its current proof body, substitute it for the goal
    // lookup so goal indices and goal contexts (branch-local bindings) match the
    // caller's proof state rather than the on-disk body. Always restored below.
    BodySubstitutor subst = null;
    if (bodyText != null) {
      if (funcDef == null) {
        System.err.println("[ERROR] Target is not a function definition: " + spec);
        return false;
      }
      subst = BodySubstitutor.substitute(ctx, module, funcDef, bodyText);
      if (subst == null) return false;
    }

    // Typecheck to find goals. The captured GoalErrors (and their typechecking
    // contexts) stay valid after the body is restored, so restore right away.
    List<GoalError> goalErrors;
    try {
      goalErrors = FindGoals.findGoals(module, parsed.proj2, ctx);
    } finally {
      if (subst != null) subst.restore();
    }

    // Keep only the target definition's goals so indices match -as output.
    if (targetRef != null) {
      final ArendRef finalTargetRef = targetRef;
      goalErrors.removeIf(ge -> ge.definition != null && ge.definition != finalTargetRef);
    }

    if (targetGoal < 0 || targetGoal >= goalErrors.size()) {
      System.err.println("[ERROR] Goal ID " + goalId + " out of range (found " + goalErrors.size() + " goals)");
      return false;
    }

    GoalError goal = goalErrors.get(targetGoal);

    // Parse the expression
    try {
      List<String> parseErrors = new ArrayList<>();
      org.arend.ext.error.ErrorReporter parseCapture = error -> {
        if (error instanceof org.arend.frontend.parser.ParserError) {
          parseErrors.add(error.toString());
        }
      };
      var parser = CommonCliRepl.createParser(exprText, module, parseCapture);
      var buildVisitor = new org.arend.frontend.parser.BuildVisitor(module, parseCapture);
      Concrete.Expression concreteExpr = buildVisitor.visitExpr(parser.expr());

      if (!parseErrors.isEmpty() || concreteExpr == null) {
        System.err.println("[ERROR] Failed to parse: " + exprText);
        return false;
      }

      if (parser.getCurrentToken().getType() != org.antlr.v4.runtime.Token.EOF) {
        System.err.println("[ERROR] Unexpected input after expression");
        return false;
      }

      // Resolve names in the goal's scope
      Scope scope = null;
      for (DefinitionData data : ctx.server.getResolvedDefinitions(module)) {
        if (parsed.proj2 == null || data.definition().getData().getRefLongName().equals(parsed.proj2)) {
          scope = ctx.server.getReferableScope(data.definition().getData());
          break;
        }
      }

      if (scope != null) {
        if (goal.typecheckingContext != null) {
          List<ArendRef> locals = new ArrayList<>();
          for (Referable ref : goal.typecheckingContext.localContext().keySet()) {
            if (ref != null) locals.add(ref);
          }
          if (!locals.isEmpty()) {
            scope = new ListScope(scope, locals);
          }
        }
        TypingInfo typingInfo = ctx.server instanceof ArendServerImpl si
            ? si.getTypingInfo() : TypingInfo.EMPTY;
        concreteExpr = SyntacticDesugarVisitor.desugar(
            concreteExpr.accept(
                new ExpressionResolveNameVisitor(scope, new ArrayList<>(), typingInfo,
                    ctx.systemErrErrorReporter, null, null),
                null),
            ctx.systemErrErrorReporter);
      }

      // Typecheck to infer the type
      if (goal.typecheckingContext == null) {
        System.err.println("[ERROR] No typechecking context for goal");
        return false;
      }

      List<GeneralError> tcErrors = new ArrayList<>();
      CheckTypeVisitor checker = CheckTypeVisitor.loadTypecheckingContext(
          goal.typecheckingContext, error -> {
        if (error.level == GeneralError.Level.ERROR) tcErrors.add(error);
      });

      var result = checker.checkExpr(concreteExpr, null);
      if (result == null || !tcErrors.isEmpty()) {
        String errMsg = tcErrors.isEmpty() ? "Type inference failed" : tcErrors.get(0).toString();
        System.err.println(errMsg);
        return false;
      }

      Expression type = result.type;
      Map<String, Object> output = new LinkedHashMap<>();
      output.put("type", type != null ? type.toString() : null);
      if (type instanceof DefCallExpression defCall) {
        if (defCall.getDefinition() instanceof DataDefinition dataDef) {
          Map<String, Object> dataTypeMap = new LinkedHashMap<>();
          var modulePath = dataDef.getRef().getModulePath();
          if (modulePath != null) {
            dataTypeMap.put("module", modulePath.toString());
            dataTypeMap.put("typename", dataDef.getName());
            output.put("datatype", dataTypeMap);
          }
        }
      }
      System.out.println(MAPPER.writeValueAsString(output));
      return true;

    } catch (Exception e) {
      System.err.println("[ERROR] " + e.getMessage());
      return false;
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
