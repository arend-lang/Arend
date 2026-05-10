### Combinatorics.Combinations

Defines $k$-combinations of an $n$-element set as strictly monotone maps $\mathrm{Fin}\ k \to \mathrm{Fin}\ n$, and proves their cardinality equals the binomial coefficient $\binom{n}{k}$.

A combination is encoded as a `StrictPosetHom` between linear finite orders, giving a canonical "sorted" representation that avoids quotienting by permutations. Finiteness is established by exhibiting combinations as the decidable subset of strictly sorted arrays inside `Array (Fin n) k`. The counting equivalence `Fin (binom n k) ≃ Combinations n k` is proved by induction on $n, k$ using Pascal's recurrence: a combination of size $k+1$ from $n+1$ elements either contains the maximum (and so reduces to a size-$k$ combination of $n$) or omits it (a size-$(k+1)$ combination of $n$), matching the recursive structure of `binom`.

#### Array Finiteness

- **`array-equiv`**: Equivalence between `Array A n` and `Fin n -> A`.
- **`ArrayFin`**: `FinSet` instance for `Array A n` over a finite `A`, with cardinality `A.finCard ^ n`.

#### Core Definition

- **`Combinations n k`**: A $k$-combination of `Fin n`, defined as `StrictPosetHom (FinOrder k) (FinOrder n)` — a strictly increasing map.
- **`IsStrictlySorted`**: Predicate on arrays `Array (Fin n) k` asserting `arr i < arr j` whenever `i < j`.
- **`IsStrictlySorted-dec`**: Decidability of strict sortedness via finite trichotomy and decidable order on `FinOrder n`.
- **`CombinationsFin`**: `FinSet` instance for `Combinations n k`, obtained by viewing combinations as the decidable subset of strictly sorted arrays.
  - **`equiv-arr-hom`**: Equivalence between strictly sorted arrays and strict poset homomorphisms.

#### Fin Restriction Helpers

- **`finRestrict`**: Restricts `a : Fin (suc n)` to `Fin n` given a proof `a < n`.
- **`finRestrict-nat`**: Restriction preserves the underlying natural number.
- **`finRestrict-<`**: Restriction preserves the strict order.
- **`finLast-nat`**: `finLast k` has natural-number value `k`.
- **`binom0`**: `binom n 0 = 1`.
- **`fin-last-or`**: Trichotomy: any `i : Fin (suc k)` is either `< k` or equals `finLast k`.
- **`lt-suc-neq`**: From `a < suc b` and `a ≠ b`, conclude `a < b`.

#### Tail and Extension

- **`gTail`**: Drops the first element of a combination of size `suc k`, yielding a combination of size `k`.
- **`extendFunc`**: Extends a combination `g : Combinations n k` to `Fin (suc k) -> Fin (suc n)` by appending `finLast n` at the end (used to encode "the maximum element is in the combination").
- **`extendFunc-gt-head`**: The head `g 0` is strictly less than any value of `extendFunc (gTail g)`.
- **`extendFunc-mono`**: `extendFunc g` is strictly monotone.
- **`extendFunc-last`**, **`extendFunc-last-nat`**: `extendFunc g` sends `finLast k` to `finLast n` (value `n`).
- **`extendFunc-notlast`**: On non-last indices, `extendFunc g` agrees with `g`.

#### Pascal Splitting

- **`Combinations-split-f`**: Splits a combination `h : Combinations (suc n) (suc k)` based on whether `h (finLast k) = n`: if yes, the maximum is included and the rest restricts to `Combinations n k`; if no, all values fit in `Fin n`, giving `Combinations n (suc k)`.
- **`Combinations-split-ret`**: Inverse — embeds either case back into `Combinations (suc n) (suc k)` via `extendFunc` or direct inclusion.
- **`Combinations-split`**: `QEquiv` between `Combinations (suc n) (suc k)` and `Or (Combinations n k) (Combinations n (suc k))`, the combinatorial Pascal identity.

#### Base Cases

- **`Combinations-base0`**: `Fin 1 ≃ Combinations n 0` — there is exactly one empty combination.
- **`Combinations-base-empty`**: `Fin 0 ≃ Combinations 0 (suc k)` — no nonempty combinations from the empty set.

#### Main Counting Theorem

- **`Combinations-binom`**: `Equiv {Fin (binom n k)} {Combinations n k}`, proved by induction on `n, k` combining `Combinations-split`, `OrFin.aux`, and the base cases. Witnesses that combinations are counted by the binomial coefficient.
