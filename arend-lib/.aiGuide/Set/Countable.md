### Set.Countable

Constructions and combinators for countable sets, built from explicit bijections between `Nat` and structured data.

This module establishes a framework for working with countable types in two complementary forms: as concrete `QEquiv` bijections with `Nat` (for `Nat`-pairs, tuples, and arrays via Cantor-style pairing) and as a more flexible `Countable` predicate based on partial surjections `Nat -> Maybe A`. The `Countable` form is closed under sums, surjections, quotients, and factor rings, making it suitable for transferring countability to derived constructions like quotients and factor rings of ideals. The natural-number bijections use a pairing function whose inverse `aux` is justified by strong induction on the sum of coordinates.

#### Bijections with Nat

- **`natPair-countable`**: `QEquiv` between `Nat` and `Nat × Nat`, using a Cantor-style enumeration along anti-diagonals. The forward map walks through pairs by decrementing the first coordinate and bumping the second, while the inverse computes triangular-number offsets.
- **`natTuple-countable`**: For each `n`, a `QEquiv` between `Nat` and `Array Nat (suc n)` (nonempty fixed-length tuples), built inductively by composing `natPair-countable` with itself.
- **`natArray-countable`**: A `QEquiv` between `Nat` and `Array Nat` (arbitrary-length lists of naturals), obtained by reducing arrays to `Maybe (Σ n. Array Nat (suc n))` via `array-sum` and then enumerating.
- **`natArray-countable.array-sum`**: Auxiliary `QEquiv` decomposing `Array A` as either empty or a nonempty array tagged with its predecessor length.

#### QEquiv Combinators

- **`sum-countable`**: Given a family `B : Nat -> Type` with each `B n` in bijection with `Nat`, builds a `QEquiv` between `Nat` and `Σ (n : Nat) (B n)`.
- **`maybe-countable`**: Lifts a `QEquiv {Nat} {A}` to a `QEquiv {Nat} {Maybe A}` by mapping `0` to `nothing` and `suc n` to `just (e n)`.

#### Countable Predicate

- **`Countable`**: A type `A` is countable if there exists `f : Nat -> Maybe A` such that every `a : A` is hit by some `f n = just a`. This partial-surjection form gracefully handles empty types and avoids requiring a chosen basepoint.
- **`fin-countable`**: Every `KFinSet` is countable; uses the finite cardinal bound to define `f` via `LinearOrder.dec<_<=`.
- **`countable-func`**: Extracts a function `Nat -> Maybe A` from a `Countable` witness, shifted so index `0` returns `nothing`.
- **`countable-func-surj`**: The extracted function is surjective onto `Maybe A`.
- **`func-countable`**: Conversely, any total surjection `f : Nat -> A` (with proof of `IsSurj f`) yields `Countable A` by composing with `just`.
- **`pointed-countable`**: Given a basepoint `a0 : A` and a `Countable A`, produces a total surjection `Nat -> A` by replacing `nothing` with `a0`.
- **`pointed-countable-surj`**: Surjectivity of `pointed-countable`.

#### Closure Properties

- **`surj-countable`**: Countability transfers along surjections: if `A` is countable and `f : A -> B` is surjective, then `B` is countable.
- **`array-countable`**: If `A` is countable, so is `Array A`. Built by composing the `Nat`-surjection of `natArray-countable` with `map (countable-func c)` and then with `func-array`, which strips arrays at the first `nothing`.
- **`array-countable.map-surj`**: Surjectivity is preserved under `map` over arrays (uses finite axiom of choice `FinSet.finiteAC`).
- **`array-countable.func-array`**: Truncates an `Array (Maybe A)` to an `Array A` at the first `nothing`.
- **`array-countable.func-array_just`**: `func-array (map just l) = l`, witnessing the right-inverse on the `just`-image.
- **`array-countable.func-array-surj`**: Every `Array A` arises as `func-array` of some `Array (Maybe A)`.
- **`quotient-countable`**: If `A` is countable, so is any quotient `Quotient R`, via the canonical surjection `Quotient.in-surj`.
- **`factor-countable`**: If the carrier of an ideal `I` is countable, then `FactorRing I` is countable, by composing `quotient-countable` (with the relation `λ a b. I (a - b)`) with the identity surjection onto `FactorRing I`.
