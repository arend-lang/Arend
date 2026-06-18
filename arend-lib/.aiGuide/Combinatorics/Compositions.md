### Combinatorics.Compositions

Compositions and weak compositions of a natural number `n` into `k` parts, with their finiteness and bijection to combinations.

A *composition* of `n` into `k` parts is a sequence of `k` positive naturals summing to `n`; a *weak composition* drops the positivity requirement and allows zeros. Both are encoded as Sigma types over arrays bounded by `Fin n`, with side conditions captured as decidable predicates so the resulting types inherit a `FinSet` structure via `DecSubSet-isFin` over array finiteness. The module also constructs the classical bijection between weak compositions of `n` into `k+1` parts and `k`-combinations of `n + k`, via partial sums, yielding the count `binom (n + k) k`.

#### Core Types

- **`Compositions`**: `\Sigma (arr : Array (Fin n) k) (IsPositive arr) (AddMonoid.BigSum arr = n)` — sequences of `k` strictly positive entries summing to `n`.
- **`WeakCompositions`**: `\Sigma (arr : Array (Fin n) k) (AddMonoid.BigSum arr = n)` — like `Compositions` but allowing zeros.

#### Decidable Predicates

- **`IsPositive`**: Predicate `\Pi (i : Fin k) -> 0 < arr i` stating every entry of an array is positive.
- **`IsPositive-dec`**: `Decide` instance for `IsPositive`, enabling its use as a decidable subset predicate.
- **`IsSum`**: Predicate `AddMonoid.BigSum arr = n` stating an array sums to `n`.
- **`IsSum-dec`**: Decidability of `IsSum`, derived from decidable equality on `Nat`.

#### Sum Lemmas

- **`BigSum_elem`**: For `arr : Array Nat (suc k)` and `i : Fin (suc k)`, `arr i + BigSum (skip arr i) = BigSum arr` — extracting one element from a sum.
- **`<=_exists`**: From `a + b = n` derive `a < suc n`; used to coerce summands into `Fin (suc n)`.

#### Finite-Set Instances

- **`WeakCompositionsFin`**: `FinSet` instance for `WeakCompositions n k`, built by carving out the `IsSum n` decidable subset of `Array (Fin n) k` and transporting finiteness across the obvious equivalence.
- **`CompositionsFin`**: `FinSet` instance for `Compositions n k`, layered on top of `WeakCompositionsFin` by further restricting to the `IsPositive` decidable subset.

#### Partial Sums

- **`partialSums`**: Maps an array `arr` of length `k` to the array whose `i`-th entry is `arr 0 + arr 1 + ... + arr i + i` (a strictly increasing rewriting suitable for indexing into `Fin (n + k)`).
- **`partialSums-strictlyIncreasing`**: For `i < j`, `partialSums arr i < partialSums arr j`.
- **`partialSums-leq-sum`**: `partialSums arr i <= BigSum arr + i`, the basic bound.
- **`partialSums-bound`**: When `BigSum arr = n`, every `partialSums arr i` is `< n + k`.
- **`partialSums-strict-bound`**: Variant of the previous bound for arrays of length `suc k`, used to land partial sums in `Fin (n + k)`.

#### Bijection with Combinations

- **`weakComp-to-comb`**: Encodes a weak composition `w : WeakCompositions n (suc k)` as a `Combinations (n + k) k`, i.e. a strictly monotone map `Fin k -> Fin (n + k)`, by taking partial sums of `w`'s entries.
- **`comb-to-weakComp`**: Inverse direction — recovers a weak composition from a `k`-combination of `n + k` (gap-encoding).
- **`weakComp-comb-equiv`**: The equivalence `WeakCompositions n (suc k) ≃ Combinations (n + k) k`.
- **`weakCompositions-binom`**: Cardinality formula `finCard (WeakCompositionsFin n (suc k)) = binom (n + k) k`, the standard "stars and bars" count.
