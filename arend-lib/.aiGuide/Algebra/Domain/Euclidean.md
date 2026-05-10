### Algebra.Domain.Euclidean

Euclidean semirings, rings, and domains: structures equipped with a Euclidean function (size measure) supporting division with remainder, from which GCDs, Bézout coefficients, and PID structure are derived.

This module formalizes the Euclidean algorithm constructively using a "fuel" parameter (a natural number bounding the recursion depth) so that recursion is structurally well-founded; the fuel is supplied as `suc' (euclideanMap b)` to guarantee termination. The semiring layer (`EuclideanSemiringData`) builds GCDs and a "reduce" pair giving cofactors `(a/g, b/g)` directly from `divMod`, while the ring layer (`EuclideanRingData`) additionally produces Bézout coefficients. `EuclideanDomain` packages this into a PID via a truncated existence of the Euclidean structure, and the file closes by realizing `Nat` and `Int` as instances and transporting GCD/divisibility facts between them via `iabs` and `pos`.

#### Euclidean Semiring Structure

- **`EuclideanSemiringData`**: Class extending `CSemiring` and `DecSet` with a Euclidean size `euclideanMap : E -> Nat`, division-with-remainder `divMod`, the equation `y * q + r = x`, the size-decrease law `euclideanMap r < euclideanMap y` when `y, r ≠ 0`, and `summandDiv` (cancellation: if `d * x + y = d * z` then `d ∣ y`).
- **`suc'`**: Helper sending `0 ↦ 1` and `suc n ↦ suc (suc n)`, used to provide fuel that strictly exceeds `euclideanMap b`.
- **`suc'=suc`**: `suc' n = suc n` for `n` viewed as the appropriate successor.

#### GCD via Fueled Recursion

- **`gcd-fueled`**: Recursive Euclidean algorithm with explicit fuel `s`; returns `a` when `b = 0` or fuel is exhausted, otherwise recurses on `(b, (divMod a b).2)`.
- **`gcd`**: Top-level GCD `gcd a b := gcd-fueled (suc' (euclideanMap b)) a b`.
- **`fueled_zro`**: `gcd-fueled s a 0 = a`.
- **`gcd_0`** (with **`aux`**): `gcd x 0 = x`.
- **`gcd-isGCD-fueled`**: If `g ∣ a` and `g ∣ b`, then `g ∣ gcd-fueled s a b`; the universality side of the GCD property.
- **`gcd-isGCD`**: Packages `gcd a b` as a full `GCD a b` record, supplying both divisibility witnesses (via `reduce`) and universality.

#### Cofactor Reduction

- **`reduce-fueled`**: Computes the pair of cofactors `(a/gcd, b/gcd)` in lockstep with `gcd-fueled`, accumulating quotients along the recursion.
- **`reduce`**: Top-level cofactor pair `reduce a b := reduce-fueled (suc' (euclideanMap b)) a b`.
- **`reduce_zro`**: `reduce-fueled s a 0 = (1, 0)`.
- **`reduce*fueled-left`** / **`reduce*fueled-right`**: `(reduce-fueled s a b).1 * gcd-fueled s a b = a` and the analogue for `b` (with sufficient fuel).
- **`reduce*gcd-right`**: `(reduce a b).2 * gcd a b = b`.

#### Pseudonorm and Unit Decidability

- **`isPseudonorm`** (with **`aux`**): Given `b ∣ a` and `a ≠ 0`, decides between `a ∣ b` (so `a` and `b` are associates) and the existence of an associate `b'` of `b` with strictly smaller Euclidean size than `a`; the engine for inducting on `euclideanMap`.
- **`unit-dec`**: Decides invertibility of `a`, given that any inverse-witnessing element of nonzero `b` has `euclideanMap a <= euclideanMap b`.

#### Euclidean Rings and Bézout

- **`EuclideanRingData`**: Class extending `EuclideanSemiringData` and `CRing`; in a ring, `summandDiv` is automatic (the inverse witness is `z - x`).
- **`bezout-fueled`**: Extended Euclidean algorithm with fuel; returns coefficients `(u, v)` mirroring the recursion of `gcd-fueled`.
- **`bezout`**: Top-level Bézout coefficients `bezout a b := bezout-fueled (suc' (euclideanMap b)) a b`.
- **`bezoutIdentity-fueled`** / **`bezoutIdentity`**: `(bezout a b).1 * a + (bezout a b).2 * b = gcd a b`.

