### Analysis.PowerSeries

Power series over extended pseudo-normed rings, their radius of convergence, and convergence tests.

This module formalizes formal power series `∑ cₙ xⁿ` valued in an `ExPseudoNormedRing`, together with their analytic behavior. The central abstraction is the **radius of convergence** `convRadius`, packaged as a `LowerReal` whose lower set records rationals `a` for which `∑ ‖cₙ‖ bⁿ` converges for some `b > a`; this lower-real presentation matches the constructive style used elsewhere for real numbers. The predicate `IsPowerSeriesConv` captures series that converge absolutely on every rational radius (i.e. infinite radius of convergence), and the module connects these notions via absolute convergence inside the radius, divergence outside, uniform/functional convergence of partial sums, and ratio-style tests bounding `‖cₙ₊₁‖ / ‖cₙ‖`.

#### Core Definitions

- **`powerSeries`**: The `n`-th term `cₙ * xⁿ` of a power series with coefficients `cs : Nat -> X` evaluated at `x : X`, for `X : ExPseudoNormedRing`.
- **`convRadius`**: The radius of convergence of a power series, presented as a `LowerReal`. A rational `a` lies below the radius iff there exists `b > a` such that `∑ ‖cₙ‖ * bⁿ` converges as an upper-real series.
- **`IsPowerSeriesConv`**: Property that a power series `cs` converges absolutely on every rational radius `a ≥ 0`, i.e. `∑ ‖cₙ‖ * aⁿ` is a convergent upper-real series for all such `a`. Effectively expresses "infinite radius of convergence".

#### Convergence Inside / Outside the Radius

- **`powerSeries_0`**: Evaluating any termwise upper-bounded series at `x = 0` yields a convergent upper-real series (the trivial `n = 0` case dominates).
- **`convRadius-absConv`**: If `‖x‖ <LU convRadius cs`, then `powerSeries cs __ x` converges absolutely. This is the key "inside the radius" theorem.
- **`convRadius-div`**: Contrapositive direction over a pseudo-valued ring: if the power series at `x` converges, then every rational `a < ‖x‖` lies in the lower set of `convRadius cs`.

#### Globally Convergent Power Series

- **`IsPowerSeriesConv.upper`**: Upgrades `IsPowerSeriesConv` to convergence at any extended-upper-real radius `a ≥ 0` that is rationally bounded above.
- **`IsPowerSeriesConv.atPoint`**: Specializes the upper-real version to `a := ‖x‖` for any point `x : X`, yielding convergence of `∑ ‖cₙ‖ * ‖x‖ⁿ`.
- **`powerSeriesConv-absConv`**: An `IsPowerSeriesConv` series converges absolutely at every point `x : X`.
- **`powerSeriesConv-funcConv`**: The sequence of partial sums `n ↦ x ↦ ∑_{k<n} cₖ * xᵏ` is functionally (uniformly on the appropriate cover) convergent.
- **`powerSeries-unbounded-conv`**: Converse over an unbounded pseudo-valued ring: if the power series converges at every point of `X`, then it satisfies `IsPowerSeriesConv`.

#### Ratio Tests

- **`power-ratio-test`**: Ratio test at a single rational radius. Given `‖c_{n+1}‖ ≤ ‖cₙ‖ * bₙ` with `b → l` and `l * a < 1` for `a ≥ 0`, the series `∑ ‖cₙ‖ * aⁿ` converges as an upper-real series.
- **`power-ratio-test-inf`**: Infinite-radius ratio test. If `‖c_{n+1}‖ ≤ ‖cₙ‖ * bₙ` and `b → 0`, then `cs` is `IsPowerSeriesConv` (equivalent to a power series with infinite radius of convergence, e.g. exp, sin, cos).
