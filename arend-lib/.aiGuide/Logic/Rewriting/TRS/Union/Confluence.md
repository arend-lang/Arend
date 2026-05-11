### Logic.Rewriting.TRS.Union.Confluence

Confluence proof for the union of color-disjoint confluent higher-order rewrite systems via bordered parallel reduction.

This module formalizes the central diamond lemma for the union construction: if each constituent monochrome system is confluent, then their disjoint union admits a joinable diagram on a refined parallel-reduction relation. The key technical device is `BorderedParallelReduction`, which combines parallel rewriting in arguments with an optional top-level monochrome rewrite step whose color must "oppose" the surrounding head symbol. Inner arguments carry an `OppositeColored` tag preventing same-color collapse across the border, which lets confluence be reduced componentwise to confluence in a single color via `unify-top` / `alternate-subst`. The proof proceeds by case analysis on whether each side's bordered step rewrites at the root, dispatching to monochromatic confluence (`join-multicolor-tlcrs`) and using substitution/weakening lifting lemmas (`reduction-over-substitution`, `bpr-over-weakening`, `tlcr-over-substitution`) to propagate joins through contexts.

#### Colored Top-Level Reduction

- **`TopLevelColoredReduction`**: A single rewrite step in color `c`, indexed by source/target terms `A B`. Constructors `rewrite-with-rule-colored` (root rule application of a `c`-colored rule with linear pattern matching) and `rewrite-with-parameter-f-colored` (rewriting under a single function-symbol argument while leaving siblings intact). Records that the root has color `c` via `HasColoredRoot`.
- **`TopLevelColoredReduction.extract-root-coloring`**: Recovers `HasColoredRoot color A` from any colored reduction, encoding the invariant that monochrome reduction preserves the root's color.

#### Opposite-Color Tagging

- **`OppositeColored`**: Predicate stating that an optional color `maybe` is incompatible with `negated`: either `nothing` (skip) or a `just c` with `c /= negated`. Used to enforce the layering discipline between outer head symbol and inner bordered reductions.

#### Bordered and Switching Reductions

- **`BorderedParallelReduction global-color color A B`**: Two-tier parallel reduction: either trivially equal terms (`equal-trees`), or parallelization through a function symbol (`parallelization-f`) consisting of inner bordered reductions (each tagged with an opposite color to the head) into a `mediator`, followed by a top-level switching reduction from `func f mediator` to `B`.
- **`BorderedParallelReduction.append-tlcr`**: Extends a bordered reduction `A => B` by a closure of top-level colored reductions `B ~>* C`, producing a bordered reduction `A => C`. Handles all three constructor cases by appending the tail to the existing switching reduction.
- **`SwitchingReduction global-color color A B`**: Either `cr-skip` (no top-level reduction, `A = B`) or `cr-rewrite` (a closure of `TopLevelColoredReduction color` with the global color matching). Acts as the optional monochrome "border" attached to the parallel inner step.
- **`TrichromaticParallelReduction A B`**: Existential packaging of a global color, an optional border color, and a `BorderedParallelReduction`. Convenience type for stating the joining theorem.

#### Confluence Statement and Witnesses

- **`ConfluentialSystem env rules`**: Property that a single-color rewrite system is confluent, with the strong-form join producing a closure of rewrites paired with `FunctionalWitness` data.
- **`FunctionalWitness rd`**: Structural witness that a rewrite step `rd` either acts at the root (`rule-rewriting`) or recurses through a single argument (`param-rewriting`). Used downstream to keep track of where reductions occur.

#### Main Confluence Theorem

- **`bpr-confluence`**: Diamond lemma for `BorderedParallelReduction`. Given peaks `A => B` and `A => C` and per-color confluence, produces a common reduct `D` together with bordered reductions `B => D` and `C => D` whose global colors are appropriately swapped. Case split on whether either side is `equal-trees`, on whether either border is `cr-skip` or `cr-rewrite`, and—when both borders fire—on whether the closures are empty or witness an actual root rewrite (in which case `HasColoredRoot.equalize-colors` forces the two colors to agree). Uses `unify-top` and `unwrap-cwr` / `unwrap-double-cwr` to glue inner and border reductions.
- **`bpr-confluence.unwrap-cwr`**: Closes a single-sided peak where one branch consists of a switching reduction and the other of a bare colored closure. Uses `join-multicolor-tlcrs` to join the two and then `append-tlcr` to extend pending bordered reductions to the join point.
- **`bpr-confluence.unwrap-double-cwr`**: Closes a peak where both sides have switching reductions out of a common term `E`, splitting on whether each switching reduction is `cr-skip` or `cr-rewrite` and chaining monochromatic joins to find the common reduct.
- **`bpr-confluence.join-multicolor-tlcrs`**: Joins two top-level closures of (possibly different) colors. If colors agree, defers to `join-tlcrs` and per-color confluence; if they disagree, at least one closure must be empty since both witnesses would force `HasColoredRoot` of incompatible colors at the same root—an impossibility resolved by absurdity.

#### Lifting Through Substitutions and Weakenings

