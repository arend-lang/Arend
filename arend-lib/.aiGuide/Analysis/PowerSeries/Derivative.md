### Analysis.PowerSeries.Derivative

The termwise derivative of a power series has the same radius of convergence as the series.

The module contains a single result, `derivative-convRadius`, about the radius of convergence `convRadius` of the coefficient sequence `deriv cs` of the formal derivative of a power series with coefficients in a real pre-Banach algebra.

#### Radius of convergence

- **`derivative-convRadius`**: In a `RealPreBanachAlgebra`, `convRadius (deriv cs) = convRadius cs`: differentiating termwise does not change the radius of convergence.
