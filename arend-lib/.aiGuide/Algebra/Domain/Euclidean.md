### Algebra.Domain.Euclidean

Euclidean domains: commutative rings with a division-with-remainder algorithm, plus the canonical instances for `Nat` and `Int`.

#### Core Classes

- **`EuclideanSemiringData`**: Extends `CSemiring` and `DecSet`. Packages a Euclidean structure: `euclideanMap : E -> Nat`, `divMod : E -> E -> \Sigma E E`, the division identity `y * (divMod x y).1 + (divMod x y).2 = x`, the size-decreasing property of remainders, and a `summandDiv` axiom recovering divisibility from `d * x + y = d * z`.
- **`EuclideanRingData`**: Extends `EuclideanSemiringData` and `CRing`. Derives `summandDiv` automatically by solving `inv := z - x` using ring laws.
- **`EuclideanDomain`**: Extends `PID`. Asserts `isEuclidean : TruncP (EuclideanRingData ...)` and derives the Bezout property and the descending-divisor-chain condition from it.

#### Helpers in `EuclideanSemiringData.where`

- **`suc'`**: A variant successor function (`0 ↦ 1`, `suc n ↦ suc (suc n)`) used for fueling recursion.
- **`suc'=suc`**: Shows `suc' n = suc n` for `n` reachable, used to discharge fuel bounds.

#### Helpers in `EuclideanDomain.where`

- **`aux`**: Pigeonhole-style lemma: given a sequence `a : Fin (suc n) -> nonzero` with each `a (suc i)` dividing `a i`, and a witness `b` divisibility-equivalent to `a 0` with `euclideanMap b < n`, produces some `i` where `a i` divides `a (suc i)` (used for `divChain`).

#### Natural Number Instance

- **`NatEuclidean`**: `EuclideanSemiringData` instance on `Nat` using `euclideanMap = id` and the standard `Nat.divMod`.
- **`NatEuclidean.gcd0_`**: `gcd 0 x = x` in the Nat instance.

#### Integer Instance

- **`IntEuclidean`**: `EuclideanRingData` instance on `Int`. Uses `iabs` as the Euclidean map; `divMod x y` computes the unsigned `Nat.divMod (iabs x) (iabs y)` and re-attaches signs via `signum`.
- **`IntEuclidean.modToNat`**: Normalizes a `(quotient, remainder)` pair so the remainder is a `Nat`, adjusting the quotient by `signum m` when the raw remainder is negative.
- **`IntEuclidean.natDivMod`**: `divMod` returning a `Nat`-valued remainder via `modToNat`.
- **`div`**, **`mod`**: Integer division and natural-number-valued remainder (`x div y : Int`, `x mod y : Nat`).
- **`intMod<right`**: `iabs (divMod x (suc n)).2 < suc n`.
- **`natMod<right`**: `x mod suc n < suc n`.
- **`divModProp`**: `y * (x div y) + x mod y = x` for `y /= 0`.
- **`natDivModProp`**: Same identity specialized to a positive `suc n` divisor.
- **`mod-unique`**: Uniqueness of remainder: if `pos n * q + r = pos n * q' + r'` with both remainders `< n`, then `r = r'`. Uses internal helper `aux` for the additive rearrangement.
- **`natMod=mod'`**, **`natMod=mod`**: Bridge between the Euclidean instance's `mod` and `Nat.mod`.

- **`IntDomain`**: `EuclideanDomain` instance on `Int`, exhibiting `IntRing` as a Euclidean (and hence PID/Bezout) domain.

#### Nat ↔ Int Bridges for Divisibility

- **`ldiv_nat_int`**: Lifts `LDiv` from `Nat` to `Int` via `pos`.
- **`ldiv_int_nat`**: Pushes `LDiv` from `Int` to `Nat` via `iabs`.
- **`inv_nat_int`**, **`inv_int_nat`**: Same lifts/pushdowns for `Inv` (units).
- **`inv_abs`**: If `iabs x` is a unit then so is `x`.

#### GCD Bridges

- **`gcd_nat_int`**: Promotes a `GCD` over `Nat` to a `GCD` over `Int` (using `pos`).
- **`gcd_int_nat`**: Reflects a `GCD` over `Int` to one over `Nat` (using `iabs`).
- **`nat_gcd-isUnique`**: GCDs in `Nat` are equal on the nose.
- **`int_gcd-isUnique`**: GCDs in `Int` agree up to absolute value.
- **`int_gcd_pos`**: `gcd (pos n) (pos m) = pos (gcd n m)`, with internal `aux` showing the fueled GCD on positives stays nonnegative.
- **`nat_gcd_sum`**: `gcd (a + b * d) b = gcd a b` (translation invariance by multiples).
- **`nat_gcd_*_div`**: Coprime cancellation: if `a | b * c` and `gcd a b = 1`, then `a | c`. Derived by lifting to `Int` and applying the domain-level coprime division lemma.
- **`nat_gcd-comm`**: Commutativity of `gcd` on `Nat`.
