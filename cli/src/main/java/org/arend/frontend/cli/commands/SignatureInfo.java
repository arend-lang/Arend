package org.arend.frontend.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.arend.core.context.param.DependentLink;
import org.arend.core.definition.*;
import org.arend.core.expr.Expression;
import org.arend.core.sort.Sort;
import org.arend.core.expr.PiExpression;
import org.arend.ext.module.FullName;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.util.Pair;
import org.arend.frontend.cli.CommandContext;
import org.arend.module.error.ModuleNotFoundError;
import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.scope.Scope;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.DefinitionData;

import java.util.*;

/**
 * {@code -si MODULE:DEF NAME} — return structured signature info for NAME
 * resolved in the scope of MODULE:DEF, classifying each parameter as
 * explicit/implicit and propositional/non-propositional.
 *
 * Output JSON:
 * <pre>
 * {
 *   "name": "pmap",
 *   "params": [
 *     {"name": "A", "type": "\\Type", "explicit": false, "propositional": false},
 *     {"name": "f", "type": "A -> B", "explicit": true, "propositional": false},
 *     {"name": "p", "type": "a = a'", "explicit": true, "propositional": true}
 *   ],
 *   "resultType": "f a = f a'"
 * }
 * </pre>
 *
 * A parameter is classified as propositional if:
 * <ul>
 *   <li>Its type sorts into {@code \Prop}, OR</li>
 *   <li>Its type is an equality/path type ({@code a = b})</li>
 * </ul>
 */
public final class SignatureInfo {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private SignatureInfo() {}

  public static boolean run(CommandContext ctx, String[] args) {
    if (args == null || args.length < 2) {
      System.err.println("[ERROR] -si requires: MODULE:DEF NAME");
      return false;
    }

    String spec = args[0];
    String name = args[1];

    Pair<ModulePath, LongName> parsed = ctx.parseFullName(spec);
    if (parsed == null) return false;

    ModuleLocation module = ctx.server.findModule(parsed.proj1, null, false, false);
    if (module == null) {
      ctx.systemErrErrorReporter.report(new ModuleNotFoundError(parsed.proj1));
      return false;
    }

    ctx.server.getCheckerFor(Collections.singletonList(module))
        .resolveAll(ctx.cancellation, ProgressReporter.empty());

    if (parsed.proj2 != null) {
      ctx.server.getCheckerFor(Collections.singletonList(module))
          .typecheck(Collections.singletonList(new FullName(module, parsed.proj2)),
              ctx.systemErrErrorReporter, ctx.cancellation, ProgressReporter.empty());
    } else {
      ctx.server.getCheckerFor(Collections.singletonList(module))
          .typecheck(ctx.cancellation, ProgressReporter.empty());
    }

    Scope scope = null;
    for (DefinitionData data : ctx.server.getResolvedDefinitions(module)) {
      if (parsed.proj2 == null || data.definition().getData().getRefLongName().equals(parsed.proj2)) {
        scope = ctx.server.getReferableScope(data.definition().getData());
        break;
      }
    }

    if (scope == null) {
      System.err.println("[ERROR] Could not find scope for " + spec);
      return false;
    }

    Referable ref = resolveQualifiedName(scope, name);
    if (ref == null) {
      System.err.println("[ERROR] Name '" + name + "' not found in scope of " + spec);
      return false;
    }

    if (!(ref instanceof TCDefReferable tcRef)) {
      System.err.println("[ERROR] '" + name + "' is not a definition");
      return false;
    }

    Definition def = tcRef.getTypechecked();
    if (def == null) {
      ModuleLocation targetModule = tcRef.getLocation();
      if (targetModule != null) {
        ctx.server.getCheckerFor(Collections.singletonList(targetModule))
            .typecheck(ctx.cancellation, ProgressReporter.empty());
        def = tcRef.getTypechecked();
      }
    }
    if (def == null) {
      System.err.println("[ERROR] '" + name + "' is not typechecked");
      return false;
    }

    try {
      Map<String, Object> result = buildSignatureInfo(name, def);
      System.out.println(MAPPER.writeValueAsString(result));
      return true;
    } catch (Exception e) {
      System.err.println("[ERROR] Failed to serialize signature: " + e.getMessage());
      return false;
    }
  }

