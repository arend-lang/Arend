### Arith.Real.Approximate

Approximation of real numbers by rationals, integers, and natural numbers within prescribed error bounds.

This module provides existence lemmas showing that every real number can be approximated arbitrarily well by a rational, and within a fixed bound by an integer or (for non-negative reals) a natural number. Distances are measured using the `RealNormed` metric on the reals, treating `Rat`, `Int`, and `Nat` as embedded into `Real` via the standard inclusions. These results encode the density of rationals in the reals and the bounded-distance discreteness of integers, providing foundational lemmas for constructive analysis on `Real`.

#### Approximation Lemmas

- **`real-rat-approx`**: For any real `x` and positive real `eps`, there exists a rational `q` with `RealNormed.dist q x < eps`. Expresses density of `Rat` in `Real`.
- **`real-int-approx`**: For any real `x`, there exists an integer `n` with `RealNormed.dist n x < 1`. Every real is within unit distance of some integer.
- **`real-int-approx.exact`**: Sharper integer approximation: for any `eps > 0`, there exists `n : Int` with distance less than `1/2 + eps`, approaching the optimal half-unit bound.
- **`real-nat-approx`**: For any non-negative real `x`, there exists a natural number `n` with `RealNormed.dist n x < 1`. The non-negative analogue of `real-int-approx`.
