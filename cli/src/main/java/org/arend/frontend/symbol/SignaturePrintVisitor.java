package org.arend.frontend.symbol;

import org.arend.ext.reference.Precedence;
import org.arend.term.concrete.Concrete;
import org.arend.term.prettyprint.PrettyPrintVisitor;
import org.jetbrains.annotations.Nullable;

/**
 * Renders the header of a concrete definition on a single line for the symbol index.
 */
public final class SignaturePrintVisitor {
  private SignaturePrintVisitor() {}

  public static String render(Concrete.GeneralDefinition def) {
    StringBuilder sb = new StringBuilder();
    if (def instanceof Concrete.BaseFunctionDefinition fdef) {
      renderFunction(sb, fdef);
    } else if (def instanceof Concrete.DataDefinition ddef) {
      renderData(sb, ddef);
    } else if (def instanceof Concrete.ClassDefinition cdef) {
      renderClass(sb, cdef);
    } else if (def instanceof Concrete.MetaDefinition mdef) {
      renderMeta(sb, mdef);
    } else if (def instanceof Concrete.Constructor cons) {
      renderConstructor(sb, cons);
    } else if (def instanceof Concrete.ClassField field) {
      renderClassField(sb, field);
    } else {
      sb.append(def == null ? "<unknown>" : def.getClass().getSimpleName());
    }
    return collapse(sb.toString());
  }

  private static void renderFunction(StringBuilder sb, Concrete.BaseFunctionDefinition def) {
    sb.append(switch (def.getKind()) {
      case FUNC -> "\\func ";
      case SFUNC -> "\\sfunc ";
      case FUNC_COCLAUSE -> "| ";
      case CLASS_COCLAUSE -> "\\default ";
      case TYPE -> "\\type ";
      case LEMMA -> "\\lemma ";
      case AXIOM -> "\\axiom ";
      case LEVEL -> "\\use \\level ";
      case COERCE -> "\\use \\coerce ";
      case INSTANCE -> "\\instance ";
      case CONS -> "\\cons ";
    });
    sb.append(def.getData().textRepresentation());
    appendParams(sb, def.getParameters());
    appendResultType(sb, def.getResultType(), def.getResultTypeLevel());
  }

  private static void renderData(StringBuilder sb, Concrete.DataDefinition def) {
    sb.append("\\data ").append(def.getData().textRepresentation());
    appendTypeParams(sb, def.getParameters());
    Concrete.Expression universe = def.getUniverse();
    if (universe != null) {
      sb.append(" : ");
      appendExpr(sb, universe);
    }
  }

  private static void renderClass(StringBuilder sb, Concrete.ClassDefinition def) {
    sb.append(def.isRecord() ? "\\record " : "\\class ").append(def.getData().textRepresentation());
    if (!def.getSuperClasses().isEmpty()) {
      sb.append(" \\extends ");
      boolean first = true;
      for (Concrete.ReferenceExpression sup : def.getSuperClasses()) {
        if (!first) sb.append(", ");
        first = false;
        sb.append(sup.getReferent().textRepresentation());
      }
    }
  }

  private static void renderMeta(StringBuilder sb, Concrete.MetaDefinition def) {
    sb.append("\\meta ").append(def.getData().textRepresentation());
    appendParams(sb, def.getParameters());
  }

  private static void renderConstructor(StringBuilder sb, Concrete.Constructor cons) {
    sb.append("| ").append(cons.getData().textRepresentation());
    appendTypeParams(sb, cons.getParameters());
    Concrete.Expression resultType = cons.getResultType();
    if (resultType != null) {
      sb.append(" : ");
      appendExpr(sb, resultType);
    }
  }

  private static void renderClassField(StringBuilder sb, Concrete.ClassField field) {
    sb.append(switch (field.getKind()) {
      case FIELD -> "\\field ";
      case PROPERTY -> "\\property ";
      default -> "| ";
    });
    sb.append(field.getData().textRepresentation());
    appendTypeParams(sb, field.getParameters());
    sb.append(" : ");
    appendExpr(sb, field.getResultType());
  }

  private static void appendParams(StringBuilder sb, java.util.List<? extends Concrete.Parameter> params) {
    for (Concrete.Parameter p : params) {
      sb.append(' ');
      try {
        StringBuilder local = new StringBuilder();
        new PrettyPrintVisitor(local, 0).prettyPrintParameter(p);
        sb.append(local);
      } catch (RuntimeException e) {
        sb.append("...");
      }
    }
  }

  private static void appendTypeParams(StringBuilder sb, java.util.List<? extends Concrete.TypeParameter> params) {
    appendParams(sb, params);
  }

  private static void appendResultType(StringBuilder sb, @Nullable Concrete.Expression type, @Nullable Concrete.Expression typeLevel) {
    if (type == null) return;
    sb.append(" : ");
    if (typeLevel != null) {
      sb.append("\\level ");
      appendExpr(sb, type);
      sb.append(' ');
      appendExpr(sb, typeLevel);
    } else {
      appendExpr(sb, type);
    }
  }

  private static void appendExpr(StringBuilder sb, Concrete.Expression expr) {
    try {
      StringBuilder local = new StringBuilder();
      new PrettyPrintVisitor(local, 0).printExpr(expr, new Precedence(Concrete.Expression.PREC));
      sb.append(local);
    } catch (RuntimeException e) {
      sb.append("...");
    }
  }

  private static String collapse(String s) {
    StringBuilder out = new StringBuilder(s.length());
    boolean prevSpace = true;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isWhitespace(c)) {
        if (!prevSpace) {
          out.append(' ');
          prevSpace = true;
        }
      } else {
        out.append(c);
        prevSpace = false;
      }
    }
    int end = out.length();
    while (end > 0 && out.charAt(end - 1) == ' ') end--;
    return out.substring(0, end);
  }
}
