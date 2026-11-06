### Topology.Elem

Subspace structures: equipping a subset `S ⊆ X` with the induced topological, cover, uniform, or metric structure inherited from `X`.

This module systematically transfers each topological structure on `X` to its subset type `Elem S` via the inclusion map `s ↦ s.1`. The construction relies on generic transfer instances (`TopTransfer`, `CoverTransfer`, `UniformTransfer`, `MetricTransfer`) that pull back the structure along an arbitrary function. For metric spaces, the injectivity of inclusion is supplied as the proof obligation (via `ext`), ensuring the induced pseudometric is genuinely a metric.

#### Subspace Structures

- **`ElemTopSpace`**: Topology on `Elem S` induced from `X`, defined as `TopTransfer` along the first projection.
- **`ElemCoverSpace`**: Cover space structure on `Elem S` induced from a cover space `X` via `CoverTransfer`.
- **`ElemUniformSpace`**: Uniform space structure on `Elem S` induced from a uniform space `X` via `UniformTransfer`.
- **`ElemMetricSpace`**: Metric space structure on `Elem S` induced from a metric space `X` via `MetricTransfer`, with injectivity of inclusion supplied by `ext` on subset elements.

#### Continuity

- **`elemCont`**: The inclusion map `Elem S → X`, `s ↦ s.1`, is continuous with respect to the induced topology on `Elem S`.
