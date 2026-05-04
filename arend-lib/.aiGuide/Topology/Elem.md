### Topology.Elem

Subspace structures on `Elem S` (the type of elements of a subset `S ⊆ X`) for various topological structures, obtained by transfer along the inclusion map.

#### Subspace Instances

- **`ElemTopSpace`**: Topological space structure on `Elem S` for `S : Set X` where `X` is a `TopSpace`, defined via `TopTransfer` along the projection `s ↦ s.1`.
- **`ElemCoverSpace`**: Cover space structure on `Elem S` induced from `X : CoverSpace` via `CoverTransfer`.
- **`ElemUniformSpace`**: Uniform space structure on `Elem S` induced from `X : UniformSpace` via `UniformTransfer`.
- **`ElemMetricSpace`**: Metric space structure on `Elem S` induced from `X : MetricSpace` via `MetricTransfer`, using the injectivity of the inclusion (`\lam p => ext p`) to verify the separation axiom.

#### Continuity

- **`elemCont`**: The inclusion map `Elem S → X` sending `s ↦ s.1` is continuous as a `ContMap` from `ElemTopSpace S` to `X`.
