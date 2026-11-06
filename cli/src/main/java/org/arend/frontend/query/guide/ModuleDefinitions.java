package org.arend.frontend.query.guide;

import org.arend.ext.module.ModulePath;
import org.arend.frontend.query.SignaturePrintVisitor;
import org.arend.frontend.query.SourcePositionUtils;
import org.arend.frontend.query.SymbolIndex;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.AccessModifier;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.term.prettyprint.PrettyPrintVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The definitions of one module as the guide checker sees them: their names, and two
 * fingerprints computed from the parsed (unresolved) source.
 *
 * <ul>
 *   <li>The <em>signature</em> fingerprint hashes what {@code -ss} prints as the signature: the
 *       header of a function or lemma, and the declared fields / constructors of a
 *       {@code \class}, {@code \record} or {@code \data} -- for those, the contents are the
 *       meaning.</li>
 *   <li>The <em>definition</em> fingerprint hashes the whole printed definition, body included,
 *       but only where the body is the meaning: {@code \func}, {@code \sfunc}, {@code \type},
 *       {@code \cons} and {@code \meta}. A {@code \lemma}'s body is a proof, and rewriting a proof
 *       never makes a description of the lemma wrong, so lemmas (and axioms, instances, coercions,
 *       level lemmas) get none.</li>
 * </ul>
 *
 * Both are computed from the pretty-printed AST, so formatting and comments never change them.
 */
final class ModuleDefinitions {
  /**
   * One definition.
   *
   * @param signature the rendered signature (shown in reports, so a fix can be written without
   *                  opening the source)
   * @param sigHash   fingerprint of {@code signature}; null when it could not be rendered
   * @param defHash   fingerprint of the whole definition when its body is its meaning, else null
   */
  record Def(String longName, String shortName, @Nullable String alias, SymbolIndex.Kind kind,
             AccessModifier access, Role role, int line, String signature,
             @Nullable String sigHash, @Nullable String defHash) {
    /** A short fingerprint covering both hashes, recorded for each mention of this definition. */
    String mentionHash() {
      return Fingerprints.hash((sigHash == null ? "-" : sigHash) + "/" + (defHash == null ? "-" : defHash),
          Fingerprints.MENTION_HASH_LENGTH);
    }

    /** The kind as {@code -ss kind=} spells it. */
    String kindLabel() {
      return switch (kind) {
        case FUNCTION -> "func";
        case CONSTRUCTOR -> "cons";
        default -> kind.name().toLowerCase(Locale.ROOT);
      };
    }
  }

  /**
   * Where a definition sits: at the top of the module, as a member of a class or record (part of
   * its interface), or in the {@code \where} block of another definition (usually a helper).
   */
  enum Role { TOP, MEMBER, HELPER }

  /**
   * What a name in the guide refers to: exactly one definition, several (ambiguous), or none.
   */
  record Resolution(@Nullable Def def, List<Def> candidates) {
    static final Resolution NONE = new Resolution(null, List.of());

    boolean isAmbiguous() {
      return def == null && candidates.size() > 1;
    }
  }

  final ModulePath modulePath;
  final List<Def> defs = new ArrayList<>();
  private final Map<String, Def> myByLongName = new HashMap<>();
  private final Map<String, List<Def>> myByShortName = new HashMap<>();

  private ModuleDefinitions(ModulePath modulePath) {
    this.modulePath = modulePath;
  }

  static @NotNull ModuleDefinitions collect(@NotNull ModulePath modulePath, @NotNull ConcreteGroup group) {
    ModuleDefinitions result = new ModuleDefinitions(modulePath);
    // The module's own group is not a definition: its statements are the top-level ones.
    Set<LocatedReferable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (ConcreteStatement statement : group.statements()) {
      if (statement.group() != null) result.collectGroup(statement.group(), Role.TOP, seen);
    }
    return result;
  }

  // ---- lookup -------------------------------------------------------------

  /**
   * Resolves a name as written in the guide. Tried in order: the exact long name (or alias);
   * the module path prefix stripped; for a dotted name, a unique definition whose long name
   * ends with it; for a bare name, a unique definition with that short name or alias.
   */
  @NotNull Resolution resolve(@NotNull String name) {
    Def exact = myByLongName.get(name);
    if (exact != null) return new Resolution(exact, List.of(exact));

    String modulePrefix = modulePath + ".";
    if (name.startsWith(modulePrefix) && name.length() > modulePrefix.length()) {
      Resolution stripped = resolve(name.substring(modulePrefix.length()));
      if (stripped.def != null || stripped.isAmbiguous()) return stripped;
    }

    List<Def> candidates;
    if (name.indexOf('.') > 0) {
      candidates = new ArrayList<>();
      String suffix = "." + name;
      for (Map.Entry<String, Def> e : myByLongName.entrySet()) {
        if (e.getKey().endsWith(suffix) && !candidates.contains(e.getValue())) candidates.add(e.getValue());
      }
    } else {
      candidates = myByShortName.getOrDefault(name, List.of());
    }
    if (candidates.size() == 1) return new Resolution(candidates.getFirst(), candidates);
    return candidates.isEmpty() ? Resolution.NONE : new Resolution(null, List.copyOf(candidates));
  }

