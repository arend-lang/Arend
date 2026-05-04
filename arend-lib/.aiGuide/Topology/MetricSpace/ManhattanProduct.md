### Topology.MetricSpace.ManhattanProduct

Equips the product of two extended pseudo-metric spaces with the Manhattan (L¹) distance, summing the component distances.

#### Product Pseudo-Metric Space

- **`ManhattanProductPseudoMetricSpace`**: Instance making `\Sigma X Y` an `ExPseudoMetricSpace` for `X Y : ExPseudoMetricSpace`. Inherits the underlying uniform structure from `ProductUniformSpace X Y` and defines `dist s t = dist s.1 t.1 + dist s.2 t.2`, the Manhattan/taxicab metric on the product. Use this when combining two pseudo-metric spaces additively (as opposed to a sup or Euclidean product).
