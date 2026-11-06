### Analysis.SeriesComposition

Convergence of the composition (substitution) of power series, with Tannery's theorem for interchanging a limit and a series.

The module defines `pow-conv b n`, the n-fold convolution power of a series, whose terms are the coefficients of `B(x)^n` where `B(x) = Σ b_m x^m`; it is the building block for substituting one power series into another. It proves that `pow-conv` preserves absolute convergence and sums to powers of the sum (`pow-conv-absConv`, `pow-conv-sum`, `pow-B-as-powerSeries`). Using a decomposition of the partial sums of the composite `fcompose a b` (`compose-partialSum-decomp`) and a version of Tannery's theorem on dominated convergence for series (`tannery-converges`), it shows that the composite series sums to `Σ a_n B^n` under several kinds of hypotheses (`compose-converges`, `compose-converges-radius`, `compose-converges-abs`). It relies on `conv` from `Analysis.CauchyProduct`.

#### Powers by convolution

- **`pow-conv`**: `pow-conv a n` is the n-th power of the formal series `a` for convolution; `pow-conv a 0` is the series `1, 0, 0, ...`.
- **`pow-conv-absConv`**: If `a` is absolutely convergent, so is `pow-conv a n`.
- **`pow-conv-sum`**: If `a` is absolutely convergent and sums to `sa`, then `pow-conv a n` sums to `pow sa n`.
- **`powerSeries-const-eq`**: The power series of a constant series `FSeriesRing.coef a` evaluated at any `x` is again `FSeriesRing.coef a`.
- **`pow-conv-powerSeries-absConv`**: For a central `x` and `powerSeries b x` absolutely convergent, the power series of `pow-conv b n` at `x` is absolutely convergent.
- **`pow-B-as-powerSeries`**: For a central `x`, if `powerSeries b x` is absolutely convergent with sum `B`, then the power series of `pow-conv b n` at `x` sums to `pow B n`.
- **`pow-conv-partialSum-M-zero`**: If `b 0 = 0`, then the first `M` terms of the power series of `pow-conv b M` at `x` sum to zero.

#### Tannery's theorem

- **`tannery-converges`**: Tannery's theorem for series in an `ExPseudoNormedRing`: if `F n N` is bounded in norm by a convergent upper-real series `D n`, and tends to `L n` as `N` grows, where `L` sums to `sL`, then `partialSum (F n N)` over `n < N` tends to `sL`.

#### Composition of power series

- **`compose-partialSum-decomp`**: If `b 0 = 0`, the N-th partial sum of the composite `fcompose a b k * x^k` equals `partialSum (\lam n => a n * partialSum (powerSeries (pow-conv b n) x) N) N`.
- **`compose-converges`**: For a central `x`, `b 0 = 0`, `powerSeries b x` absolutely convergent with sum `B`, `Σ a_n B^n` summing to `sA` and a summable dominating series `D`, the composite series sums to `sA`.
- **`compose-converges-radius`**: A version of `compose-converges` where the dominating series is replaced by convergence of `norm (a n) * r^n`, with the partial sums of the norms of `powerSeries b x` bounded by `r`.
- **`compose-converges-abs`**: A version of `compose-converges` assuming that `a` is a convergent power series (`IsPowerSeriesConv`), with no explicit dominating series.

#### Rational coefficients in real Banach algebras

- **`fromRat-pow-conv`**: In a `RealBanachAlgebra`, `fromRat` commutes with `pow-conv` on series of rationals.
- **`compose-coef-fromRat`**: In a `RealBanachAlgebra`, `fromRat` commutes with the coefficients of the composite series `fcompose a b` of series of rationals.
