### Analysis.PowerSeries

Power series over extended pseudo-normed rings, their radius of convergence, and convergence tests.

This module formalizes formal power series `∑ cₙ xⁿ` valued in an `ExPseudoNormedRing`, together with their analytic behavior. The central abstraction is the **radius of convergence** `convRadius`, packaged as a `LowerReal` whose lower set records rationals `a` for which `∑ ‖cₙ‖ bⁿ` converges for some `b > a`; this lower-real presentation matches the constructive style used elsewhere for real numbers. The predicate `IsPowerSeriesConv` captures series that converge absolutely on every rational radius (i.e. infinite radius of convergence), and the module connects these notions via absolute convergence inside the radius, divergence outside, uniform/functional convergence of partial sums, and ratio-style tests bounding `‖cₙ₊₁‖ / ‖cₙ‖`.

#### Core Definitions

- **`powerSeries`**: The `n`-th term `cₙ * xⁿ` of a power series with coefficients `cs : Nat -> X` evaluated at `x : X`, for `X : ExPseudoNormedRing`.
- **`convRadius`**: The radius of convergence of a power series, presented as a `LowerReal`. A rational `a` lies below the radius iff there exists `b > a` such that `∑ ‖cₙ‖ * bⁿ` converges as an upper-real series.
- **`IsPowerSeriesConv`**: Property that a power series `cs` converges absolutely on every rational radius `a ≥ 0`, i.e. `∑ ‖cₙ‖ * aⁿ` is a convergent upper-real series for all such `a`. Effectively expresses "infinite radius of convergence".

#### Convergence Inside / Outside the Radius

- **`powerSeries_0`**: The series `∑ ‖cₙ‖ * 0ⁿ` is a convergent upper-real series (only the `n = 0` term is nonzero); this shows that `0` lies in the lower set of `convRadius`.
- **`convRadius>=0`**: The radius of convergence is nonnegative: `0 <= convRadius cs`.
- **`convRadius-absConv`**: If `‖x‖ <LU convRadius cs`, then `powerSeries cs x` converges absolutely. This is the key "inside the radius" theorem.
- **`convRadius-div`**: Contrapositive direction over a pseudo-valued ring: if the power series at `x` converges, then every rational `a < ‖x‖` lies in the lower set of `convRadius cs`.

#### Globally Convergent Power Series

- **`IsPowerSeriesConv.upper`**: Upgrades `IsPowerSeriesConv` to convergence at any extended-upper-real radius `a ≥ 0` that is rationally bounded above.
- **`IsPowerSeriesConv.atPoint`**: Specializes the upper-real version to `a := ‖x‖` for any point `x : X`, yielding convergence of `∑ ‖cₙ‖ * ‖x‖ⁿ`.
- **`powerSeriesConv-absConv`**: An `IsPowerSeriesConv` series converges absolutely at every point `x : X`.
- **`powerSeries-absConv`**: If `∑ ‖cₙ‖ * ‖x‖ⁿ` converges as an upper-real series, then `powerSeries cs x` converges absolutely.
- **`powerSeriesConv-funcConv`**: The sequence of partial sums `n ↦ x ↦ ∑_{k<n} cₖ * xᵏ` is functionally convergent on the open ball cover space `OpenBallCoverSpace 0 (convRadius cs)` (a statement about all `x` with `dist 0 x <LU convRadius cs`, no hypothesis on `cs`).
- **`powerSeriesConv-funcConv-total`**: The total version of `powerSeriesConv-funcConv`: if `cs` satisfies `IsPowerSeriesConv`, the partial sums `n ↦ x ↦ ∑_{k<n} cₖ * xᵏ` are functionally convergent on all of `X`.
- **`powerSeries-unbounded-conv`**: Converse over an unbounded pseudo-valued ring: if the power series converges at every point of `X`, then it satisfies `IsPowerSeriesConv`.

#### Sums of Power Series

For `X : CompleteExNormedRing`.

- **`powerSeriesSum`**: The sum of the power series `cs` at a point `x` inside the radius (`dist 0 x <LU convRadius cs`), as a point of `X`.
- **`powerSeriesSum-cover`**: `powerSeriesSum` is a cover map from `OpenBallCoverSpace 0 (convRadius cs)` to `X`.
- **`powerSeriesSum-isSum`**: `powerSeriesSum cs x xc` is the sum of the series `powerSeries cs x`, i.e. `IsSeriesSum`.
- **`powerSeriesSum-total`**: The sum of a power series satisfying `IsPowerSeriesConv`, defined at every `x : X`.
- **`powerSeriesSum-total-cover`**: `powerSeriesSum-total cs psc` is a cover map `X -> X`.
- **`powerSeriesSum-total-isSum`**: `powerSeriesSum-total cs psc x` is the sum of the series `powerSeries cs x`.

#### Products

- **`conv-powerSeries`**: Compatibility of convolution with `powerSeries`: if `x` is central in `A`, the `n`-th term of the convolution (in `FSeriesRing A`) of `powerSeries a x` and `powerSeries b x` equals the `n`-th term of `powerSeries (a * b) x`.

#### Ratio Tests

- **`power-ratio-test`**: Ratio test at a single rational radius. Given `‖c_{n+1}‖ ≤ ‖cₙ‖ * bₙ` with `b → l` and `l * a < 1` for `a ≥ 0`, the series `∑ ‖cₙ‖ * aⁿ` converges as an upper-real series.
- **`power-ratio-test-inf`**: Infinite-radius ratio test. If `‖c_{n+1}‖ ≤ ‖cₙ‖ * bₙ` and `b → 0`, then `cs` is `IsPowerSeriesConv` (equivalent to a power series with infinite radius of convergence, e.g. exp, sin, cos).