  private static Map<String, Object> buildSignatureInfo(String name, Definition def) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("name", name);

    List<Map<String, Object>> params = new ArrayList<>();
    Expression resultType;

    if (def instanceof ClassField field) {
      // ClassField stores params in nested PiExpressions — decompose the full chain
      Expression expr = field.getType();
      while (expr instanceof PiExpression pi) {
        for (DependentLink link = pi.getParameters(); link.hasNext(); link = link.getNext()) {
          params.add(buildParamInfo(link));
        }
        expr = pi.getCodomain();
      }
      resultType = expr;
    } else {
      DependentLink link = getParameters(def);
      if (link != null) {
        for (; link.hasNext(); link = link.getNext()) {
          params.add(buildParamInfo(link));
        }
      }
      resultType = getResultType(def);
    }

    result.put("params", params);
    result.put("resultType", resultType != null ? resultType.toString() : null);

    if (def instanceof DataDefinition dataDef) {
      List<Map<String, Object>> constructors = new ArrayList<>();
      for (Constructor constr : dataDef.getConstructors()) {
        Map<String, Object> constrInfo = new LinkedHashMap<>();
        constrInfo.put("name", constr.getName());

        List<Map<String, Object>> constrParams = new ArrayList<>();
        DependentLink cLink = constr.getParameters();
        if (cLink != null) {
          for (; cLink.hasNext(); cLink = cLink.getNext()) {
            Map<String, Object> cp = new LinkedHashMap<>();
            cp.put("name", cLink.getName() != null ? cLink.getName() : "_");
            Expression cType = cLink.getType();
            cp.put("type", cType != null ? cType.toString() : "?");
            cp.put("explicit", cLink.isExplicit());
            constrParams.add(cp);
          }
        }
        constrInfo.put("params", constrParams);
        constructors.add(constrInfo);
      }
      result.put("constructors", constructors);
    }

    return result;
  }

  private static Map<String, Object> buildParamInfo(DependentLink link) {
    Map<String, Object> paramInfo = new LinkedHashMap<>();
    paramInfo.put("name", link.getName() != null ? link.getName() : "_");

    Expression typeExpr = link.getType();
    paramInfo.put("type", typeExpr != null ? typeExpr.toString() : "?");
    paramInfo.put("explicit", link.isExplicit());

    boolean propositional = false;
    if (typeExpr != null) {
      // Expression.isPropType() was removed upstream; this is its former body.
      Sort typeSort = typeExpr.getSortOfType();
      if (typeSort != null && typeSort.isProp()) {
        propositional = true;
      }
      if (!propositional) {
        try {
          propositional = typeExpr.toEquality() != null;
        } catch (Exception ignored) {}
      }
    }
    paramInfo.put("propositional", propositional);
    return paramInfo;
  }

  private static DependentLink getParameters(Definition def) {
    if (def instanceof FunctionDefinition funcDef) {
      return funcDef.getParameters();
    }
    if (def instanceof Constructor constr) {
      return constr.getParameters();
    }
    if (def instanceof DataDefinition dataDef) {
      return dataDef.getParameters();
    }
    if (def instanceof ClassField field) {
      return field.getParameters();
    }
    return null;
  }

  private static Expression getResultType(Definition def) {
    if (def instanceof FunctionDefinition funcDef) {
      return funcDef.getResultType();
    }
    if (def instanceof ClassField field) {
      return field.getResultType();
    }
    return null;
  }

  private static Referable resolveQualifiedName(Scope scope, String name) {
    String[] parts = name.split("\\.");
    Scope current = scope;
    for (int i = 0; i < parts.length - 1; i++) {
      current = current.resolveNamespace(parts[i]);
      if (current == null) return null;
    }
    return current.resolveName(parts[parts.length - 1]);
  }
}
