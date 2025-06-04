package org.arend.lib;

import org.arend.ext.*;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.definition.ConcreteMetaDefinition;
import org.arend.ext.typechecking.meta.MetaTypechecker;
import org.arend.ext.typechecking.meta.TrivialMetaTypechecker;
import org.arend.ext.core.definition.*;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.dependency.Dependency;
import org.arend.ext.module.ModulePath;
import org.arend.ext.reference.MetaRef;
import org.arend.ext.reference.Precedence;
import org.arend.ext.typechecking.*;
import org.arend.ext.typechecking.meta.DependencyMetaTypechecker;
import org.arend.ext.ui.ArendUI;
import org.arend.lib.goal.StdGoalSolver;
import org.arend.lib.key.IrreflexivityKey;
import org.arend.lib.key.ReflexivityKey;
import org.arend.lib.key.TransitivityKey;
import org.arend.lib.meta.*;
import org.arend.lib.meta.cases.CasesMeta;
import org.arend.lib.meta.cases.CasesMetaResolver;
import org.arend.lib.meta.cases.MatchingCasesMeta;
import org.arend.lib.meta.cases.MatchingCasesMetaResolver;
import org.arend.lib.meta.cong.CongruenceMeta;
import org.arend.lib.meta.debug.PrintMeta;
import org.arend.lib.meta.debug.RandomMeta;
import org.arend.lib.meta.debug.SleepMeta;
import org.arend.lib.meta.debug.TimeMeta;
import org.arend.lib.meta.equation.EquationMeta;
import org.arend.lib.meta.exists.ExistsMeta;
import org.arend.lib.meta.exists.GivenMeta;
import org.arend.lib.meta.exists.ExistsResolver;
import org.arend.lib.meta.linear.LinearSolverMeta;
import org.arend.lib.meta.rewrite.RewriteEquationMeta;
import org.arend.lib.meta.rewrite.RewriteMeta;
import org.arend.lib.meta.simplify.SimplifyMeta;
import org.arend.lib.util.Names;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;

@SuppressWarnings("unused")
public class StdExtension implements ArendExtension {
  public ArendPrelude prelude;
  private ConcreteFactory factory;

  public final IrreflexivityKey irreflexivityKey = new IrreflexivityKey("irreflexivity", this);
  public final TransitivityKey transitivityKey = new TransitivityKey("transitivity", this);
  public final ReflexivityKey reflexivityKey = new ReflexivityKey("reflexivity", this);

  @Dependency(module = "Paths")              public CoreFunctionDefinition transport;
  @Dependency(module = "Paths")              public CoreFunctionDefinition transportInv;
  @Dependency(module = "Paths", name = "*>") public CoreFunctionDefinition concat;
  @Dependency(module = "Paths")              public CoreFunctionDefinition inv;

  @Dependency(module = "Logic")                       public CoreDataDefinition Empty;

  @Dependency(module = "Algebra.Pointed")                          public CoreClassDefinition Pointed;
  @Dependency(module = "Algebra.Pointed")                          public CoreClassDefinition AddPointed;
  @Dependency(module = "Algebra.Pointed", name = "Pointed.ide")    public CoreClassField ide;
  @Dependency(module = "Algebra.Pointed", name = "AddPointed.zro") public CoreClassField zro;

  @Dependency(module = "Algebra.Semiring")                                public CoreClassDefinition Semiring;
  @Dependency(module = "Algebra.Ring")                                    public CoreClassDefinition Ring;
  @Dependency(module = "Algebra.Group", name = "AddGroup.negative")       public CoreClassField negative;
  @Dependency(module = "Algebra.Semiring", name = "Semiring.natCoef")     public CoreClassField natCoef;
  @Dependency(module = "Algebra.Ring", name = "Ring.intCoef")             public CoreFunctionDefinition intCoef;

  @Dependency(module = "Order.LinearOrder")                                         public CoreClassDefinition LinearOrder;
  @Dependency(module = "Order.StrictOrder", name = "StrictPoset.<")                 public CoreClassField less;
  @Dependency(module = "Order.Biordered", name = "BiorderedSet.<-transitive-left")  public CoreClassField lessTransitiveLeft;

