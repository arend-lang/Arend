### Topology.NormedAbGroup.Real.Compact

Total boundedness of bounded intervals in the real line as a normed abelian group.

This module establishes that the real numbers, viewed as a normed abelian group, satisfy the `BallsTotallyBounded` property — every closed ball admits finite ε-nets. The key technical device is `real-split`, which discretizes an interval `[a, b]` into `n+1` evenly-spaced sample points; any real in the interval is then approximated by one of these samples within distance `(b-a)/n`. This combinatorial approximation lemma feeds directly into the proof that balls in `RealNormed` are totally bounded, a prerequisite for compactness arguments on the reals.

#### Interval Discretization

- **`real-split`**: Given reals `a`, `b` and `n : Nat`, produces an array of `n+1` real samples `a + j · (b-a)/n` for `j = 0, …, n`, evenly partitioning the interval `[a, b]`.
- **`real-split.real-approx`**: Approximation lemma — for any `x` with `a ≤ x ≤ b` and `a < b`, `n ≠ 0`, there exists an index `i` such that the sample `real-split a b n i` is within distance `(b-a)/n` of `x`.

#### Total Boundedness

- **`real-bounded-tb`**: Proves that `RealNormed` satisfies `BallsTotallyBounded`, i.e. every closed ball in the real line admits a finite ε-net for any ε > 0; obtained by applying `real-approx` with sufficiently large `n`.
