### Analysis.Series

Infinite series in topological abelian groups, with notions of convergence, absolute convergence, and standard convergence tests.

A `Series` is just a sequence `Nat -> A`, with convergence defined via the limit of partial sums. The module distinguishes ordinary convergence (`IsConvSeries`, in any `TopAbGroup`) from absolute convergence (`IsAbsConvSeries`, defined through an upper-real-valued series of norms in an `ExPseudoNormedAbGroup`). Mid-sums (`midSum b n m` = sum of `b n, ..., b (m-1)`) provide the Cauchy-style criterion that powers most lemmas, including the ratio test, the Weierstrass M-test, and comparison results. The `Partial`-valued `seriesSum` packages the limit in a complete topological abelian group, returning `defined l` exactly when the series sums to `l`.

#### Partial Sums

- **`Series`**: A series in `A` is a function `Nat -> A`.
- **`partialSum`**: `partialSum S n` = `S 0 + S 1 + ... + S (n-1)` via `BigSum`.
- **`partialSum-empty`**: `partialSum S 0 = 0`.
- **`partialSum_<=`**: Pointwise `<=` lifts to partial sums in a `PosetAddMonoid`.
- **`partialSum_hom`**: `AddMonoidHom`s commute with `partialSum`.

#### Convergence and Sum

- **`IsConvSeries`**: `S` is convergent iff its partial sums converge in a `TopAbGroup`.
- **`IsSeriesSum`**: `S` sums to `l` iff `partialSum S` has limit `l`.
- **`seriesSum-fin`**: A series eventually zero (from index `n`) sums to its finite `BigSum`.
- **`seriesSum-conv`**: Having a sum implies convergence.
- **`seriesSum`**: In a `CompleteTopAbGroup`, returns the sum as a `Partial A`.
- **`seriesConv-sum`**: A convergent series's `seriesSum` is its actual sum.
- **`seriesSum-char`**: `seriesSum S = defined l` iff `IsSeriesSum S l`.

#### Mid-Sums

- **`midSum`**: Recursive definition of partial sum from index `n` to `m`; `midSum S 0 m = partialSum S m`.
- **`midSum'`**: Alternative definition: `partialSum (\lam k => S (n + k)) m`.
- **`midSum'_midSum`**, **`midSum_midSum'`**: Conversion between the two forms.
- **`midSum_hom`**: Homomorphisms commute with `midSum`.
- **`midSum_suc`**: `midSum S n (suc n) = S n` (single-term sum).
- **`midSum-diff`**: In an `AbGroup`, `midSum S n m = partialSum S m - partialSum S n` (when `n <= m`).
- **`midSum-split`**: `midSum b n k = midSum b n m + midSum b m k` for `n <= m <= k`.
- **`midSum-degenerate`**: `midSum b n m = 0` when `m < n`.
- **`midSum_<=-left`**, **`midSum_<=`**: Monotonicity in starting index and pointwise comparison.
- **`midSum_0`**: Mid-sum of the zero series is `0`.
- **`midSum>=0`**: Non-negative summands give non-negative mid-sum.
- **`midSum_norm`**: `norm (midSum S n m) <= midSum (norm ∘ S) n m` (triangle inequality).
- **`midSum_<=-ldistr`**: Left distributivity `midSum (x * b) n m <= x * midSum b n m` for `ExUpperReal`; `BigSum_<=-ldistr` is the finite-sum version.

#### Upper-Real and Absolute Convergence

- **`IsConvUpperSeries`**: Cauchy-style convergence for `ExUpperReal`-valued series via mid-sums.
- **`IsAbsConvSeries`**: Series with `IsConvUpperSeries (norm ∘ S)` in an `ExPseudoNormedAbGroup`.
- **`absConv-isConv`**: Absolute convergence implies convergence.

#### Shift Invariance

- **`series-shift-conv`**: Convergence is preserved by dropping the head.
- **`upperSeries-shift-conv`**, **`upperSeries-shifts-conv`**: Shift invariance for non-negative upper-real series, by one or by `N` steps.
- **`series-shift-absConv`**, **`series-shifts-absConv`**: Same for absolute convergence.

#### Cauchy Characterizations and Limits

- **`series-conv`**: Convergence iff Cauchy condition on `norm (midSum S N n)` against rational `eps`.
- **`series-conv-real`**: Same with real `eps` in a `PseudoNormedAbGroup`.
- **`seriesSum_defined`**: Sum equals `defined a` from a Cauchy-style estimate `norm (a - partialSum S n)`; inner `conv` extracts that estimate from a known sum.
- **`series-limit`**: Convergent series have terms tending to `0`.
- **`zeroSeries-sum`**: The zero series sums to `0`.

#### Comparison Tests

- **`real-series_<=`**: For non-negative real series, pointwise `<=` plus convergence of the majorant gives convergence.
- **`series_<=`**: Comparison test for upper-real series.

#### Boundedness

- **`IsBoundedUpperSeries`**: Existence of a rational bound `B` for every term; `shifted` gives boundedness from a bound holding only past index `N`.
- **`upperSeries-bounded_<=`**: Boundedness propagates downward via pointwise `<=`.
- **`IsBoundedSeries`**: Boundedness of `norm ∘ S` in an `ExPseudoNormedAbGroup`.
- **`series-lim-bound`**: A series with a limit in a bounded normed group is bounded.
- **`series_*-bounded`**: A bounded series times a convergent non-negative series is convergent.

#### Geometric and Ratio Tests

- **`geometric-upper-series-conv`**: `IsConvUpperSeries (pow r)` for `0 <= r` with `r.U 1` (i.e., `r < 1`).
- **`geometric-series-absConv`**: Geometric series `pow x` is absolutely convergent when `norm x < 1` in an `ExPseudoNormedRing`.
- **`upper-ratio-test`**: Ratio test for upper-real series with limit `l < 1` of the ratio sequence; `iter-bound` and `S-bounded` are the key auxiliary bounds.
- **`ratio-test`**: Ratio test in a `BoundedExPseudoNormedAbGroup`: limit `l < 1` of `b` with `norm (S (suc n)) <= norm (S n) * b n` gives absolute convergence.

#### Function Series

- **`MTest`**: Weierstrass M-test — uniform convergence of partial sums of `f n : X -> Y` from a convergent dominating upper-real series `M`.
- **`partialSum-cover`**: Partial sums of cover maps `X -> Y` form a cover map.