  public final ContradictionMeta contradictionMeta = new ContradictionMeta(this);

  private final StdGoalSolver goalSolver = new StdGoalSolver();
  private final StdNumberTypechecker numberTypechecker = new StdNumberTypechecker(this);
  private final ListDefinitionListener definitionListener = new ListDefinitionListener().addDeclaredListeners(this);
  public ArendUI ui;

  @Override
  public void setUI(@NotNull ArendUI ui) {
    this.ui = ui;
  }

  @Override
  public void setPrelude(@NotNull ArendPrelude prelude) {
    this.prelude = prelude;
  }

  @Override
  public void setConcreteFactory(@NotNull ConcreteFactory factory) {
    this.factory = factory;
  }

  private MetaRef makeRef(ModulePath modulePath, String name, MetaResolver resolver, MetaDefinition definition) {
    return factory.metaRef(factory.moduleRef(modulePath), name, Precedence.DEFAULT, null, null, resolver, new TrivialMetaTypechecker(definition));
  }

  private MetaRef makeRef(ModulePath modulePath, String name, MetaDefinition definition) {
    return makeRef(modulePath, name, definition instanceof MetaResolver ? (MetaResolver) definition : null, definition);
  }

  private MetaRef makeRef(ModulePath modulePath, String name, Precedence precedence, MetaDefinition definition) {
    return factory.metaRef(factory.moduleRef(modulePath), name, precedence, null, null, definition instanceof MetaResolver ? (MetaResolver) definition : null, new TrivialMetaTypechecker(definition));
  }

  private ConcreteMetaDefinition makeDef(ModulePath modulePath, String name, MetaDefinition definition) {
    return factory.metaDef(makeRef(modulePath, name, definition), Collections.emptyList(), null);
  }

  private ConcreteMetaDefinition makeDef(MetaRef ref) {
    return factory.metaDef(ref, Collections.emptyList(), ref.getTypechecker() instanceof DependencyMetaTypechecker tc ? tc.makeBody(factory) : null);
  }

  private ConcreteMetaDefinition makeDef(ModulePath modulePath, String name, MetaResolver resolver, MetaTypechecker typechecker) {
    return makeDef(factory.metaRef(factory.moduleRef(modulePath), name, Precedence.DEFAULT, null, null, resolver, typechecker));
  }

  private ConcreteMetaDefinition makeDef(ModulePath modulePath, String name, DependencyMetaTypechecker typechecker) {
    return makeDef(modulePath, name, null, typechecker);
  }

