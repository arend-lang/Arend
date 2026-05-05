### Topology.NormedAbGroup.Real.Functions

Continuity and uniform-continuity lemmas for fundamental real-valued operations (norm, distance, lattice operations, addition, multiplication) on normed abelian groups, metric spaces, and (extended) upper reals.

#### Norm and Distance Maps

- **`lnorm-metric`**: For a `PseudoNormedAbGroup` `X`, the norm `X.norm : X -> RealNormed` is a `MetricMap` (1-Lipschitz into the reals).
- **`norm-metric-map`**: For an `ExPseudoNormedAbGroup` `X`, the extended norm `norm : X -> ExUpperRealMetric` is a `MetricMap`.
- **`bnorm-metric-map`**: For a `BoundedExPseudoNormedAbGroup` `X`, the bounded norm `bnorm : X -> UpperRealMetric` is a `MetricMap`.
- **`dist-uniform-map`**: For an `ExPseudoMetricSpace` `X`, the distance function `(s.1, s.2) ↦ dist s.1 s.2` is a `UniformMap` from `X ⨯ X` to `ExUpperRealMetric`.
- **`ldist-uniform-map`**: For a `PseudoMetricSpace` `X`, the distance function is a `UniformMap` from `X ⨯ X` to `RealNormed`.

#### Lattice Operations

- **`upper-meet-uniform`**: Meet `∧` is a `UniformMap` on `ExUpperRealMetric ⨯ ExUpperRealMetric`.
- **`real-meet-uniform`**: Meet `∧` is a `UniformMap` on `RealNormed ⨯ RealNormed`.
- **`upper-join-uniform`**: Join `∨` is a `UniformMap` on `ExUpperRealMetric ⨯ ExUpperRealMetric`.
- **`real-join-uniform`**: Join `∨` is a `UniformMap` on `RealNormed ⨯ RealNormed`.

#### Addition and Multiplication on (Extended) Upper Reals

- **`upper-+-uniform`**: Addition is a `MetricMap` from the Manhattan product of two `ExUpperRealMetric` factors to `ExUpperRealMetric`.
- **`upper-*-left-uniform`**: For a bounded `a : ExUpperReal`, left multiplication `(a *)` is a `UniformMetricMap` on `ExUpperRealMetric`.
- **`upper-*-right-uniform`**: For a bounded `a : ExUpperReal`, right multiplication `(* a)` is a `UniformMetricMap` on `ExUpperRealMetric`.
- **`upper-*-locally-uniform`**: Multiplication on `UpperRealMetric ⨯ UpperRealMetric` (Manhattan product) is a `LocallyUniformMap` to `UpperRealMetric`.
- **`ex-upper-*-locally-uniform`**: Extended-upper-real multiplication `s.1 ExUpperReal.* s.2` is a `LocallyUniformMap` from the Manhattan product of `UpperRealMetric` factors to `ExUpperRealMetric`.
