### Combinatorics.Compositions

Compositions and weak compositions of a natural number `n` into `k` parts, with their finiteness and bijection to combinations.

#### Core Definitions

- **`Compositions`**: `Compositions n k` is the type of compositions of `n` into `k` positive parts: an array of `k` elements of `Fin n` summing to `n`, all strictly positive.
- **`WeakCompositions`**: `WeakCompositions n k` is the type of weak compositions: an array of `k` elements of `Fin n` summing to `n` (parts may be zero).

#### Predicates and Decidability

- **`IsPositive`**: Predicate stating every entry of an array `arr : Array Nat k` is strictly positive.
- **`IsPositive-dec`**: Decidability instance for `IsPositive`, via finite decidability over `Fin k`.
- **`IsSum`**: Predicate stating `AddMonoid.BigSum arr = n`.
- **`IsSum-dec`**: Decidability of `IsSum n arr` via `NatSemiring.decideEq`.

#### Sum Lemmas

- **`BigSum_elem`**: Removing index `i` from an array and adding back `arr i` recovers the full sum: `arr i + BigSum (skip arr i) = BigSum arr`.
- **`<=_exists`**: From `a + b = n`, deduces `a < suc n`.

#### Finiteness Instances

- **`WeakCompositionsFin`**: `FinSet` instance for `WeakCompositions n k`, built as a decidable subset of `ArrayFin (FinFin n) k` carved out by `IsSum n`.
- **`CompositionsFin`**: `FinSet` instance for `Compositions n k`, built as the further decidable subset of `WeakCompositions n k` carved out by `IsPositive`.

#### Partial Sums

- **`partialSums`**: For `arr : Array Nat k`, produces the array of strictly increasing partial sums shifted so that consecutive entries differ by at least one (`a :: map (\lam x => suc (a + x)) (partialSums rest)`).
- **`partialSums-strictlyIncreasing`**: `i < j` implies `partialSums arr i < partialSums arr j`.
- **`partialSums-leq-sum`**: `partialSums arr i <= BigSum arr + i`.
- **`partialSums-bound`**: When `BigSum arr = n`, every `partialSums arr i` is below `n + k`.
- **`partialSums-strict-bound`**: Strict variant of the above for arrays of length `suc k`, used to land in `Fin (n + k)`.

#### Bijection with Combinations

- **`weakComp-to-comb`**: Maps a weak composition `w : WeakCompositions n (suc k)` to a `Combinations (n + k) k` by sending it to the strictly increasing function from `FinOrder k` to `FinOrder (n + k)` given by partial sums.
- **`comb-to-weakComp`**: Inverse direction, recovering a weak composition from a combination.
- **`weakComp-comb-equiv`**: Equivalence `WeakCompositions n (suc k) ≃ Combinations (n + k) k` (stars-and-bars bijection).

#### Cardinality

- **`weakCompositions-binom`**: The number of weak compositions of `n` into `suc k` parts equals the binomial coefficient `binom (n + k) k`.
