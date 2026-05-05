### Set.Countable

Countable types: bijections between `Nat` and various structured types, plus a notion of countability via partial enumeration.

#### Bijections with Nat

- **`natPair-countable`**: `QEquiv` between `Nat` and `Nat × Nat` (Cantor pairing).
- **`natTuple-countable`**: `QEquiv` between `Nat` and `Array Nat (suc n)` for any fixed length `n+1`, by iterated pairing.
- **`sum-countable`**: Given a family `B : Nat -> \Type` with each `B n` countable, builds a `QEquiv` between `Nat` and `\Sigma (n : Nat) (B n)`.
- **`maybe-countable`**: Lifts a `QEquiv {Nat} {A}` to a `QEquiv {Nat} {Maybe A}` (sending `0` to `nothing`).
- **`natArray-countable`**: `QEquiv` between `Nat` and `Array Nat` (variable-length sequences of naturals), built from `maybe-countable`, `sum-countable`, and `natTuple-countable`.
  - **`array-sum`**: Auxiliary `QEquiv` between `Array A` and `Maybe (\Sigma (n : Nat) (Array A (suc n)))` splitting empty vs. non-empty arrays.

#### Countable Predicate

- **`Countable`**: A type `A` is countable if there is `f : Nat -> Maybe A` whose image (as `just`) covers every `a : A`. Defined as `\Sigma (f : Nat -> Maybe A) (\Pi (a : A) -> ∃ (n : Nat) (f n = just a))`.
- **`fin-countable`**: Every `KFinSet` is countable (truncated): uses the surjection from `Fin finCard` and decides `n < finCard`.
- **`countable-func`**: Extracts the partial enumeration `Nat -> Maybe A` from a `Countable A` (shifted: `0 ↦ nothing`).
- **`countable-func-surj`**: `countable-func c` is surjective onto `Maybe A`.
- **`func-countable`**: Builds `Countable A` from any surjection `f : Nat -> A`.
- **`pointed-countable`**: Given a basepoint `a0 : A` and `Countable A`, produces a total surjection `Nat -> A` by replacing `nothing` with `a0`.
- **`pointed-countable-surj`**: `pointed-countable a0 c` is surjective.

#### Closure Under Constructions

- **`surj-countable`**: Countability transfers along surjections: `Countable A` and `f : A -> B` surjective imply `Countable B`.
- **`array-countable`**: `Countable A` implies `Countable (Array A)`, via mapping `countable-func` over `natArray-countable`.
  - **`map-surj`**: `map f` on arrays is surjective when `f` is.
  - **`func-array`**: Collapses an `Array (Maybe A)` to an `Array A` by truncating at the first `nothing`.
  - **`func-array_just`**: `func-array (map just l) = l`.
  - **`func-array-surj`**: `func-array` is surjective.
- **`quotient-countable`**: `Countable A` implies `Countable (Quotient R)` for any relation `R`, via `Quotient.in-surj`.
- **`factor-countable`**: For an ideal `I`, `Countable I.S` implies `Countable (FactorRing I)`.
