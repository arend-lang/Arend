### Analysis.Series

Infinite series in topological abelian groups: convergence, absolute convergence, partial sums, and standard convergence tests.

#### Series and Partial Sums

- **`Series`**: A series is a function `Nat -> A`.
- **`partialSum`**: Sum of the first `n` terms of a series in an `AddMonoid`.
- **`partialSum-empty`**: `partialSum S 0 = 0`.
- **`partialSum_<=`**: Monotonicity of partial sums in a `PosetAddMonoid`.
- **`partialSum_hom`**: Additive monoid homomorphisms commute with partial sums.

#### Convergence and Sum

- **`IsConvSeries`**: A series in a `TopAbGroup` is convergent iff its partial sums converge.
- **`IsSeriesSum`**: `l` is the sum of `S` iff `partialSum S` converges to `l` in the ambient topology.
- **`seriesSum-fin`**: A series that vanishes from index `n` onward sums to its finite partial sum.
- **`seriesSum-conv`**: Having a sum implies convergence.
- **`seriesSum`**: Partial function `Series A -> Partial A` returning the limit, for complete topological abelian groups.
- **`seriesConv-sum`**: For convergent series, `seriesSum` provides the actual sum.
- **`seriesSum-char`**: `seriesSum S = defined l` iff `IsSeriesSum S l`.
- **`seriesSum_defined`**: Cauchy-style criterion (in terms of `Rat`-bounded norm of `a - partialSum S n`) implying `seriesSum S = defined a`; its `.conv` converse extracts the criterion from a defined sum.

#### Mid Sums (Sums between two indices)

- **`midSum`**: Sum of terms `S n + S (n+1) + ... + S (m-1)` defined recursively.
- **`midSum'`**: Equivalent definition via `partialSum` of a shifted series.
- **`midSum_hom`**: Homomorphisms preserve `midSum`.
- **`midSum'_midSum`**, **`midSum_midSum'`**: Mutual conversion between the two definitions.
- **`midSum_suc`**: `midSum S n (suc n) = S n` (single-term case).
- **`midSum-diff`**: In an `AbGroup`, `midSum S n m = partialSum S m - partialSum S n`.
- **`midSum_norm`**: Triangle inequality for `midSum` under a normed abelian group.
- **`midSum_<=-ldistr`**: Left distributivity inequality for `midSum` over multiplication by a non-negative scalar; helper `BigSum_<=-ldistr` does the same for arrays.
- **`midSum-split`**: `midSum b n k = midSum b n m + midSum b m k` for `n <= m <= k`.
- **`midSum-degenerate`**: `midSum b n m = 0` when `m < n`.
- **`midSum_<=-left`**: Shrinking the left endpoint of a non-negative `midSum` only increases it.
- **`midSum_<=`**: Pointwise comparison of `midSum`s.
- **`midSum_0`**: `midSum` of the zero series is `0`.
- **`midSum>=0`**: `midSum` of a non-negative series is non-negative.

#### Upper-Real and Absolute Convergence

- **`IsConvUpperSeries`**: Cauchy criterion for series in `ExUpperReal`: tail `midSum`s become arbitrarily small (in `.U`).
- **`IsAbsConvSeries`**: A series in an `ExPseudoNormedAbGroup` is absolutely convergent iff its norm series is upper-convergent.
- **`absConv-isConv`**: Absolute convergence implies convergence.
- **`series_<=`**: Comparison test for upper-real series.
- **`real-series_<=`**: Comparison test for non-negative real series.

#### Shift Invariance

- **`zeroSeries-sum`**: The zero series sums to `0`.
- **`series-shift-conv`**: Convergence is invariant under shifting by one.
- **`upperSeries-shift-conv`**, **`upperSeries-shifts-conv`**: Same for upper-real series, single shift and shift by `N`.
- **`series-shift-absConv`**, **`series-shifts-absConv`**: Same for absolute convergence.

#### Cauchy Criteria and Limits

- **`series-conv`**: Cauchy criterion for convergence in an `ExPseudoNormedAbGroup` (rational `eps`, `.U`).
- **`series-conv-real`**: Cauchy criterion using real `eps` and strict inequality, for `PseudoNormedAbGroup`.
- **`series-limit`**: Convergent series have terms tending to `0`.

#### Boundedness

- **`IsBoundedUpperSeries`**: All terms of an upper-real series are bounded by some rational `B`; `.shifted` derives boundedness when only a tail is uniformly bounded.
- **`upperSeries-bounded_<=`**: Boundedness is preserved under pointwise inequality.
- **`IsBoundedSeries`**: Boundedness for series in `ExPseudoNormedAbGroup` via the norm.
- **`series-lim-bound`**: Convergent series in a bounded normed group are bounded.
- **`series_*-bounded`**: A bounded series times a convergent non-negative series is upper-convergent.

#### Geometric and Ratio Tests

- **`geometric-upper-series-conv`**: The geometric upper-real series converges when `r >= 0` and `r.U 1`.
- **`geometric-series-absConv`**: `pow x` is absolutely convergent in a normed ring when `(norm x).U 1`.
- **`upper-ratio-test`**: Ratio test for non-negative upper-real series with ratios converging to `l < 1`; helpers `iter-bound` (iterated bound `f j <= f 0 * pow r j`) and `S-bounded` (each term is bounded).
- **`ratio-test`**: Ratio test for series in a bounded normed abelian group, yielding absolute convergence.

#### Function Series

- **`MTest`**: Weierstrass M-test: pointwise norm-bound by a convergent upper series gives uniform convergence of partial sums.
- **`partialSum-cover`**: Partial sums of cover maps `X -> Y` form a cover map.
