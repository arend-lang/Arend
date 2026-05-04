### Arith.Nat.Sequence

This module provides search and finiteness results for decidable predicates on natural numbers.

#### Definitions

- **`search`**: Given a decidable predicate `A` on `Nat` and a witness `∃ n, A n`, produces a contractible type of minimal witnesses `(n0, A n0, ∀ n. A n → n0 ≤ n)` (well-ordering / least element principle).
- **`NatFinSubset`**: Given a decidable predicate `A` on `Nat` bounded by `M` (i.e., `A n → n < M`), proves that `Σ (n : Nat) (A n)` is a `FinSet`.
