### Topology.MetricSpace.ManhattanProduct

Equips the product of two pseudo-metric spaces with the Manhattan (L¹) distance.

This module provides the product construction for extended pseudo-metric spaces using the sum of component distances, also known as the L¹ or taxicab metric. The underlying uniform structure is inherited from `ProductUniformSpace`, ensuring that the Manhattan metric is compatible with the canonical product uniformity. This is one of several equivalent ways to metrize a finite product of metric spaces (alongside the sup and Euclidean metrics), and the L¹ choice is often most convenient for additive estimates and triangle-inequality reasoning.

#### Product Pseudo-Metric Instance

- **`ManhattanProductPseudoMetricSpace`**: Instance making `\Sigma X Y` an `ExPseudoMetricSpace` for any two extended pseudo-metric spaces `X Y`. The distance is defined componentwise as `dist s t = dist s.1 t.1 + dist s.2 t.2`, and the uniform structure is the product uniformity from `ProductUniformSpace X Y`. Reflexivity, symmetry, and the triangle inequality follow pointwise from the corresponding properties of `X` and `Y`, and `dist-uniform` shows that this metric induces the product uniformity.
