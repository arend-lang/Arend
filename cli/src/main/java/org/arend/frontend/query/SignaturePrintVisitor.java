package org.arend.frontend.query;

import org.arend.ext.reference.Precedence;
import org.arend.term.concrete.Concrete;
import org.arend.term.prettyprint.PrettyPrintVisitor;
import org.jetbrains.annotations.Nullable;

/**
 * Renders a concrete definition's signature for the symbol index.
 * Leaf definitions (functions, lemmas, instances, individual constructors and
 * fields, metas) collapse to a single-line header: keyword + name + parameters
 * + result type. A container -- {@code \class}/{@code \record}/{@code \data} --
 * instead renders its full body (the directly declared fields / constructors),
 * multi-line, via {@link SignatureOnlyVisitor}: the standard pretty-printer with
 * member function/lemma bodies stripped, so {@code -ss} shows a record/class/data's
 * declared contents as its signature.
 */
public final class SignaturePrintVisitor {
  private SignaturePrintVisitor() {}

  public static String render(Concrete.GeneralDefinition def) {
    if (def instanceof Concrete.ClassDefinition || def instanceof Concrete.DataDefinition) {
      return renderContainer((Concrete.ResolvableDefinition) def);
    }
    StringBuilder sb = new StringBuilder();
      switch (def) {
          case Concrete.BaseFunctionDefinition baseFunctionDefinition -> renderFunction(sb, baseFunctionDefinition);
          case Concrete.MetaDefinition metaDefinition -> renderMeta(sb, metaDefinition);
          case Concrete.Constructor cons -> renderConstructor(sb, cons);
          case Concrete.ClassField field -> renderClassField(sb, field);
          default -> sb.append(def.getClass().getSimpleName());
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

  /**
   * Full render of a {@code \class}/{@code \record}/{@code \data} through the
   * pretty-printer, keeping the declared fields / constructors but stripping
   * member function bodies (see {@link SignatureOnlyVisitor}). Multi-line;
   * trailing whitespace per line and blank edge lines are trimmed.
   */
  private static String renderContainer(Concrete.ResolvableDefinition def) {
    StringBuilder sb = new StringBuilder();
    try {
      def.accept(new SignatureOnlyVisitor(sb, 0, true), null);
    } catch (RuntimeException e) {
      return "";
    }
    return trimLines(sb.toString());
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

  /** Right-trims each line and drops blank leading/trailing lines. */
  private static String trimLines(String s) {
    String[] lines = s.split("\n", -1);
    int first = 0, last = lines.length - 1;
    while (first <= last && lines[first].isBlank()) first++;
    while (last >= first && lines[last].isBlank()) last--;
    StringBuilder out = new StringBuilder(s.length());
    for (int i = first; i <= last; i++) {
      String line = lines[i];
      int end = line.length();
      while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) end--;
      out.append(line, 0, end);
      if (i < last) out.append('\n');
    }
    return out.toString();
  }

  /**
   * The pretty-printer with function/lemma/instance bodies suppressed. For
   * {@code \class}/{@code \record} and {@code \data} the directly declared
   * fields / constructors are printed by other hooks and survive, so the result
   * is the container's full signature without any proof terms. {@link #copy} is
   * overridden so the suppression propagates into nested sub-prints.
   */
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
