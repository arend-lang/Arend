### Topology.CoverSpace.RelativelyComplete

Relative completion of cover spaces: factoring a map through a dense embedding into a relatively complete and relatively Hausdorff map, generalizing the absolute completion construction.

#### Relative Completeness

- **`IsRelativelyComplete`**: Property of `p : X -> Y` stating that for every Cauchy filter `F` on `X` whose image filter contains the point filter of `y : Y`, there exists `x : X` with `p x = y` whose point filter is contained in `F`.
- **`IsRelativelyComplete.dense-complete`**: Reduction lemma — to prove `p` is relatively complete, it suffices to verify the lifting property for regular Cauchy filters arising from a dense embedding `f`.

#### Lifting Along Dense Embeddings

- **`relativelyCompleteAndSeparated`**: Combines relative completeness and relative Hausdorffness: for `p` satisfying both, the lift `(x, p x = y, pointCF x ⊆ F)` is contractible (uniquely determined).
- **`relativelyCompleteAndSeparated.neighborhood`**: Neighborhood control for the unique lift — if `V <=< U` and `F V`, then the lifted point's singleton is rather-below `U`.
- **`relativelyCompleteAndSeparated.filter-lift`**: Constructs the Cauchy filter on `X` used to lift a point `y : Y` along a dense embedding.
- **`relativelyCompleteAndSeparated.lift-contr`**: Given a square `g ∘ i = p ∘ f` with `i` a dense embedding, `p` relatively complete and Hausdorff, produces the unique lift `z : Z` for each `y : Y` with `p z = g y`.

#### Universal Lifting Maps

- **`dense-cauchy-relative-lift`**: Lifts a Cauchy map `f : CauchyMap X Z` along a dense embedding `i : X -> Y` to a Cauchy map `Y -> Z` whenever `g ∘ i = p ∘ f` and `p` is relatively complete and Hausdorff.
- **`dense-relative-lift`**: Same lift as above, but producing a `CoverMap` when the input `f : CoverMap X Z`.
- **`dense-relative-lift-proj`**: Compatibility with `p`: the lift composed with `p` reproduces `g`, i.e. `p (lift y) = g y`.
- **`dense-relative-lift-char`**: Compatibility with `i`: the lift restricted along `i` reproduces `f`, i.e. `lift (i x) = f x`.

#### Relative Completion Construction

- **`RelativeCompletion`**: Type of triples `(F, y, pointCF y ⊆ SetFilter-map f F)` where `F` is a regular Cauchy filter on `X` and `y : Y` is a target point compatible with `F` via `f`.
- **`RelativeCompletion.inc`**: Forgetful map `RelativeCompletion f -> RegularCauchyFilter X × Y`.
- **`RelativeCompletionCoverSpace`**: Cover space structure on `RelativeCompletion f`, transferred via `inc` from the product of the absolute completion of `X` and `Y`.
- **`relativeCompletion`**: The canonical cover map `X -> RelativeCompletionCoverSpace f` sending `x` to `(pointCF x, f x, ...)`.
- **`relativeCompletion.toCompletion`**: Projection from the relative completion to the absolute completion of `X`.
- **`relativeCompletion.isDenseEmbedding`**: The map `relativeCompletion` is a dense embedding.
- **`relativeCompletion-proj`**: Projection `RelativeCompletionCoverSpace f -> Y` extracting the second coordinate.
- **`relativeCompletion-proj.isHausdorff`**: The projection is relatively Hausdorff.
- **`relativeCompletion-proj.isCompletion`**: The projection is relatively complete.

#### Orthogonal Factorization System

- **`CompletionOFS`**: Orthogonal factorization system on `CoverSpaceCat` whose left class consists of dense embeddings and whose right class consists of maps that are both relatively Hausdorff and relatively complete; every map factors as `relativeCompletion h` followed by `relativeCompletion-proj h`.
