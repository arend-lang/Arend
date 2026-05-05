### Topology.MetricSpace.ExComplete

Completion of extended pseudo-metric spaces, providing a universal complete extended metric space and the canonical isometric embedding.

#### Universal Property

- **`dense-metric-lift`**: Given an isometric dense map `f : X -> Y` between extended pseudo-metric spaces and a metric map `g : X -> Z` into a complete extended metric space, lifts `g` uniquely to a `MetricMap Y Z`. Builds on `dense-uniform-lift` while preserving the distance structure.

#### Completion Construction

- **`ExMetricCompletion`**: The completion of an `ExPseudoMetricSpace X` as a `CompleteExMetricSpace`, extending `UniformCompletion X` with a distance function and the metric axioms (reflexivity, symmetry, triangle inequality, uniform compatibility).
- **`completion-ex-isometry`**: The canonical `IsometricMap` from `X` into its completion `ExMetricCompletion X`, extending `uniform-completion` with the isometry property.