  @Override
  public void declareDefinitions(@NotNull DefinitionContributor contributor) {
    ModulePath meta = new ModulePath("Meta");
    ModulePath logicMeta = ModulePath.fromString("Logic.Meta");
    ModulePath paths = new ModulePath("Paths");
    ModulePath logic = new ModulePath("Logic");
    ModulePath category = new ModulePath("Category");
    ModulePath categorySolver = ModulePath.fromString("Category.Solver");
    ModulePath equiv = new ModulePath("Equiv");
    ModulePath orderedAlgebra = ModulePath.fromString("Algebra.Ordered");
    ModulePath pointed = ModulePath.fromString("Algebra.Pointed");
    ModulePath lattice = ModulePath.fromString("Order.Lattice");
    ModulePath algebraAlgebra = ModulePath.fromString("Algebra.Algebra");
    ModulePath group = ModulePath.fromString("Algebra.Group");
    ModulePath groupSolver = ModulePath.fromString("Algebra.Group.Solver");
    ModulePath algebraModule = ModulePath.fromString("Algebra.Module");
    ModulePath monoid = ModulePath.fromString("Algebra.Monoid");
    ModulePath monoidSolver = ModulePath.fromString("Algebra.Monoid.Solver");
    ModulePath semiring = ModulePath.fromString("Algebra.Semiring");
    ModulePath ring = ModulePath.fromString("Algebra.Ring");
    ModulePath ringSolver = ModulePath.fromString("Algebra.Ring.Solver");
    ModulePath arithInt = ModulePath.fromString("Arith.Int");
    ModulePath arithNat = ModulePath.fromString("Arith.Nat");
    ModulePath arithRat = ModulePath.fromString("Arith.Rat");
    ModulePath dataList = Names.getListModule();
    ModulePath set = Names.getSetModule();
    String constructorName = "constructor";

    contributor.declare(meta, logicMeta);
    contributor.declare(text("`later meta args` defers the invocation of `meta args`"), makeDef(meta, "later", new LaterMeta()));
    contributor.declare(multiline("""
        `fails meta args` succeeds if and only if `meta args` fails

        `fails {T} meta args` succeeds if and only if `meta args : T` fails
        """), makeDef(meta, "fails", new FailsMeta()));
    contributor.declare(text("`using (e_1, ... e_n) e` adds `e_1`, ... `e_n` to the context before checking `e`"),
        makeDef(meta, "using", new UsingMeta(true)));
    contributor.declare(text("`usingOnly (e_1, ... e_n) e` replaces the context with `e_1`, ... `e_n` before checking `e`"),
        makeDef(meta, "usingOnly", new UsingMeta(false)));
    contributor.declare(text("`hiding (x_1, ... x_n) e` hides local variables `x_1`, ... `x_n` from the context before checking `e`"),
        makeDef(meta, "hiding", new HidingMeta()));
    contributor.declare(text("`run { e_1, ... e_n }` is equivalent to `e_1 $ e_2 $ ... $ e_n`"),
        makeDef(meta, "run", new RunMeta()));
    contributor.declare(text("`((f_1, ... f_n) at x) r` replaces variable `x` with `f_1 (... (f_n x) ...)` and runs `r` in the modified context"),
        factory.metaDef(makeRef(meta, "at", new Precedence(Precedence.Associativity.NON_ASSOC, (byte) 1, true), new AtMeta()), Collections.emptyList(), null));
    contributor.declare(text("`f in x` is equivalent to `\\let r => f x \\in r`. Also, `(f_1, ... f_n) in x` is equivalent to `f_1 in ... f_n in x`. This meta is usually used with `f` being a meta such as `rewrite`, `simplify`, `simp_coe`, or `unfold`."),
        factory.metaDef(makeRef(meta, "in", new Precedence(Precedence.Associativity.RIGHT_ASSOC, (byte) 1, true), new InMeta()), Collections.emptyList(), null));
    CasesMetaResolver casesMetaResolver = new CasesMetaResolver(factory);
    contributor.declare(multiline("""
        `cases args default` works just like `mcases args default`, but does not search for \\case expressions or definition invocations.
        Each argument has a set of parameters that can be configured.
        Parameters are specified after keyword 'arg' which is written after the argument.
        Available parameters are 'addPath', 'name', and 'as'.
        Parameter 'name' can be used to specify the name of the argument which can be used in types of subsequent arguments.
        Parameter 'as' can be used to specify an \\as-name that will be added to corresponding patterns in each clause.
        The type of an argument is specified as either `e : E` or `e arg parameters : E`.
        The flag 'addPath' indicates that argument `idp` with type `e = x` should be added after the current one, where `e` is the current argument and `x` is its name.
        That is, `cases (e arg addPath)` is equivalent to `cases (e arg (name = x), idp : e = x)`.
        """), makeDef(meta, "cases", casesMetaResolver, new DependencyMetaTypechecker(CasesMeta.class, () -> new CasesMeta(casesMetaResolver))));
    contributor.declare(multiline("""
        `mcases {def} args default \\with { ... }` finds all invocations of definition `def` in the expected type and generate a \\case expression that matches arguments of those invocations.

        It matches only those arguments which are matched in `def`.
        If the explicit argument is omitted, then `mcases` searches for \\case expressions instead of definition invocations.
        `default` is an optional argument which is used as a default result for missing clauses.
        The list of clauses after \\with can be omitted if the default expression is specified.
        `args` is a comma-separated list of expressions (which can be omitted) that will be additionally matched in the resulting \\case expressions.
        These arguments are written in the same syntax as arguments in `cases`.
        `mcases` also searches for occurrences of `def` in the types of these additional expressions.
        Parameters of found arguments can be specified in the second implicit argument.
        The syntax is similar to the syntax for arguments in `cases`, but the expression should be omitted.
        If the first implicit argument is `_`, it will be skipped.
        `mcases {def_1, ... def_n}` searches for occurrences of definitions `def_1`, ... `def_n`.
        `mcases {def, i_1, ... i_n}` matches arguments only of `i_1`-th, ... `i_n`-th occurrences of `def`.
        For example,
        * `mcases {(def1,4),def2,(def3,1,2)}` looks for the 4th occurrence of `def1`, all occurrences of `def2`, and the first and the second occurrence of `def3`.
        * `mcases {(1,2),(def,1)}` looks for the first and the second occurrence of a \\case expression and the first occurrence of `def`.
        * `mcases {(def1,2),(),def2}` looks for the second occurrence of `def1`, all occurrences of \\case expressions, and all occurrences of `def2`.
        * `mcases {_} {arg addPath, arg (), arg addPath}` looks for case expressions and adds a path argument after the first and the third matched expression.
        """), makeDef(meta, "mcases", new MatchingCasesMetaResolver(casesMetaResolver), new DependencyMetaTypechecker(MatchingCasesMeta.class, () -> new MatchingCasesMeta(casesMetaResolver))));
    contributor.declare(multiline("""
        `unfold (f_1, ... f_n) e` unfolds functions/fields/variables `f_1`, ... `f_n` in the expected type before type-checking of `e` and returns `e` itself.
        If the first argument is omitted, it unfold all fields.
        If the expected type is unknown, it unfolds these function in the result type of `e`.
        """), makeDef(meta, "unfold", new DeferredMetaDefinition(new UnfoldMeta(), true, false)));
    contributor.declare(text("Unfolds \\let expressions"),
        makeDef(meta, "unfold_let", new DeferredMetaDefinition(new UnfoldLetMeta(), true, false)));
    contributor.declare(text("Unfolds recursively top-level functions and fields"),
        makeDef(meta, "unfolds", new DeferredMetaDefinition(new UnfoldsMeta(), true, false)));
    MetaRef orMeta = makeRef(meta, "<|>", new Precedence(Precedence.Associativity.RIGHT_ASSOC, (byte) 3, true), new OrElseMeta());
    contributor.declare(multiline("""
        `x <|> y` invokes `x` and if it fails, invokes `y`
        Also, `(x <|> y) z_1 ... z_n` is equivalent to `x z_1 ... z_n <|> z_1 ... z_n`
        """), factory.metaDef(orMeta, Collections.emptyList(), null));
    contributor.declare(hList(text("The same as "), refDoc(orMeta)), makeDef(meta, "try", new OrElseMeta()));
    contributor.declare(multiline("""
        Inserts data type arguments in constructor invocation.
        If constructor `con` has 3 data type arguments, then `mkcon con args` is equivalent to `con {_} {_} {_} args`
        """), makeDef(meta, "mkcon", new MakeConstructorMeta()));
    contributor.declare(multiline("""
        * `assumption` searches for a proof in the context. It tries variables that are declared later first.
        * `assumption` {n} returns the n-th variables from the context counting from the end.
        * `assumption` {n} a1 ... ak applies n-th variable from the context to arguments a1, ... ak.
        """), makeDef(meta, "assumption", new AssumptionMeta()));
    contributor.declare(text("`defaultImpl C F E` returns the default implementation of field `F` in class `C` applied to expression `E`. The third argument can be omitted, in which case either `\\this` or `_` will be used instead,"),
        makeDef(meta, "defaultImpl", new DefaultImplMeta()));

    ModulePath pathsMeta = ModulePath.fromString("Paths.Meta");
    contributor.declare(pathsMeta, equiv);
    contributor.declare(pathsMeta, ModulePath.fromString("Equiv.Univalence"), "Equiv-to-=", "QEquiv-to-=");
    contributor.declare(pathsMeta, logic);
    contributor.declare(pathsMeta, meta);
    contributor.declare(pathsMeta, paths);
    ConcreteMetaDefinition rewrite = makeDef(pathsMeta, "rewrite", new DependencyMetaTypechecker(RewriteMeta.class, () -> new RewriteMeta(true)));
    contributor.declare(multiline("""
        `rewrite (p : a = b) t : T` replaces occurrences of `a` in `T` with a variable `x` obtaining a type `T[x/a]` and returns `transportInv (\\lam x => T[x/a]) p t`

        If the expected type is unknown, the meta rewrites in the type of the arguments instead.
        `rewrite {i_1, ... i_k} p t` replaces only occurrences with indices `i_1`, ... `i_k`. Here `i_j` is the number of occurrence after replacing all the previous occurrences.\s
        Also, `p` may be a function, in which case `rewrite p` is equivalent to `rewrite (p _ ... _)`.
        `rewrite (p_1, ... p_n) t` is equivalent to `rewrite p_1 (... rewrite p_n t ...)`
        """), rewrite);
    contributor.declare(text("`rewriteI p` is equivalent to `rewrite (inv p)`"),
        makeDef(pathsMeta, "rewriteI", new DependencyMetaTypechecker(RewriteMeta.class, () -> new RewriteMeta(false))));
    ConcreteMetaDefinition simp_coe = makeDef(pathsMeta, "simp_coe", new ClassExtResolver(), new DependencyMetaTypechecker(SimpCoeMeta.class, SimpCoeMeta::new));
    ConcreteMetaDefinition extMeta = makeDef(pathsMeta, "ext", new ClassExtResolver(), new DependencyMetaTypechecker(ExtMeta.class, () -> new DeferredMetaDefinition(new ExtMeta(false), false, ExtMeta.defermentChecker)));
    contributor.declare(vList(
        text("Simplifies certain equalities. It expects one argument and the type of this argument is called 'subgoal'. The expected type is called 'goal'."),
        text("* If the goal is `coe (\\lam i => \\Pi (x : A) -> B x i) f right a = b'`, then the subgoal is `coe (B a) (f a) right = b`."),
        text("* If the goal is `coe (\\lam i => \\Pi (x : A) -> B x i) f right = g'`, then the subgoal is `\\Pi (a : A) -> coe (B a) (f a) right = g a`."),
        text("* If the goal is `coe (\\lam i => A i -> B i) f right = g'`, then the subgoal is `\\Pi (a : A left) -> coe B (f a) right = g (coe A a right)`."),
        hList(text("* If the type under "), refDoc(prelude.getCoerceRef()), text(" is a higher-order non-dependent function type, "), refDoc(simp_coe.getRef()), text(" simplifies it recursively.")),
        text("* If the goal is `(coe (\\lam i => \\Sigma (x_1 : A_1 i) ... (x_n : A_n i) ...) t right).n = b'`, then the subgoal is `coe A_n t.n right = b`."),
        text("* If the goal is `coe (\\lam i => \\Sigma (x_1 : A_1) ... (x_n : A_n) (B_{n+1} i) ... (B_k i)) t right = s'`, then the subgoal is a \\Sigma type consisting of equalities as specified above ignoring fields in \\Prop."),
        hList(text("* If the type under "), refDoc(prelude.getCoerceRef()), text(" is a record, then "), refDoc(simp_coe.getRef()), text(" works similarly to the case of \\Sigma types. The copattern matching syntax as in "), refDoc(extMeta.getRef()), text("is also supported.")),
        hList(text("* All of the above cases also work for goals with `Paths.transport` instead of "), refDoc(prelude.getCoerceRef()), text(" since the former evaluates to the latter.")),
        text("* If the goal is `transport (\\lam x => f x = g x) p q = s`, then the subgoal is `q *> pmap g p = pmap f p *> s`. If `f` does not depend on `x`, then the right hand side of the subgoal is simply `s`."),
        text("If the expected type is unknown or if the meta is applied to more than one argument, then it applies to the type of the argument instead of the expected type.")),
        simp_coe);
    contributor.declare(multiline("""
        Proves goals of the form `a = {A} a'`. It expects (at most) one argument and the type of this argument is called 'subgoal'. The expected type is called 'goal'
        * If the goal is `f = {\\Pi (x_1 : A_1) ... (x_n : A_n) -> B} g`, then the subgoal is `\\Pi (x_1 : A_1) ... (x_n : A_n) -> f x_1 ... x_n = g x_1 ... x_n`
        * If the goal is `t = {\\Sigma (x_1 : A_1) ... (x_n : A_n) (y_1 : B_1 x_1 ... x_n) ... (y_k : B_k x_1 ... x_n) (z_1 : C_1) ... (z_m : C_m)} s`, where `C_i : \\Prop` and they can depend on `x_j` and `y_l` for all `i`, `j`, and `l`, then the subgoal is `\\Sigma (p_1 : t.1 = s.1) ... (p_n : t.n = s.n) D_1 ... D_k`, where `D_j` is equal to `coe (\\lam i => B (p_1 @ i) ... (p_n @ i)) t.{k + j - 1} right = s.{k + j - 1}`
        * If the goal is `t = {R} s`, where `R` is a record, then the subgoal is defined in the same way as for \\Sigma-types It is also possible to use the following syntax in this case: `ext R { | f_1 => e_1 ... | f_l => e_l }`, which is equivalent to `ext (e_1, ... e_l)`
        * If the goal is `A = {\\Prop} B`, then the subgoal is `\\Sigma (A -> B) (B -> A)`
        * If the goal is `A = {\\Type} B`, then the subgoal is `Equiv {A} {B}`
        * If the goal is `x = {P} y`, where `P : \\Prop`, then there is no subgoal
        """), extMeta);
    contributor.declare(hList(text("Similar to "), refDoc(extMeta.getRef()), text(", but also applies either "), refDoc(simp_coe.getRef()), text(" or "), refDoc(extMeta.getRef()), text(" when a field of a \\Sigma-type or a record has an appropriate type.")),
        makeDef(pathsMeta, "exts", new ClassExtResolver(), new DependencyMetaTypechecker(ExtMeta.class, () -> new DeferredMetaDefinition(new ExtMeta(true), false, ExtMeta.defermentChecker))));

    MetaDefinition apply = new ApplyMeta();
    ModulePath function = ModulePath.fromString("Function.Meta");
    contributor.declare(text("`f $ a` returns `f a`"),
        factory.metaDef(makeRef(function, "$", new Precedence(Precedence.Associativity.RIGHT_ASSOC, (byte) 0, true), apply), Collections.emptyList(), null));
    contributor.declare(text("`f #' a` returns `f a`"),
        factory.metaDef(makeRef(function, "#'", new Precedence(Precedence.Associativity.LEFT_ASSOC, (byte) 0, true), apply), Collections.emptyList(), null));
    contributor.declare(multiline("""
        `repeat {n} f x` returns `f^n(x)

        ``repeat f x` repeats `f` until it fails and returns `x` in this case
        """), makeDef(function, "repeat", new RepeatMeta()));

    ModulePath algebra = ModulePath.fromString("Algebra.Meta");
    contributor.declare(algebra, algebraAlgebra);
    contributor.declare(algebra, group);
    contributor.declare(algebra, groupSolver, "NatData", "CGroupData", "GroupTerm");
    contributor.declare(algebra, ModulePath.fromString("Algebra.Linear.Solver"));
    contributor.declare(algebra, algebraModule);
    contributor.declare(algebra, monoid);
    contributor.declare(algebra, monoidSolver);
    contributor.declare(algebra, orderedAlgebra);
    contributor.declare(algebra, pointed);
    contributor.declare(algebra, ring);
    contributor.declare(algebra, ringSolver);
    contributor.declare(algebra, semiring);
    contributor.declare(algebra, arithInt);
    contributor.declare(algebra, arithNat);
    contributor.declare(algebra, arithRat);
    contributor.declare(algebra, category, "Precat");
    contributor.declare(algebra, categorySolver);
    contributor.declare(algebra, ModulePath.fromString("Data.Bool"));
    contributor.declare(algebra, dataList);
    contributor.declare(algebra, equiv);
    contributor.declare(algebra, lattice);
    contributor.declare(algebra, ModulePath.fromString("Order.LinearOrder"));
    contributor.declare(algebra, ModulePath.fromString("Order.PartialOrder"));
    contributor.declare(algebra, ModulePath.fromString("Order.StrictOrder"));
    contributor.declare(algebra, paths);
    contributor.declare(algebra, set);
    contributor.declare(multiline("""
        `equation a_1 ... a_n` proves an equation a_0 = a_{n+1} using a_1, ... a_n as intermediate steps

        A proof of a_i = a_{i+1} can be specified as implicit arguments between them.
        `using`, `usingOnly`, and `hiding` with a single argument can be used instead of a proof to control the context.
        The first implicit argument can be either a universe or a subclass of either `Algebra.Monoid.Monoid`, `Algebra.Monoid.AddMonoid`, or `Order.Lattice.Bounded.MeetSemilattice`.
        In the former case, the meta will prove an equality in a type without using any additional structure on it.
        In the latter case, the meta will prove an equality using only structure available in the specified class.
        """), makeDef(algebra, "equation", new DependencyMetaTypechecker(EquationMeta.class, () -> new DeferredMetaDefinition(new EquationMeta(this), true))));
    contributor.declare(text("Solve systems of linear equations"), makeDef(algebra, "linarith", new DependencyMetaTypechecker(LinearSolverMeta.class, () -> new DeferredMetaDefinition(new LinearSolverMeta(), true))));
    contributor.declare(text("Proves an equality by congruence closure of equalities in the context. E.g. derives f a = g b from f = g and a = b"),
        makeDef(algebra, "cong", new DependencyMetaTypechecker(CongruenceMeta.class, () ->  new DeferredMetaDefinition(new CongruenceMeta()))));
    contributor.declare(text("Simplifies the expected type or the type of the argument if the expected type is unknown."),
        makeDef(algebra, "simplify", new DependencyMetaTypechecker(SimplifyMeta.class, () -> new DeferredMetaDefinition(new SimplifyMeta(), true))));
    contributor.declare(vList(
            hList(text("`rewriteEq (p : a = b) t : T` is similar to "), refDoc(rewrite.getRef()), text(", but it finds and replaces occurrences of `a` up to algebraic equivalences.")),
            text("For example, `rewriteEq (p : b * (c * id) = x) t : T` rewrites `(a * b) * (id * c)` as `a * x` in `T`."),
            text("Similarly to `rewrite` this meta allows specification of occurrence numbers."),
            text("Currently this meta supports noncommutative monoids and categories.")),
        makeDef(algebra, "rewriteEq", new DependencyMetaTypechecker(RewriteEquationMeta.class, RewriteEquationMeta::new)));

    contributor.declare(logicMeta, Names.getLogicModule());
    contributor.declare(multiline("""
        Derives a contradiction from assumptions in the context

        A proof of a contradiction can be explicitly specified as an implicit argument
        `using`, `usingOnly`, and `hiding` with a single argument can be used instead of a proof to control the context
        """), makeDef(logicMeta, "contradiction", contradictionMeta));
    ConcreteMetaDefinition givenMetaRef = makeDef(logicMeta, "Given", new ExistsResolver(GivenMeta.Kind.SIGMA), new TrivialMetaTypechecker(new GivenMeta(GivenMeta.Kind.SIGMA)));
    contributor.declare(multiline("""
        Given constructs a \\Sigma-type:
        * `Given (x y z : A) B` is equivalent to `\\Sigma (x y z : A) B`.
        * `Given {x y z} B` is equivalent to `\\Sigma (x y z : _) B`
        * If `P : A -> B -> C -> \\Type`, then `Given ((x,y,z) (a,b,c) : P) (Q x y z a b c)` is equivalent to `\\Sigma (x a : A) (y b : B) (z c : C) (P x y z) (P a b c) (Q x y z a b c)`
        * If `l : Array A`, then `Given (x y : l) (P x y)` is equivalent to `\\Sigma (j j' : Fin l.len) (P (l j) (l j'))`
        """), givenMetaRef);
    MetaRef existsMetaRef = factory.metaRef(factory.moduleRef(logicMeta), "Exists", Precedence.DEFAULT, "∃", Precedence.DEFAULT, new ExistsResolver(GivenMeta.Kind.TRUNCATED), new DependencyMetaTypechecker(ExistsMeta.class, ExistsMeta::new));
    contributor.declare(hList(refDoc(existsMetaRef), text(" is a truncated version of "), refDoc(givenMetaRef.getRef()), text(". That is, `Exists a b c` is equivalent to `TruncP (Given a b c)`")),
        makeDef(existsMetaRef));
    MetaRef forallMetaRef = factory.metaRef(factory.moduleRef(logicMeta), "Forall", Precedence.DEFAULT, "∀", Precedence.DEFAULT, new ExistsResolver(GivenMeta.Kind.PI), new TrivialMetaTypechecker(new GivenMeta(GivenMeta.Kind.PI)));
    contributor.declare(vList(
        hList(refDoc(forallMetaRef), text(" works like "), refDoc(givenMetaRef.getRef()), text(", but returns a \\Pi-type instead of a \\Sigma-type.")),
        text("The last argument should be a type and will be used as the codomain."),
        hList(text("Other arguments work like arguments of "), refDoc(givenMetaRef.getRef()), text(" with the exception that curly braces mean implicit arguments:")),
        multiline("""
          * `Forall (x y : A) B` is equivalent to `\\Pi (x y : A) -> B`
          * `Forall {x y : A} B` is equivalent to `\\Pi {x y : A} -> B`
          * `Forall x y B` is equivalent to `\\Pi (x : _) (y : _) -> B
          * `Forall x (B 0) (B 1)` is equivalent to `\\Pi (x : _) -> B 0 -> B 1
          * `Forall {x y} {z} B` is equivalent to `\\Pi {x y : _} {z : _} -> B`
          * If `P : A -> \\Type`, then `Forall {x y : P} (Q x) (Q y)` is equivalent to `\\Pi {x y : A} -> P x -> P y -> Q x -> Q y`
          """)), makeDef(forallMetaRef));
    contributor.declare(text("Returns either a tuple, a \\new expression, or a single constructor of a data type depending on the expected type"),
        makeDef(logicMeta, constructorName, new ConstructorMeta(false)));

    ModulePath debug = ModulePath.fromString("Debug.Meta");
    contributor.declare(text("Returns current time in milliseconds"), makeDef(debug, "time", new TimeMeta()));
    contributor.declare(text("Prints the argument to the console"), makeDef(debug, "println", new PrintMeta(this)));
    contributor.declare(text("`sleep m` waits for `m` milliseconds"), makeDef(debug, "sleep", new SleepMeta()));
    contributor.declare(multiline("""
        `random` returns a random number.
        `random n` returns a random number between 0 and `n`.
        `random (l,u)` returns a random number between `l` and `u`.
        """), makeDef(debug, "random", new RandomMeta()));
    MetaRef nfMeta = makeRef(debug, "nf", new NormalizationMeta(NormalizationMode.ENF));
    contributor.declare(text("Normalizes the argument"), factory.metaDef(nfMeta, Collections.emptyList(), null));
    contributor.declare(nullDoc(), factory.metaDef(factory.metaRef(nfMeta, "whnf", Precedence.DEFAULT, null, null, null, new TrivialMetaTypechecker(new NormalizationMeta(NormalizationMode.WHNF))), Collections.emptyList(), null));
    contributor.declare(nullDoc(), factory.metaDef(factory.metaRef(nfMeta, "rnf", Precedence.DEFAULT, null, null, null, new TrivialMetaTypechecker(new NormalizationMeta(NormalizationMode.RNF))), Collections.emptyList(), null));

    ModulePath categoryMeta = ModulePath.fromString("Category.Meta");
    contributor.declare(categoryMeta, category);
    contributor.declare(categoryMeta, equiv);
    contributor.declare(categoryMeta, paths);
    contributor.declare(categoryMeta, pathsMeta);
    contributor.declare(categoryMeta, set);
    contributor.declare(categoryMeta, ModulePath.fromString("Set.SetCategory"));
    contributor.declare(categoryMeta, ModulePath.fromString("Set.SetHom"));
    contributor.declare(text("Proves univalence for categories. The type of objects must extend `BaseSet` and the Hom-set must extend `SetHom` with properties only."), makeDef(categoryMeta, "sip", new DependencyMetaTypechecker(SIPMeta.class, SIPMeta::new)));
  }

  @Override
  public @Nullable StdGoalSolver getGoalSolver() {
    return goalSolver;
  }

  @Override
  public @Nullable LiteralTypechecker getLiteralTypechecker() {
    return numberTypechecker;
  }

  @Override
  public @Nullable DefinitionListener getDefinitionListener() {
    return definitionListener;
  }
}
