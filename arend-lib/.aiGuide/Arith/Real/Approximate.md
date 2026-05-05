### Arith.Real.Approximate

This module provides approximation lemmas for real numbers by rationals, integers, and naturals.

#### Approximation Lemmas

- **`real-rat-approx`**: Every real `x` can be approximated by a rational `q` within any `eps > 0`: `∃ (q : Rat) (dist q x < eps)`.
- **`real-int-approx`**: Every real `x` can be approximated by an integer `n` within distance `1`: `∃ (n : Int) (dist n x < 1)`.
  - **`exact`**: Refined variant: for any `eps > 0`, `∃ (n : Int) (dist n x < 1/2 + eps)`.
- **`real-nat-approx`**: Every non-negative real `x` can be approximated by a natural `n` within distance `1`: `∃ (n : Nat) (dist n x < 1)`.
