### Topology.CoverSpace.RelativelyComplete

Relative completion of cover spaces: lifting cauchy/cover maps along a base map and the orthogonal factorization system of dense embeddings vs. relatively-Hausdorff-and-complete maps.

This module generalizes the absolute completion of a cover space to a *relative* setting, where one works fiberwise over a base map `p : X -> Y`. A map is "relatively complete" if every cauchy filter on `X` whose pushforward refines the point filter at `y : Y` lifts to a unique point in the fiber over `y`. Combined with relative Hausdorffness, this yields a contractible lift, which powers the universal property of `RelativeCompletion f` — the space of regular cauchy filters on `X` paired with their limits in `Y`. The whole construction assembles into an orthogonal factorization system on `CoverSpaceCat` whose left class is dense embeddings and whose right class is relatively Hausdorff complete maps.

#### Relative Completeness

- **`IsRelativelyComplete`**: A map `p : X -> Y` is relatively complete if every cauchy filter `F` on `X` whose image refines `pointCF y` lifts to a point `x` with `p x = y` and `pointCF x ⊆ F`.
- **`IsRelativelyComplete.dense-complete`**: Reduces relative completeness for a cauchy map `p` to the case of regular cauchy filters along a dense embedding `f`.

#### Relatively Complete + Hausdorff

- **`relativelyCompleteAndSeparated`**: Combining relative completeness and relative Hausdorffness, the lift `Σ (x : X) (p x = y) (pointCF x ⊆ F)` is contractible.
- **`relativelyCompleteAndSeparated.neighborhood`**: A neighborhood lemma: if `V <=< U` and `F V`, then the singleton of the unique lift is rather-below `U`.
- **`relativelyCompleteAndSeparated.filter-lift`**: Constructs a cauchy filter on `Z` from a cauchy map `f : X -> Z` and a dense embedding `i : X -> Y`, evaluated at a point `y : Y`.
- **`relativelyCompleteAndSeparated.lift-contr`**: Given a square `g ∘ i = p ∘ f` with `i` a dense embedding, produces the canonical lift `(z, p z = g y, pointCF z ⊆ filter-lift)` from contractibility.

#### Lifting Maps Along Dense Embeddings

- **`dense-cauchy-relative-lift`**: Lifts a cauchy map `f : X -> Z` along a dense embedding `i : X -> Y` and a square `g ∘ i = p ∘ f` to a cauchy map `Y -> Z`, when `p` is relatively complete and Hausdorff.
- **`dense-relative-lift`**: Same lift, but produces a `CoverMap` when `f` is a cover map.
- **`dense-relative-lift-proj`**: The lift commutes with projection: `p (lift y) = g y`.
- **`dense-relative-lift-char`**: The lift extends `f`: composing with `i` recovers `f`, i.e. `lift (i x) = f x`.

#### The Relative Completion

- **`RelativeCompletion`**: The relative completion of `f : X -> Y` is the type of triples `(F, y, pointCF y ⊆ SetFilter-map f F)` with `F` a regular cauchy filter on `X`.
- **`RelativeCompletion.inc`**: Forgetful map to `RegularCauchyFilter X × Y`.
- **`RelativeCompletionCoverSpace`**: Cover space structure on `RelativeCompletion f`, transferred along `inc` from the product of the completion of `X` and `Y`.
- **`relativeCompletion`**: The canonical cover map `X -> RelativeCompletion f`, sending `x` to `(pointCF x, f x, _)`.
- **`relativeCompletion.toCompletion`**: Projection `RelativeCompletion f -> Completion X` onto the filter component.
- **`relativeCompletion.isDenseEmbedding`**: The map `relativeCompletion` is a dense embedding.
- **`relativeCompletion-proj`**: The projection `RelativeCompletion f -> Y` onto the limit component.
- **`relativeCompletion-proj.isHausdorff`**: The projection is relatively Hausdorff.
- **`relativeCompletion-proj.isCompletion`**: The projection is relatively complete.

#### Factorization System

- **`CompletionOFS`**: The orthogonal factorization system on `CoverSpaceCat` whose left class `L` consists of dense embeddings and whose right class `R` consists of maps that are simultaneously relatively Hausdorff and relatively complete; any cover map `h` factors as `relativeCompletion h` followed by `relativeCompletion-proj h`.
