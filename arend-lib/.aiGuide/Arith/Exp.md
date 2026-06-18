### Arith.Exp

The exponential function on real Banach algebras, defined as the limit of the standard power series.

This module constructs `exp : A → A` for any real Banach algebra `A` as the cover map obtained from the partial sums of the power series `Σ (1/n!) · xⁿ`. The construction uses `funcLimit` together with `powerSeriesConv-funcConv` to upgrade pointwise convergence of the power series into a continuous (cover) map. The convergence proof relies on bounding the ratios `1/(n+1)` going to zero, which is captured by the auxiliary `inv-limit` lemma.

#### Exponential Map

- **`exp`**: The exponential function on a `RealBanachAlgebra` `A`, packaged as a `CoverMap A A`. Defined as the function limit of the partial sums of the power series with coefficients `1/n!` (lifted from `Rat` to `A` via `A.fromRat`).

#### Convergence Lemmas

- **`exp-series-conv`**: Proof that the power series with coefficients `n ↦ A.fromRat (1/n!)` satisfies `IsPowerSeriesConv`, i.e. converges everywhere on `A`. This is what justifies defining `exp` as a function limit.
- **`inv-limit`**: The sequence `n ↦ 1/(n+1)` converges to `0` in `RealNormed`. Used as an auxiliary fact in establishing convergence of the exponential series via comparison.
