### Analysis.PowerSeries

Power series over extended pseudo-normed rings: convergence radius, absolute convergence, and the ratio test.

#### Power Series Construction

- **`powerSeries`**: Given coefficients `cs : Nat -> X` and a point `x : X`, the `n`-th term `cs n * pow x n` of the formal power series.
- **`powerSeries_0`**: Evaluation at `0` yields a convergent upper series, given each coefficient is upper-bounded by some rational.

#### Radius of Convergence

- **`convRadius`**: The radius of convergence of `cs` as a `LowerReal`, defined by `L a := ∃ b > a` such that `Σ ‖cs j‖ * b^j` converges.
- **`convRadius-absConv`**: Inside the radius (`norm x < convRadius cs`), the power series converges absolutely at `x`.
- **`convRadius-div`**: Conversely, if the series converges at `x` and `a < norm x` (rational), then `a` is in the radius.

#### Globally Convergent Power Series

- **`IsPowerSeriesConv`**: Predicate stating that `Σ ‖cs n‖ * a^n` converges as an upper series for every nonnegative rational `a` (i.e. infinite radius of convergence).
- **`IsPowerSeriesConv.upper`**: Extends convergence to any nonnegative `ExUpperReal` bounded above by a rational.
- **`IsPowerSeriesConv.atPoint`**: Specialization to `a = norm x` for any point `x : X`.

#### Convergence Consequences

- **`powerSeriesConv-absConv`**: A globally convergent power series is absolutely convergent at every point.
- **`powerSeriesConv-funcConv`**: The sequence of partial sums `λ n x. partialSum (powerSeries cs __ x) n` converges as a function on `X`.
- **`powerSeries-unbounded-conv`**: Over an unbounded pseudo-valued ring, pointwise convergence at every `x` implies `IsPowerSeriesConv cs`.

#### Ratio Test

- **`power-ratio-test`**: If `‖c (n+1)‖ ≤ ‖c n‖ * b n` and `b → l` with `l * a < 1` for nonnegative rational `a`, then `Σ ‖c j‖ * a^j` converges.
- **`power-ratio-test-inf`**: If the same ratio bound holds with `b → 0`, then `cs` is a globally convergent power series (infinite radius).
