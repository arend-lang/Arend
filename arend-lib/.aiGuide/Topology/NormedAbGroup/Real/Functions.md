### Topology.NormedAbGroup.Real.Functions

Continuity properties of norm, distance, lattice, and arithmetic operations on (extended) real-valued metric and normed structures.

This module collects the basic uniform/metric/locally-uniform continuity lemmas needed to lift pointwise real-valued operations to maps between metric and normed spaces. The norm and distance functions are shown to be (uniform) metric maps into the real line or the extended upper-real metric space, which is essential for working with completions and continuous extensions. Lattice operations (`∧`, `∨`) and arithmetic (`+`, `*`) on (extended) upper reals are established as uniform or locally uniform maps over Manhattan-product metrics, providing the building blocks for continuous real analysis on top of `Topology.CoverSpace.Complete` and the metric-space hierarchy.

#### Norm and Distance Maps

- **`lnorm-metric`**: For a `PseudoNormedAbGroup` `X`, the norm `X.norm : X -> RealNormed` is a `MetricMap` — distances in `X` dominate distances of norms.
- **`norm-metric-map`**: For an `ExPseudoNormedAbGroup` `X`, the norm into `ExUpperRealMetric` is a `MetricMap`, the extended-real version of `lnorm-metric`.
- **`bnorm-metric-map`**: For a `BoundedExPseudoNormedAbGroup` `X`, the bounded norm `bnorm` is a `MetricMap` into `UpperRealMetric`.
- **`dist-uniform-map`**: The distance function `\lam s => dist s.1 s.2` from `X ⨯ X` to `ExUpperRealMetric` is a `UniformMap` for any `ExPseudoMetricSpace` `X`.
- **`ldist-uniform-map`**: The (real-valued) distance function on a `PseudoMetricSpace` is a `UniformMap` from `X ⨯ X` to `RealNormed`.

#### Lattice Operations

- **`upper-meet-uniform`**: Binary meet `∧` on `ExUpperRealMetric` is a `UniformMap` from the product to `ExUpperRealMetric`.
- **`real-meet-uniform`**: Binary meet `∧` on `RealNormed` is a `UniformMap`.
- **`upper-join-uniform`**: Binary join `∨` on `ExUpperRealMetric` is a `UniformMap`.
- **`real-join-uniform`**: Binary join `∨` on `RealNormed` is a `UniformMap`.

#### Arithmetic Operations

- **`upper-+-uniform`**: Addition on `ExUpperRealMetric` is a `MetricMap` from the Manhattan product to `ExUpperRealMetric` (1-Lipschitz with the ℓ¹ metric).
- **`upper-*-left-uniform`**: Left multiplication `a *` by a bounded extended upper real `a` (with hypothesis `a.IsBounded`) is a `UniformMetricMap` on `ExUpperRealMetric`.
- **`upper-*-right-uniform`**: Right multiplication `* a` by a bounded `a` is a `UniformMetricMap` on `ExUpperRealMetric`.
- **`upper-*-locally-uniform`**: Multiplication on `UpperRealMetric` is a `LocallyUniformMap` from the Manhattan product to `UpperRealMetric`; uniform continuity holds locally because boundedness of factors is needed.
- **`ex-upper-*-locally-uniform`**: The extended-upper-real multiplication `ExUpperReal.*` is a `LocallyUniformMap` from `UpperRealMetric ⨯ UpperRealMetric` (Manhattan) into `ExUpperRealMetric`.
