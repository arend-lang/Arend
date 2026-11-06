### Topology.MetricSpace.ExComplete

Completion of extended pseudo-metric spaces and the universal property for extending metric maps.

This module constructs the metric completion of an extended pseudo-metric space as a complete extended metric space, building on the underlying uniform-space completion via regular Cauchy filters. The distance between two completion points (filters `F`, `G`) is defined as the upper-real whose rational over-approximations are witnessed by sets `U ∈ F`, `V ∈ G` on which the original distance lies below the bound. The dense isometric embedding of `X` into its completion together with the lifting lemma provides the universal property: any metric map from `X` to a complete extended metric space factors uniquely through the completion.

#### Lifting Metric Maps

- **`dense-metric-lift`**: Given an isometric map `f : X → Y` with dense image and a metric map `g : X → Z` into a complete extended metric space, produces the unique extension `Y → Z` as a `MetricMap`. Builds on `dense-uniform-lift` by upgrading the resulting uniform map with the metric (distance-preserving) condition. Used to factor metric maps through completions and other dense isometric embeddings.

#### Metric Completion

- **`ExMetricCompletion`**: The completion of an `ExPseudoMetricSpace` `X` as a `CompleteExMetricSpace`, extending `UniformCompletion X`. Points are regular Cauchy filters; the distance between filters `F` and `G` is the extended upper real whose rational upper bounds `q` are precisely those for which some `r < q` and sets `U ∈ F`, `V ∈ G` exist with `dist(x, y) < r` for all `x ∈ U`, `y ∈ V`. The upper-real structure (`U-closed`, `U-rounded`) follows from monotonicity and density of rationals.

#### Embedding into the Completion

- **`completion-ex-isometry`**: The canonical map `X → ExMetricCompletion X` as an `IsometricMap`, exhibiting `X` as an isometrically embedded (and dense) subspace of its completion. Wraps the underlying `uniform-completion` with the isometry property, and is the map used as `f` when applying `dense-metric-lift` to extend metric maps out of `X`.
