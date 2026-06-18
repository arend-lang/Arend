### Topology.MetricSpace.Compact

Compactness properties for metric spaces, specifically the preservation of total boundedness under products.

This module connects metric space structure to the abstract notion of compactness from `Topology.Compact`. It establishes that the property of having totally bounded balls (a quantitative compactness condition) is preserved when forming Manhattan products of extended pseudometric spaces, which is the natural product structure for metric spaces in this library.

#### Product Compactness

- **`product-balls-tb`**: Given two extended pseudometric spaces `X` and `Y` whose balls are totally bounded (`BallsTotallyBounded X` and `BallsTotallyBounded Y`), the Manhattan product `ManhattanProductPseudoMetricSpace X Y` also has totally bounded balls. This shows that finite products of "locally compact" metric spaces remain locally compact in the relevant sense.
