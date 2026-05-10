### Logic.Rewriting.TRS.Union.TopLevel

Top-level confluence machinery for the disjoint union of two higher-order rewrite systems, lifting monochrome confluence to the union via colored top-level reductions.

This module implements the key step in the modular confluence proof for the union of TRSs: any term `A` reducing in two ways at the top color-layer can be decomposed into a *linear pattern* over `(envs color)` plus an outer substitution whose metavariable images are rooted in the *opposite* color. Rewrites at the top color are reflected back into the inner monochrome system through `decompose-along-reduction`, joined by the assumed monochrome confluence, and re-injected into the union via `inject-term` and `MetaSubstitution.apply`. The numerous commutation lemmas (`injection-over-substitution`, `metacommutation`, `colored-metacomposition`, etc.) are bookkeeping for the interaction between term injection, weakening, substitution, and meta-substitution that this back-and-forth requires.

#### Main Confluence Lifting

- **`join-tlcrs`**: Given monochrome confluence for `color`, joins two top-level colored reduction sequences `A ~> B` and `A ~> C` into a common reduct `X` with `B ~> X` and `C ~> X`. Works by decomposing `A` along the color boundary, lifting each reduction into the monochrome system, applying confluence there, and re-injecting the joining sequences back into the union.

#### Decomposition

- **`decompose-term`**: Splits a pure term `A` at the color boundary, producing a linear pattern `t : LinearTerm (envs color)`, an outer meta-substitution into the union whose images all have an *excluded* colored root (i.e. are headed by the opposite color or are variables), and a proof that applying the substitution to `t` yields `A`. Recurses through function symbols of matching color and uses `ModularMetaContext` to merge the per-argument linear contexts.
- **`decompose-along-reduction`**: Given the decomposition of `A` and a top-level colored reduction `A ~> B`, produces a corresponding monochrome reduction `t ~> u` in `(envs color)` together with a `FunctionalWitness` and a proof that applying the outer substitution to the injection of `u` is `B`. Handles the rule case via `decompose-metasubstitution` and the parameter-rewriting case by recursion; metavariable and variable cases are ruled out by color analysis.
- **`decompose-metasubstitution`**: Given a linear pattern `l` and a term `t` such that `l[big-substitution] = inject(t)[middle-substitution]`, produces an inner meta-substitution into `inner-mc` that explains the pattern's metavariables in terms of the inner term, and proves it composes correctly with `middle-substitution`. The function-symbol case recurses; the full-metavariable case takes `t` itself as the image; the metavariable-of-`t` case is ruled out by color.

#### Lifting Monochrome Reductions

- **`lift-relation`**: Lifts a monochrome reduction `t ~> u` in `(envs color)` (with a `FunctionalWitness`) to a `TopLevelColoredReduction color` between the union-injected, outer-substituted versions of `t` and `u`. Splits on whether the rewrite is a rule application or a parameter rewrite.
- **`produce-monochrome-reduction`**: Packages a rule index, an inner substitution, and the appropriate left/right equalities into a `monochrome-reduction color l r` via `rewrite-with-rule`.

#### Color Analysis

- **`lift-relation.produce-colored-root`**: Extracts a `HasColoredRoot color` witness from a function-rooted linear pattern that has been substituted and injected. Used to identify the color of a redex root.
- **`decompose-along-reduction.eliminate-colors`**: Derives `Empty` from a term that simultaneously has an excluded colored root and, after a substitution, a colored root of the same color — the central contradiction used to rule out metavariable/variable cases in decomposition.
- **`decompose-along-reduction.unifying-lemma`**: Transports a `FunctionalWitness` along an equality of right-hand sides of a rewrite relation.

#### Commutation Lemmas (Injection / Substitution / Weakening)

- **`injection-weakening-commutation`**: `weakening (inject-term t) sl = inject-term (weakening t sl)` — injection commutes with weakening.
- **`colored-metacomposition`**: Composes two colored meta-substitutions through `inject-term`: applying a combined substitution equals applying the inner one in `(envs color)` and then the outer one in the union.
- **`colored-metacomposition.extend-left-commutation`**: Pushing a meta-substitution under `extend-substitution-left` for an injected substitution.
- **`colored-metacomposition.commutation`**: Substitution and meta-substitution commute on terms over a shared meta-context.
- **`colored-metacomposition.commutation.append-context-right-commutation`**: Meta-substitution distributes over `append-context-right`.
- **`colored-metacomposition.injection-over-substitution`** (and `.push-append-context-right`, `.push-append-context-right-ext`): Injection commutes with substitution and with `append-context-right`.
- **`lift-relation.injection-over-metasubstitution`** (and `.injection-over-left-extension`, `.injection-over-right-extension`, `.injection-over-substitution`): Injection commutes with meta-substitution and with the substitution context-extension operations.
- **`lift-relation.metacommutation`**: Composition law for meta-substitutions: applying `msubst` then `msubst2` to a term equals applying their composition in one step.
- **`lift-relation.subst-metasubst-comm`**, **`lift-relation.append-context-right-inside-metaapply`**, **`lift-relation.subst-extend-left-comm`**: Auxiliary commutations for substitution under meta-substitution, including its interaction with `append-context-right` and `extend-substitution-left`.

#### Transport / Weakening Bookkeeping

- **`weakening-extension-ext`**: Extending the right-projecting variable substitution by the identity on the left equals the plain identity meta-substitution.
- **`expand-substitution-3`**: Rewrites a meta-substitution-and-weakening equation that holds modulo a context transport on the right-hand side into one with the transport pushed onto the sublists on the left.
- **`decompose-along-reduction.different-weakening`** (and its `.lemma`): Equate two weakenings of a closed term into a context, modulo a `++_nil` transport.
- **`decompose-along-reduction.metasubst-over-transport`**, **`.metasubst-over-transport-2`**: Push `transport` along context-equalities through `MetaSubstitution.apply`.
- **`decompose-metasubstitution.ad-hoc-lemma`**: Transport-coherent reformulation of a meta-substitution-equality used inside the recursive decomposition step.
- **`decompose-metasubstitution.unfunc`**: Returns the head symbol of a `func`-rooted term, used as a small projection to compare function symbols under `pmap`.
