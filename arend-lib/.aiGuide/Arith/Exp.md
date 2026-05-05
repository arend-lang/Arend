### Arith.Exp

This module defines the exponential function for real Banach algebras via power series.

#### Exponential Function

- **`exp`**: `CoverMap A A` for any `RealBanachAlgebra A`. Defined as the limit of partial sums of the power series `∑ x^n / n!`, i.e., `exp(x) = lim_{n→∞} ∑_{k=0}^{n} x^k / k!`. Constructed via `funcLimit` and `powerSeriesConv-funcConv`.
  - **`exp-series-conv`**: Proof that the power series with coefficients `1/n!` is convergent (`IsPowerSeriesConv`).
  - **`inv-limit`**: Proof that the sequence `1/(n+1)` converges to `0` (`RealNormed.IsLimit`).
