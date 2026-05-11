### Logic.Rewriting.TRS.Substitutions

Substitutions, weakenings, and context manipulations for higher-order rewriting systems (HRS).

This module develops the metatheory of substitution operations on `Term env context sort mc` from `Logic.Rewriting.TRS.HRS`. The two central operations are `weakening` (renaming a term into a larger context via a `SubList`) and `append-context-right` (extending a substitution to act on terms whose context has been padded with additional sorts, needed when descending under binders represented by `func` arguments). Most of the file consists of compatibility lemmas: weakening composes with itself and with substitution, substitutions commute past weakenings, and `append-context-right` distributes over substitution composition. These lemmas are the gluing infrastructure that makes the recursive `Substitution.apply` and `subst-comm` work cleanly across the binder-extending recursive case of `func`.

#### Identity Substitution

- **`plain-identity`**: The identity substitution `\lam i => var i idp : Substitution context context mc`.
- **`plain-identity-effect`**: Applying `plain-identity` to a term yields the term unchanged.

#### Weakening

- **`weakening`**: Renames a term `Term env narrow-context s mc` into `Term env wide-context s mc` along a `SubList narrow-context wide-context`, recursively shifting variable indices via `shift-index` and extending the sublist under `func` arguments.
- **`weakening.over-transport-sort`**: `weakening` commutes with transport in the sort argument.
- **`weakening.over-transport-ctx`**: `weakening` commutes with transport in the context argument.
- **`weakening.over-metasubstitution`**: `weakening` commutes with metavariable substitution.
- **`weakening.composition`**: Two weakenings compose into a weakening along the composed `SubList`. Includes the auxiliary `lemma` showing `shift-index` is functorial in `SubList.compose`.
- **`weakening.substitution`**: Reinterprets a `SubList` as a `Substitution narrow-context wide-context` via `\lam i => weakening (var i idp) sublist`.
- **`weakening.substitution-eq`**: Weakening a term equals applying its associated weakening-substitution; the bridge between the two views.
- **`weakening.combine-with-append-right`**: Applying `append-context-right subst` after weakening on the right equals weakening the substitution result.
- **`weakening.combine-with-append-left`**: Variant of `combine-with-append-right` for left-extension weakenings.
- **`weakening.combine-with-extend-right`** / **`weakening.combine-with-extend-left`**: Compatibility of weakening with `extend-substitution-left`.
- **`weakening.over-identity`**: Weakening along `SubList.identity` is the identity on terms.

#### Variable and Transport Helpers

- **`transport-var-over-sort`**: Transport in the sort argument distributes through `var`.
- **`sigma-to-var`**: Builds a `var` from a `(index, sort-equality)` sigma.
- **`var-extensionality`**: Two `var` constructors with equal indices are equal regardless of their proof of sort-equality (uses `prop-pi`).
- **`subst-to-var`**: Computes `Substitution.apply (var i eq) subst` as a transported lookup `subst i`.

#### Extending Substitutions over Larger Contexts

- **`append-context-right`**: Given `subst : Substitution old-context new-context mc` and an additional context `additional-context`, produces a substitution acting on `old-context ++ additional-context`, sending old-context indices through `subst` (right-weakened) and new indices to fresh variables (left-weakened). This is the core operation used when recursing into `func` subterms.
- **`append-context-right.on-begin`**: Behavior of `append-context-right` on the original portion of the index space (`expand-fin-left`).
- **`append-context-right.on-end`**: Behavior on the appended portion (`expand-fin-right`); yields a left-weakened fresh variable.
- **`append-context-right.to-identity`**: `append-context-right plain-identity = plain-identity`.
- **`append-context-right.composition`**: Iterating `append-context-right` over two added contexts agrees, via `++-assoc`-transport, with adding their concatenation in one step. Bundled with extensive transport-pushing lemmas (`end-over-transport`, `end-over-transport-2`, `end-over-transport-3`, `transport-unifier`, `push-transport-into-var`, `decompose-transport2`) needed to align the dependent indices.
- **`append-context-right.to-nil`**: Any substitution equals its `append-context-right` extension by `nil`, modulo `++_nil` transports. Includes helper lemmas `move-under-transport`, `transport-move`, `append-context-right-commute-with-transport`, `unexpand-fin`, `indexing-exteded-list` for navigating the transports.

- **`extend-substitution-left`**: Given `sublist : SubList left-context some-context` and `chooser : Substitution right-context some-context mc`, builds a substitution on `left-context ++ right-context` that maps left indices to weakened variables (via `sublist`) and right indices to `chooser`.
- **`extend-substitution-left.on-begin`** / **`extend-substitution-left.on-end`**: Characterize the behavior on the two halves of the concatenated index.

#### Substitution Composition

- **`subst-comm`**: The fundamental composition law: `Substitution.apply (Substitution.apply t subst-a) subst-b = Substitution.apply t (\lam i => Substitution.apply (subst-a i) subst-b)`. The `func` case relies on `distribute-append-context-right`.
- **`subst-comm.distribute-append-context-right`**: `append-context-right` distributes over substitution composition: composing `append-context-right subst-a` with `append-context-right subst-b` agrees with `append-context-right` of the composed substitution.
