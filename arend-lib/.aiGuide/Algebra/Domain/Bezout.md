### Algebra.Domain.Bezout

Bezout rings and domains: commutative rings where every pair of elements admits a Bezout identity, hence is a GCD domain with effective linear combinations.

A `BezoutRing` is a commutative ring satisfying `IsBezout`: for all `a, b` there exist `s, t` and divisibility witnesses making `s * a + t * b` a GCD of `a` and `b`. From this single axiom the module derives the full GCD-monoid structure, distributivity of GCD over multiplication, and a suite of consequences (Bezout for arrays, coprimality lemmas, Chinese remainder theorems, and the construction of maximal ideals via countable enumeration). `StrictBezoutRing` strengthens the axiom to `IsStrictBezout` (yielding an explicit common cofactor decomposition `a = u * d`, `b = v * d` with `s * u + t * v = 1`), and the domain variants combine these with `GCDDomain` to obtain a Bezout domain whose decidable specialization is automatically a strict Bezout domain.

#### Main Classes

- **`BezoutRing`**: Commutative ring extending `CRing` and `GCDMonoid`, axiomatized by `isBezout : IsBezout`. Derives `isGCD` and `gcd-ldistr` (left-distributivity of GCD over multiplication) from the Bezout identity.
- **`StrictBezoutRing`**: Extends `BezoutRing` with `isStrictBezout : IsStrictBezout`, providing strict Bezout coefficients `(s, t, u, v)` with `s * u + t * v = 1` and `a = u * d`, `b = v * d`; derives `isBezout` from this stronger form.
- **`BezoutDomain`**: Extends `GCDDomain` and `BezoutRing`; `isGCDDomain` is automatic via `bezoutGCD` applied to the Bezout witnesses.
- **`StrictBezoutDomain`**: Extends both `BezoutDomain` and `StrictBezoutRing`.
- **`BezoutDomain.Dec`**: Decidable Bezout domain extending `StrictBezoutDomain` and `GCDDomain.Dec`; uses `PPRing.bezout->strictBezout` to upgrade Bezout to strict Bezout in the decidable setting.

#### Bezout Identities

- **`gcd_bezout`**: From a `GCD a b c` extracts `s, t` with `s * a + t * b = c`, the classical Bezout identity for any GCD witness.
- **`bezoutArray_LDiv`**: For any array `l`, produces coefficients `c` such that `BigSum (c i * l i)` divides every `l j` — the array generalization of the Bezout combination.
- **`coprime_bezoutArray`**: When an array is coprime (`IsCoprimeArray`), produces `s` with `BigSum (s j * a j) = 1`.
- **`bezoutArray`** (in `StrictBezoutRing`): For nonempty arrays, gives a strict Bezout decomposition: coefficients `s`, cofactors `u` with `BigSum (s j * u j) = 1`, and a common divisor `d` such that `a j = u j * d` for all `j`.

#### Chinese Remainder Theorems

- **`chinese2`**: Two-modulus CRT: given coprime `d1`, `d2` and targets `a1`, `a2`, produces `r` with `d1 ∣ (r - a1)` and `d2 ∣ (r - a2)`.
- **`chinese`**: General CRT: for `n` pairwise coprime moduli `d` and targets `a`, produces a simultaneous solution `r` with `d j ∣ (r - a j)` for every `j`.
- **`chinese-unique`**: Uniqueness companion to `chinese`: if every `d j` divides `r - r'`, then their product `BigProd d` divides `r - r'`.

#### Maximal Ideal Construction

- **`maximal-ideal`**: Given a countable enumeration of the ring, decidable divisibility, and an array `l` that is not coprime, constructs a maximal ideal `M` containing every `l j`. Uses `Ideal.countable-maximal` together with `bezout_finitelyGenerated_principal` to reduce the membership decision for finitely generated ideals to divisibility by a single generator.

#### Coprimality Lemmas (in `\where`)

- **`bezoutArray_coprime`**: Witnessing coefficients summing to `1` certify `IsCoprimeArray u`.
- **`bezoutArrayInv_coprime`**: Same conclusion when the sum is merely invertible rather than equal to `1`.
- **`bezout_coprime`**: From `s * a + t * b = 1` conclude `IsCoprime a b`.
- **`bezoutInv_coprime`**: From `Monoid.Inv (s * a + t * b)` conclude `IsCoprime a b`.