  @Nullable Def byLongName(String longName) {
    return myByLongName.get(longName);
  }

  // ---- collection ---------------------------------------------------------

  private void collectGroup(ConcreteGroup group, Role role, Set<LocatedReferable> seen) {
    LocatedReferable ref = group.referable();
    if (ref instanceof TCDefReferable && seen.add(ref)) add(ref, group.definition(), role);
    for (LocatedReferable inner : group.getInternalReferables()) {
      if (seen.add(inner)) add(inner, SymbolIndex.findInternalDefinition(group.definition(), inner), Role.MEMBER);
    }
    for (ConcreteStatement statement : group.statements()) {
      if (statement.group() != null) collectGroup(statement.group(), Role.HELPER, seen);
    }
    for (ConcreteGroup dynamic : group.dynamicGroups()) {
      collectGroup(dynamic, Role.MEMBER, seen);
    }
  }

  private void add(LocatedReferable ref, @Nullable Concrete.GeneralDefinition def, Role role) {
    SymbolIndex.Kind kind = SymbolIndex.kindOf(ref, def);
    // Coclause implementations (`| f => ...` inside \cowith) are parts of their parent, not
    // definitions anyone documents; their generated names would only shadow real ones. A class's
    // `\default f \as name` is different: it is named, and documented.
    if (kind == SymbolIndex.Kind.COCLAUSE
        && !(def instanceof Concrete.BaseFunctionDefinition f && f.getKind() == org.arend.ext.concrete.definition.FunctionKind.CLASS_COCLAUSE)) {
      return;
    }

    String signature = def == null ? "" : renderSignature(def);
    // The definition's own name is left out of the hash: a rename alone changes nothing a
    // description says, and keeping the hash lets the checker find where a definition went.
    String sigHash = signature.isEmpty() ? null
        : Fingerprints.hash(withoutOwnName(signature, ref.textRepresentation()), Fingerprints.HASH_LENGTH);
    String defHash = null;
    if (def != null && bodyIsMeaning(def)) {
      String full = renderWhole(def);
      if (full != null) defHash = Fingerprints.hash(full, Fingerprints.HASH_LENGTH);
    }
    String alias = ref instanceof GlobalReferable g ? g.getAliasName() : null;
    AccessModifier access = ref instanceof GlobalReferable g ? g.getAccessModifier() : AccessModifier.PUBLIC;
    String longName = ref.getRefLongName().toString();
    Def d = new Def(longName, ref.textRepresentation(), alias, kind, access, role,
        SourcePositionUtils.lineColumn(ref)[0], signature, sigHash, defHash);
    defs.add(d);
    register(longName, d.shortName(), d);
    if (alias != null) {
      int dot = longName.lastIndexOf('.');
      register(dot < 0 ? alias : longName.substring(0, dot + 1) + alias, alias, d);
    }
  }

  private void register(String longName, String shortName, Def d) {
    myByLongName.putIfAbsent(longName, d);
    List<Def> list = myByShortName.computeIfAbsent(shortName, k -> new ArrayList<>());
    if (!list.contains(d)) list.add(d);
  }

  /** Whether a definition's body, not only its header, is what a description of it describes. */
  static boolean bodyIsMeaning(Concrete.GeneralDefinition def) {
    if (def instanceof Concrete.BaseFunctionDefinition f) {
      return switch (f.getKind()) {
        case FUNC, SFUNC, TYPE, CONS -> true;
        default -> false;
      };
    }
    return def instanceof Concrete.MetaDefinition;
  }

  /**
   * {@code signature} with the definition's name, which follows the keyword(s) as a separate
   * word, replaced by {@code _}. Only the first such word is replaced: later occurrences are
   * uses (a recursive data type's constructors name it), and those are part of the meaning.
   */
  static String withoutOwnName(String signature, String name) {
    int from = 0;
    while (true) {
      int i = signature.indexOf(name, from);
      if (i < 0) return signature;
      int end = i + name.length();
      boolean startsWord = i > 0 && signature.charAt(i - 1) == ' ';
      boolean endsWord = end == signature.length() || " {(".indexOf(signature.charAt(end)) >= 0;
      if (startsWord && endsWord) return signature.substring(0, i) + "_" + signature.substring(end);
      from = i + 1;
    }
  }

  private static String renderSignature(Concrete.GeneralDefinition def) {
    try {
      return Fingerprints.normalize(SignaturePrintVisitor.render(def));
    } catch (RuntimeException e) {
      return "";
    }
  }

  private static @Nullable String renderWhole(Concrete.GeneralDefinition def) {
    if (!(def instanceof Concrete.ResolvableDefinition resolvable)) return null;
    try {
      StringBuilder sb = new StringBuilder();
      resolvable.accept(new PrettyPrintVisitor(sb, 0), null);
      return Fingerprints.normalize(sb.toString());
    } catch (RuntimeException e) {
      return null;
    }
  }
}
