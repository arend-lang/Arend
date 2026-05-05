### Algebra.Domain.Bezout

Bezout rings and domains: commutative rings where every pair of elements has a linear combination realizing their GCD.

#### Bezout Rings

- **`BezoutRing`**: Extends `CRing` and `GCDMonoid`. A commutative ring satisfying `IsBezout`: for any `a, b` there exist `s, t` such that `s * a + t * b` is a GCD of `a` and `b`. Automatically yields `isGCD` and `gcd-ldistr` from the Bezout property.
- **`StrictBezoutRing`**: Extends `BezoutRing`. Strengthens the Bezout property to `IsStrictBezout`: the witnesses `s, t, u, v` satisfy explicit divisibility relations `a = u * d`, `b = v * d` with `s * u + t * v = 1`. Derives `isBezout` from `isStrictBezout`.

#### Bezout Domains

- **`BezoutDomain`**: Extends `GCDDomain` and `BezoutRing`. A Bezout ring that is also an integral domain; `isGCDDomain` is derived by extracting the GCD from the Bezout combination on nonzero elements.
- **`BezoutDomain.Dec`**: Extends `StrictBezoutDomain` and `GCDDomain.Dec`. A Bezout domain with decidable equality; lifts `isBezout` to `isStrictBezout` via `PPRing.bezout->strictBezout`.
- **`StrictBezoutDomain`**: Extends `BezoutDomain` and `StrictBezoutRing`. A Bezout domain whose Bezout property is strict.

#### Coprimality from Bezout Identities

- **`bezoutArray_coprime`**: Given `s : Array R n` and `u : Array R n` with `BigSum (\lam j => s j * u j) = 1`, the family `u` is coprime (`IsCoprimeArray u`).
- **`bezoutArrayInv_coprime`**: Variant where the linear combination is a unit (`Monoid.Inv`) rather than equal to `1`.
- **`bezout_coprime`**: From `s * a + t * b = 1`, concludes `IsCoprime a b`.
- **`bezoutInv_coprime`**: Variant where `s * a + t * b` is a unit.
