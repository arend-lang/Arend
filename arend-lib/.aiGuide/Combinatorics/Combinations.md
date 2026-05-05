### Combinatorics.Combinations

Combinations of `k` elements from `n` realized as strictly monotone maps `Fin k → Fin n`, with finiteness witnesses and a counting equivalence with `Fin (binom n k)`.

#### Array Finiteness

- **`array-equiv`**: `QEquiv` between `Array A n` and `Fin n → A`, identifying arrays with their indexing functions.
- **`ArrayFin`**: `FinSet` instance for `Array A n` over a finite `A`, with cardinality `A.finCard ^ n`.

#### Combinations and Sortedness

- **`Combinations`**: `Combinations n k` is defined as `StrictPosetHom (FinOrder k) (FinOrder n)`, i.e. strictly increasing maps `Fin k → Fin n`.
- **`IsStrictlySorted`**: Predicate on `Array (Fin n) k` asserting `i < j → arr i < arr j`.
- **`IsStrictlySorted-dec`**: Decidability instance for `IsStrictlySorted` via finite case analysis with trichotomy on `Fin k`.
- **`CombinationsFin`**: `FinSet` instance for `Combinations n k`, obtained as the decidable subset of strictly sorted arrays inside `ArrayFin (FinFin n) k`.
  - **`equiv-arr-hom`**: Equivalence between `Σ (arr : Array (Fin n) k), IsStrictlySorted arr` and `Combinations n k`, packaging an array with its sortedness proof as a strict poset homomorphism.

#### Fin Helpers

- **`finRestrict`**: Given `a : Fin (suc n)` with `a < n`, restricts to `Fin n`.
- **`finRestrict-nat`**: `finRestrict a p` and `a` agree as natural numbers.
- **`finRestrict-<`**: `finRestrict` preserves strict order.
- **`finLast-nat`**: `finLast k` equals `k` as a natural number.
- **`fin-last-or`**: For `i : Fin (suc k)`, either `i < k` or `i = finLast k`.
- **`lt-suc-neq`**: From `a < suc b` and `a ≠ b` derive `a < b`.
- **`binom0`**: `binom n 0 = 1`.

#### Extending and Splitting Combinations

- **`gTail`**: Drops the first element of a `Combinations n (suc k)` to get a `Combinations n k` indexed by `j ↦ g (suc j)`.
- **`extendFunc`**: From `g : Combinations n k`, build `Fin (suc k) → Fin (suc n)` that maps the last index to `finLast n` and otherwise reuses `g`; recursive in `k`.
- **`extendFunc-gt-head`**: `g 0 < extendFunc (gTail g) j` — head stays below the tail extension.
- **`extendFunc-mono`**: `extendFunc g` is strictly monotone.
- **`extendFunc-last`**: `extendFunc g (finLast k) = finLast n`.
- **`extendFunc-notlast`**: For `i : Fin k`, `extendFunc g (i : Fin (suc k)) = g i`.
- **`extendFunc-last-nat`**: `extendFunc g (finLast k) = n` as a natural number.

#### The Pascal Splitting Equivalence

- **`Combinations-split-f`**: Splits `h : Combinations (suc n) (suc k)` based on whether `h (finLast k) = n`: yields a `Combinations n k` (last hits `n`) or a `Combinations n (suc k)` (last stays below `n`), via `finRestrict`.
- **`Combinations-split-ret`**: Inverse direction — embeds `Combinations n k` into `Combinations (suc n) (suc k)` by `extendFunc` (appending `finLast`), or includes `Combinations n (suc k)` directly.
- **`Combinations-split`**: `QEquiv` between `Combinations (suc n) (suc k)` and `Or (Combinations n k) (Combinations n (suc k))`, the combinatorial Pascal identity at the level of types.

#### Base Cases and Counting

- **`Combinations-base0`**: `QEquiv` between `Fin 1` and `Combinations n 0` (the unique empty selection).
- **`Combinations-base-empty`**: `QEquiv` between `Fin 0` and `Combinations 0 (suc k)` (no nonempty selection from the empty set).
- **`Combinations-binom`**: Main result — `Equiv {Fin (binom n k)} {Combinations n k}`, proved by induction on `n, k` using the base cases and `Combinations-split` together with `OrFin.aux`, witnessing that combinations are counted by the binomial coefficient.