- **`unify-top`**: Given a top-level closure `func f arguments-A ~>* B` of color `color` together with bordered inner reductions of `arguments-A` to `arguments-C` (with opposing color), produces a common reduct `X` reachable by a `color`-colored closure from `func f arguments-C` and a bordered reduction from `B`. Decomposes the head term against the `color`-colored rules, applies `iterate-decomposition`, builds an alternate substitution via `alternate-subst`, and unifies the lifted reductions.
- **`iterate-decomposition`**: Lifts a closure of top-level reductions `A ~>* B` to a closure of monochrome reductions on the underlying linear-pattern decomposition `t`, producing a final term `u` such that the substitution applied to `u` equals `B`. Iterates `decompose-along-reduction` along the closure.
- **`alternate-subst`**: Given a linear pattern `t` decomposing the source and a bordered parallel reduction of its instance into `C`, produces a parallel-reduced substitution `sigma` whose application reproduces `C` componentwise. Recurses through the linear-term structure (`l-func`, `l-full-metavar`, `lt-var`) to align the bordered reduction with the pattern's metavariable holes.
- **`alternate-subst.unify-reduction`**: Helper that transports a bordered reduction across the trivial sort equality used by singular metacontexts, producing the correctly-typed reduction on the metavariable image.
- **`unify-left`**: Lifts a per-metavariable parallel reduction `rho => sigma` over a term `s` with sublist `sublist`, yielding a bordered parallel reduction between the substituted forms. Recurses through `var`, `metavar` (using `reduction-over-substitution`), and `func` (using `collect-reductions-together-raw`).
- **`unify-left.extend-substitutuion-left-for-parallel`**: Extends a per-index parallel reduction on substitutions through the `extend-substitution-left` plumbing used when descending under binders.
- **`reduction-over-substitution`**: Lifts a bordered parallel reduction `A => B` together with a parallel substitution refinement `subst => subst'` to a bordered parallel reduction on the substituted terms. Handles the `equal-trees` case via `distributed-reduction-for-substitution` and the `parallelization-f` case by recursing into mediators and re-collecting.
- **`expand-reduction-right`**: Extends a per-index bordered reduction on `subst => subst'` to operate on `append-context-right subst`, used when descending under a binder that introduces an additional context.
- **`tlcr-over-substitution`**: A single colored top-level reduction is preserved under substitution. Reuses linearity of patterns and a `commutation` lemma between weakening, metasubstitution, and substitution to reconcile the two ways of composing them.
- **`tlcr-over-substitution.untransport`**: Technical transport-stripping lemma used to relate the substituted forms of weakened pattern templates after rewriting their context equalities.
- **`tlcr-over-substitution.commutation`**: States that pulling a substitution through the metasubstitution of a weakened term commutes correctly. Body deferred (`hidden_proof`).
- **`tlcr-over-substitution.hcr-over-substitution`**: `HasColoredRoot` is preserved by substitution. Body deferred (`hidden_proof`).
- **`bpr-over-weakening`**: Lifts a `BorderedParallelReduction` along a context weakening, descending into mediators with `SubList.extend-right-both` and using `tlcr-over-weakening` for the border step.
- **`tlcr-over-weakening`**: Lifts a top-level colored reduction along a context weakening by reducing weakening to substitution via `weakening.substitution`.
- **`distributed-reduction-for-substitution`**: For a pure term `A` and parallel-refined substitutions `subst => subst'`, produces a bordered parallel reduction between the substituted forms. Recurses on the term structure: variables look up the per-index reduction; functions descend into arguments and reassemble.

#### Reduction Collection Combinators

- **`collect-reductions-together-tlcr`**: Given per-argument bordered reductions `arguments-A i => arguments-B i` and a switching reduction `func f arguments-B ~>* C`, packages them into a single bordered reduction `func f mediator => C` honoring the opposite-color discipline. Performs an inductive walk over the arguments using `propagate-rewriting` to thread the head reduction through each rewriting position.
- **`collect-reductions-together-tlcr.propagate-rewriting`**: Inductive step that combines a previous switching reduction at index `delim` with a new one at the next index, splitting on whether each is `cr-skip` or `cr-rewrite` and using `upgrade-iterated-reduction` when an inner-color rewrite must be lifted to a top-level one.
- **`collect-reductions-together-tlcr.upgrade-iterated-reduction`**: When a sub-argument reduction has color `f.1`, lifts a closure of those reductions to a closure of top-level rewrites at the function head by invoking `rewrite-with-parameter-f-colored` at each step, threading through `pointed-function` plumbing.
- **`collect-reductions-together-tlcr.insert-term`**: Builds `func f` with a `pointed-function` substituting one argument for a fresh point; used to package single-argument rewrites at the head.
- **`collect-reductions-together-tlcr.unfold-bpr`**: Splits a `BorderedParallelReduction A => B` into a strictly-inner part landing at `midterm` and a top-level switching-reduction tail, with the inner part guaranteed `OppositeColored required-color`. Decides via `decideEq` whether to absorb or expose the border step.
- **`collect-reductions-together-raw`**: Specialization of `collect-reductions-together-tlcr` with no extra trailing reduction, packaging only the per-argument bordered reductions into a single bordered step ending exactly at `func f arguments-B`.
