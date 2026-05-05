### Topology.NormedAbGroup.Real.Compact

Compactness/total-boundedness results for the real line, providing finite ε-nets for bounded intervals.

#### Interval Subdivision

- **`real-split`**: Constructs an array of `suc n` real numbers evenly subdividing the interval `[a, b]`, with the `j`-th point being `a + j * (b - a) / n`. Used as a finite ε-net for `[a, b]`.
- **`real-split.real-approx`**: For any `x ∈ [a, b]` with `a < b` and `n ≠ 0`, there exists an index `i : Fin (suc n)` such that `real-split a b n i` is within `(b - a) / n` of `x`. Establishes that the subdivision is an ε-net.

#### Total Boundedness

- **`real-bounded-tb`**: Proves that the normed abelian group `RealNormed` satisfies `BallsTotallyBounded`, i.e., every ball in `ℝ` is totally bounded. This is the key compactness ingredient implying that closed bounded subsets of `ℝ` are compact.
