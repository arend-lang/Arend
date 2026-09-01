package org.arend.frontend.query;

import org.arend.ext.concrete.definition.FunctionKind;
import org.arend.ext.module.ModulePath;
import org.arend.ext.reference.Precedence;
import org.arend.naming.reference.FieldReferable;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.Referable;
import org.arend.term.concrete.BaseConcreteExpressionVisitor;
import org.arend.term.concrete.Concrete;
import org.arend.term.prettyprint.PrettyPrintVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A definition's signature as text, for {@code -ss} and {@code -ps}.
 *
 * <p>A leaf definition is one line: keyword, name, parameters, result type. A
 * {@code \class}/{@code \record}/{@code \data} is a header plus one line per declared member --
 * fields and {@code \override}s for a class, constructors for a data type -- and, given
 * {@link ClassDefaults}, one line per field the class defaults. No proof terms anywhere: field
 * implementations and function bodies are dropped.
 *
 * <p>Names print bare, without precedence or {@code \alias} decoration.
 */
public final class SignaturePrintVisitor {
  private SignaturePrintVisitor() {}

  public static String render(Concrete.GeneralDefinition def) {
    return render(def, null);
  }

  /**
   * As {@link #render(Concrete.GeneralDefinition)}, but a class also lists {@code defaultedFields}
   * -- pass null or an empty list to omit them.
   */
  public static String render(Concrete.GeneralDefinition def,
                              @Nullable List<ClassDefaults.DefaultField> defaultedFields) {
    if (def instanceof Concrete.ClassDefinition cls) return renderClass(cls, defaultedFields);
    if (def instanceof Concrete.DataDefinition data) return renderData(data);
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
   * A {@code \class}/{@code \record}: header (name, parameter fields, {@code \extends}), then its
   * declared fields and {@code \override}s, then its defaults. Implemented fields are dropped.
   */
  private static String renderClass(Concrete.ClassDefinition def,
                                    @Nullable List<ClassDefaults.DefaultField> defaultedFields) {
    StringBuilder header = new StringBuilder(def.isRecord() ? "\\record " : "\\class ");
    header.append(def.getData().textRepresentation());

    // Parameter fields belong in the header, as `(x : T)` / `{x : T}`.
    for (Concrete.ClassElement element : def.getElements()) {
      if (element instanceof Concrete.ClassField field && field.getData().isParameterField()) {
        boolean explicit = field.getData().isExplicitField();
        header.append(explicit ? " (" : " {");
        if (field.isCoerce()) header.append("\\coerce ");
        header.append(field.getData().textRepresentation()).append(" : ");
        appendExpr(header, field.getResultType());
        header.append(explicit ? ')' : '}');
      }
    }

    List<Concrete.ReferenceExpression> supers = def.getSuperClasses();
    if (!supers.isEmpty()) {
      header.append(" \\extends ");
      for (int i = 0; i < supers.size(); i++) {
        if (i > 0) header.append(", ");
        appendExpr(header, supers.get(i));
      }
    }

    List<String> members = new ArrayList<>();
    for (Concrete.ClassElement element : def.getElements()) {
      StringBuilder line = new StringBuilder();
      if (element instanceof Concrete.ClassField field) {
        if (field.getData().isParameterField()) continue;   // already in the header
        renderClassField(line, field);
      } else if (element instanceof Concrete.OverriddenField overridden) {
        renderOverridden(line, overridden);
      } else {
        continue;   // an implementation, not a declaration
      }
      members.add(collapse(line.toString()));
    }

    if (defaultedFields != null) {
      for (ClassDefaults.DefaultField field : defaultedFields) members.add(renderDefault(field));
    }
    return withMembers(collapse(header.toString()), members, true);
  }

  /** One {@code \default} line: the field's signature, then the fields its implementation mentions. */
  private static String renderDefault(ClassDefaults.DefaultField field) {
    StringBuilder sb = new StringBuilder("\\default ").append(field.name());
    Concrete.ClassField declaration = field.declaration();
    if (declaration != null) {
      appendTypeParams(sb, declaration.getParameters());
      appendResultType(sb, declaration.getResultType(), declaration.getResultTypeLevel());
    }
    String line = collapse(sb.toString());
    List<String> mentioned = field.mentionedFields();
    if (mentioned.isEmpty()) return line;
    String shown = String.join(", ", mentioned.subList(0, Math.min(mentioned.size(), MAX_MENTIONED_FIELDS)))
        + (mentioned.size() > MAX_MENTIONED_FIELDS
            ? ", +" + (mentioned.size() - MAX_MENTIONED_FIELDS) + " more" : "");
    return line + "    -- uses " + shown;
  }

  /** How many field names a {@code -- uses} comment lists before summarising the rest. */
  private static final int MAX_MENTIONED_FIELDS = 12;

  /** A {@code \data}: the header plus one line per constructor. */
  private static String renderData(Concrete.DataDefinition def) {
    StringBuilder header = new StringBuilder();
    if (def.isTruncated()) header.append("\\truncated ");
    header.append("\\data ").append(def.getData().textRepresentation());
    appendTypeParams(header, def.getParameters());
    if (def.getUniverse() != null) {
      header.append(" : ");
      appendExpr(header, def.getUniverse());
    }

    List<String> constructors = new ArrayList<>();
    for (Concrete.ConstructorClause clause : def.getConstructorClauses()) {
      for (Concrete.Constructor cons : clause.getConstructors()) {
        StringBuilder line = new StringBuilder();
        renderConstructor(line, cons);
        constructors.add(collapse(line.toString()));
      }
    }
    return withMembers(collapse(header.toString()), constructors, false);
  }

  /**
   * {@code header} alone when there are no members, else each member on its own indented line.
   * {@code braces} wraps them in {@code { }}: a class body is braced, a constructor list is not.
   */
  private static String withMembers(String header, List<String> members, boolean braces) {
    if (members.isEmpty()) return header;
    StringBuilder out = new StringBuilder(header);
    if (braces) out.append(" {");
    for (String member : members) out.append("\n  ").append(member);
    if (braces) out.append("\n}");
    return out.toString();
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
    appendResultType(sb, field.getResultType(), field.getResultTypeLevel());
  }

  private static void renderOverridden(StringBuilder sb, Concrete.OverriddenField field) {
    sb.append("\\override ").append(field.getOverriddenField().textRepresentation());
    appendTypeParams(sb, field.getParameters());
    appendResultType(sb, field.getResultType(), field.getResultTypeLevel());
  }

  private static void appendParams(StringBuilder sb, List<? extends Concrete.Parameter> params) {
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

  private static void appendTypeParams(StringBuilder sb, List<? extends Concrete.TypeParameter> params) {
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

  /**
   * The fields each class gives a {@code \default}, with enough about each one to print it.
   *
   * <p>A {@code \default} makes a field optional rather than implementing it, so it is reported
   * separately from the fields a class implements outright. Only the defaults a class writes itself
   * are reported; its superclasses are still consulted, because a default may target a field a
   * superclass declared and only that declaration carries the field's type.
   *
   * <p>Feed every class in scope through {@link #addClass} (and every parameterised default through
   * {@link #addDefaultFunction}) before querying, then ask per class. Requires <b>resolved</b>
   * definitions: an unresolved {@code \extends} has no referent and an unresolved coclause no field.
   */
  public static final class ClassDefaults {
    /** One defaulted field: its name, the declaration carrying its type, and the fields its default mentions. */
    public record DefaultField(String name, @Nullable Concrete.ClassField declaration,
                               List<String> mentionedFields) {}

    /** What a single class states, before its superclasses are taken into account. */
    private record ClassEntry(Map<Referable, Concrete.ClassField> declaredFields,
                              Set<Referable> implementedFields,
                              Set<Referable> defaultedFields,
                              Map<Referable, List<String>> fieldsMentionedByDefault,
                              List<LocatedReferable> superclasses,
                              @Nullable ModulePath module) {}

    private final Map<LocatedReferable, ClassEntry> myClasses = new HashMap<>();
    private final Map<LocatedReferable, List<DefaultField>> myDefaults = new HashMap<>();
    private final Map<LocatedReferable, Set<ModulePath>> mySuperclassModules = new HashMap<>();

    /** Registers what {@code def} declares, implements, defaults and extends. */
    public void addClass(Concrete.ClassDefinition def) {
      Map<Referable, Concrete.ClassField> declaredFields = new HashMap<>();
      Set<Referable> implementedFields = new LinkedHashSet<>();
      Set<Referable> defaultedFields = new LinkedHashSet<>();
      Map<Referable, List<String>> mentioned = new HashMap<>();

      for (Concrete.ClassElement element : def.getElements()) {
        if (element instanceof Concrete.ClassField field) {
          declaredFields.put(field.getData(), field);
        } else if (element instanceof Concrete.ClassFieldImpl impl
                   && impl.getImplementedField() instanceof FieldReferable target) {
          if (impl.isDefault()) {
            defaultedFields.add(target);
            mentioned.put(target, MentionedFields.in(impl.implementation));
          } else {
            implementedFields.add(target);
          }
        }
        // An \override retypes a field without implementing it, so it counts as neither.
      }

      List<LocatedReferable> superclasses = new ArrayList<>();
      for (Concrete.ReferenceExpression sup : def.getSuperClasses()) {
        if (sup.getReferent() instanceof LocatedReferable parent) superclasses.add(parent);
      }
      myClasses.put(def.getData(), new ClassEntry(declaredFields, implementedFields, defaultedFields,
          mentioned, superclasses, def.getData().getModulePath()));
    }

    /**
     * Registers a {@code \default} written with parameters, which the parser lifts out of the class
     * body into a definition of its own. Its class must already be {@link #addClass}ed.
     */
    public void addDefaultFunction(Concrete.CoClauseFunctionDefinition def) {
      if (def.getKind() != FunctionKind.CLASS_COCLAUSE) return;
      ClassEntry entry = myClasses.get(def.getUseParent());
      if (entry == null || !(def.getImplementedField() instanceof FieldReferable target)) return;
      entry.defaultedFields().add(target);
      List<String> mentioned = MentionedFields.in(def);
      if (!mentioned.isEmpty()) entry.fieldsMentionedByDefault().put(target, mentioned);
    }

    /**
     * The fields {@code classRef} itself defaults, in the order its body writes them; empty for a
     * class that defaults nothing or was never registered. A field a superclass implements outright
     * is left out -- it is not optional.
     */
    public @NotNull List<DefaultField> defaultsOf(LocatedReferable classRef) {
      List<DefaultField> cached = myDefaults.get(classRef);
      if (cached != null) return cached;
      compute(classRef);
      return myDefaults.getOrDefault(classRef, List.of());
    }

    /**
     * The modules {@code classRef}'s superclasses are declared in, excluding its own. Editing one of
     * these changes what {@link #defaultsOf} reports without touching {@code classRef}'s own file.
     */
    public @NotNull Set<ModulePath> superclassModulesOf(LocatedReferable classRef) {
      if (!mySuperclassModules.containsKey(classRef)) compute(classRef);
      return mySuperclassModules.getOrDefault(classRef, Set.of());
    }

    private void compute(LocatedReferable classRef) {
      ClassEntry self = myClasses.get(classRef);
      if (self == null) return;

      Map<Referable, Concrete.ClassField> declaredAnywhere = new HashMap<>();
      Set<Referable> implementedAnywhere = new LinkedHashSet<>();
      Set<ModulePath> superclassModules = new LinkedHashSet<>();
      for (LocatedReferable cls : superclassesFirst(classRef)) {
        ClassEntry entry = myClasses.get(cls);
        if (entry == null) continue;
        declaredAnywhere.putAll(entry.declaredFields());
        implementedAnywhere.addAll(entry.implementedFields());
        if (entry.module() != null && !cls.equals(classRef)) superclassModules.add(entry.module());
      }

      List<DefaultField> defaults = new ArrayList<>();
      for (Referable field : self.defaultedFields()) {
        if (implementedAnywhere.contains(field)) continue;
        defaults.add(new DefaultField(field.textRepresentation(), declaredAnywhere.get(field),
            self.fieldsMentionedByDefault().getOrDefault(field, List.of())));
      }

      myDefaults.put(classRef, List.copyOf(defaults));
      mySuperclassModules.put(classRef, Set.copyOf(superclassModules));
    }

    /**
     * {@code classRef} and its transitive superclasses, each superclass before the class extending
     * it and each reached once, in {@code \extends} order.
     */
    private List<LocatedReferable> superclassesFirst(LocatedReferable classRef) {
      List<LocatedReferable> order = new ArrayList<>();
      Set<LocatedReferable> emitted = new LinkedHashSet<>();
      Set<LocatedReferable> expanded = new LinkedHashSet<>();
      Deque<LocatedReferable> stack = new ArrayDeque<>();
      stack.push(classRef);
      while (!stack.isEmpty()) {
        LocatedReferable cur = stack.pop();
        if (emitted.contains(cur)) continue;
        ClassEntry entry = myClasses.get(cur);
        if (entry == null || expanded.contains(cur)) {
          emitted.add(cur);
          order.add(cur);
          continue;
        }
        expanded.add(cur);
        stack.push(cur);
        List<LocatedReferable> superclasses = entry.superclasses();
        for (int i = superclasses.size() - 1; i >= 0; i--) {
          if (!emitted.contains(superclasses.get(i))) stack.push(superclasses.get(i));
        }
      }
      return order;
    }
  }

  /**
   * The class fields named somewhere in an expression or a definition, in the order they appear.
   *
   * <p>Stands in for a {@code \default}'s omitted implementation: knowing that a default is written
   * in terms of {@code +-comm} and {@code zro-right} says what overriding those would disturb.
   *
   * <p>It is a syntactic count of mentions, not a dependency closure -- a field named in a type
   * annotation counts, and a called function's own field uses do not. Requires resolved input.
   */
  static final class MentionedFields {
    private MentionedFields() {}

    /** Fields named in {@code expr}; empty when it is null or cannot be walked. */
    static List<String> in(@Nullable Concrete.Expression expr) {
      if (expr == null) return List.of();
      Collector collector = new Collector();
      try {
        expr.accept(collector, null);
      } catch (RuntimeException e) {
        return List.of();
      }
      return collector.result();
    }

    /** Fields named anywhere in {@code def}'s parameters, result type or body. */
    static List<String> in(Concrete.ResolvableDefinition def) {
      Collector collector = new Collector();
      try {
        def.accept(collector, null);
      } catch (RuntimeException e) {
        return List.of();
      }
      return collector.result();
    }

    private static final class Collector extends BaseConcreteExpressionVisitor<Void> {
      private final Set<String> myFields = new LinkedHashSet<>();

      List<String> result() {
        return List.copyOf(new ArrayList<>(myFields));
      }

      @Override
      public Concrete.Expression visitReference(Concrete.ReferenceExpression expr, Void params) {
        Referable ref = expr.getReferent();
        if (ref instanceof FieldReferable) myFields.add(ref.textRepresentation());
        return super.visitReference(expr, params);
      }

      @Override
      public Concrete.Expression visitFieldCall(Concrete.FieldCallExpression expr, Void params) {
        if (expr.getField() instanceof FieldReferable) myFields.add(expr.getField().textRepresentation());
        return super.visitFieldCall(expr, params);
      }
    }
  }
}
