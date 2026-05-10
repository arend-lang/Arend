### Logic.Rewriting.TRS.Union.Embedding

Bridges the native rewrite relation of a TRS union with the trichromatic parallel reduction used to prove confluence.

This module provides the embedding/unembedding machinery that connects two views of reduction in a colored union of term rewriting systems: the direct `RewriteRelation` over the joint rule registry `JRegistry`, and the structured `TrichromaticParallelReduction` (with bordered/switching/top-level layers) used by the confluence proof. The main result `fill-confluence` closes the diamond for the union by lifting two reduction sequences `A ~> B` and `A ~> C` into the parallel-reduction world, applying the per-color confluence assumption to fill the diamond, and unembedding the resulting joins back into ordinary closure-of-rewrite sequences. The internal helpers handle the case analysis on whether a redex sits at a function root (and so picks up a color) or recurses into an argument position.

#### Main Confluence Filler

- **`fill-confluence`**: Given `A ~> B` and `A ~> C` as `Closure (RewriteRelation JRegistry)` and per-color confluence (`ConfluentialSystem (envs c) (rules c)`), produces a `StraightJoin B C` — i.e. a common reduct `D` with reductions `B ~> D` and `C ~> D` — by lifting to `TrichromaticParallelReduction`, invoking `bpr-confluence`, and unembedding the resulting joins.

#### Generic Closure Unification (`Internal.unify-reduction`)

- **`unify-reduction`**: Diamond-completion at the level of `Closure Rel`: given `x ~> y` and `x ~> z` and a one-step unifier closing the diamond for `Rel`, produces `w`, `y ~> w`, `z ~> w`. Recurses on the structure of `x->y` (`c-trivial`, `c-basic`, `c-connect`).
- **`unify-reduction.unify-line`**: Helper specializing the first reduction to a single `Rel` step, recursing on `x->z`. Used to drive `unify-reduction`'s case for non-trivial first segments.

#### Embedding the Rewrite Relation

- **`embed-rewrite-relation`**: Converts a single `RewriteRelation JRegistry A B` step into a `TrichromaticParallelReduction A B`. Cases:
  - On a root rewrite at a `func` term, uses `coloring-lemma` to identify the rule's color and packages the step as a colored top-level reduction wrapped in a parallelization.
  - On a parameter rewrite (`rewrite-with-parameter-f`), recurses into the argument and reassembles via `collect-reductions-together-raw` and `parallelization-f`, leaving non-rewritten arguments as `equal-trees`.
  - The variable-headed root case is impossible (closed by `contradicting`).
- **`embed-rewrite-relation.contradicting`**: Rules out a root redex headed by a variable: a meta-substitution applied to a function-rooted rule LHS cannot equal `var index p`.
- **`embed-rewrite-relation.coloring-lemma`**: From a substitution that turns the (injected) linear pattern of a color-`c` rule into `func f arguments`, concludes `f.1 = c` — i.e. the head symbol's color matches the rule's color.

#### Unembedding Back to Plain Closures

- **`unembed-rewrite-relation`**: Converts a `BorderedParallelReduction gc color A B` into `Closure (RewriteRelation JRegistry) A B`. For `parallelization-f`, performs a modular induction over the argument vector (using `modular-function`, `modular-to-pointed`, and `pointed-function` lemmas) to chain per-argument reductions through `rewrite-with-parameter-f`, then composes with the unembedded switching reduction at the head.
- **`unwrap-cwr`**: Unembeds a `SwitchingReduction gc color A B`: the `cr-skip` case is reflexivity, and the `cr-rewrite` case lifts a `TopLevelColoredReduction` step-by-step through `unembed-tlcr`.
- **`unembed-tlcr`**: Unembeds a `TopLevelColoredReduction color A B` to a single `RewriteRelation JRegistry` step. Translates a colored root-rewrite `rewrite-with-rule-colored` into `rewrite-with-rule` with the color-tagged rule index, and recurses on `rewrite-with-parameter-f-colored`.