#### Euclidean Domains

- **`EuclideanDomain`**: Class extending `PID`, requiring `isEuclidean : TruncP (EuclideanRingData ...)`. Implements `isBezout` directly from `bezoutIdentity`, and `divChain` (ascending-chain termination) by induction on `euclideanMap`.
- **`aux`** (in `EuclideanDomain`): Helper for `divChain`; given a chain of nonzero elements with `(a (suc i)).1 ∣ (a i).1` and `euclideanMap b < n`, produces some index where the divisibility becomes a two-sided association.

#### Nat as Euclidean Semiring

- **`NatEuclidean`**: Instance making `Nat` a `EuclideanSemiringData` with `euclideanMap = id` and the standard `divMod` from `Arith.Nat`; `summandDiv` is proved via uniqueness of `mod`.
- **`NatEuclidean.gcd0_`**: `NatEuclidean.gcd 0 x = x`.

#### Int as Euclidean Ring

- **`IntEuclidean`**: Instance making `Int` a `EuclideanRingData` with `euclideanMap = iabs`; division/remainder are obtained from `Nat`'s `divMod` on `iabs`, then re-signed via `signum`.
- **`IntEuclidean.modToNat`**: Normalizes a quotient/remainder pair so the remainder lies in `Nat`, adjusting the quotient by `signum m` when necessary.
- **`IntEuclidean.natDivMod`**: Variant of `divMod` returning `(Int, Nat)`.
- **`IntEuclidean.div`** / **`IntEuclidean.mod`**: Integer quotient (returning `Int`) and remainder (returning `Nat`).
- **`intMod<right`** / **`natMod<right`**: Bounds `iabs (divMod x (suc n)).2 < suc n` and `x mod suc n < suc n`.
- **`divModProp`** / **`natDivModProp`**: `y * (x div y) + x mod y = x` when `y ≠ 0`, and the variant with `pos (suc n)`.
- **`mod-unique`** (with **`aux`**): Uniqueness of the remainder under `pos n * q + r = pos n * q' + r'` with `r, r' < n`.
- **`natMod=mod'`** / **`natMod=mod`**: Compatibility between integer `mod` and `Nat.mod` on nonnegative inputs.

#### Int as Euclidean Domain

- **`IntDomain`**: Instance making `Int` a `EuclideanDomain`; supplies `zro#ide`, the apartness multiplicativity `#0-*`, and packages `IntEuclidean` into `isEuclidean`.

#### Divisibility Transport between Nat and Int

- **`ldiv_nat_int`** / **`ldiv_int_nat`**: Transport `LDiv` between `NatSemiring` and `IntRing` via `pos` and `iabs`.
- **`inv_nat_int`** / **`inv_int_nat`** / **`inv_abs`**: Analogous transports for invertibility, including the converse `Inv (iabs x) -> Inv x`.
- **`gcd_nat_int`** / **`gcd_int_nat`**: Transport `GCD` records between `Nat` and `Int`, adjusting cofactors by `signum`.

#### GCD Uniqueness and Computational Lemmas

- **`nat_gcd-isUnique`**: Any two `GCD n m` records over `Nat` agree as `Nat`s.
- **`int_gcd-isUnique`**: Any two `GCD x y` records over `Int` agree up to absolute value.
- **`int_gcd_pos`** (with **`aux`**): `gcd (pos n) (pos m) = pos (gcd n m)` — the integer GCD on nonnegative inputs reduces to the natural one.
- **`nat_gcd_sum`**: `gcd (a + b * d) b = gcd a b` — invariance of GCD under adding multiples.
- **`nat_gcd_*_div`**: Euclid's lemma for `Nat`: if `a ∣ b * c` and `gcd a b = 1`, then `a ∣ c`; proved by transporting through `IntDomain.coprime_*_div`.
- **`nat_gcd-comm`**: Commutativity `gcd a b = gcd b a` on `Nat`.
