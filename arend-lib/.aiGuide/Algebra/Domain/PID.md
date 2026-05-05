### Algebra.Domain.PID

Principal ideal domains and the theory of adequate elements used to derive Smith normal form from the noetherian/Bezout structure.

#### Main Class

- **`PID`**: Principal Ideal Domain, extending `SmithDomain` and `NoetherianCRing`. Requires a divisibility chain condition `divChain` on the monoid of nonzero elements (modulo units). Derives `isNoetherian` from the chain condition via Bezout-implies-finitely-generated-is-principal, and derives Kaplansky's property `isKaplansky` from adequacy.

#### Adequate Elements

- **`IsAdequate`**: For `a : M` in a commutative monoid, asserts that for every `b` there exists a maximal divisor `c` of `a` coprime to `b` (universal among such divisors).
- **`IsAdequate'`**: Weaker form: for every `b`, `a` factors as `a = a1 * a2` where `a1` divides `pow b n` for some `n`, and `a2` is coprime to `b`.
- **`adequate'_adequate`**: In a `GCDMonoid`, `IsAdequate'` implies `IsAdequate`.

#### Adequacy from Chain Conditions

- **`unitless_adequate'`**: In a `UnitlessGCDMonoid` with a divisibility chain condition, every element satisfies `IsAdequate'`.
- **`monoid_adequate'`**: In a `CancelGCDMonoid` whose `DivQuotientMonoid` has the chain condition, every element satisfies `IsAdequate'`.
- **`domain_adequate'`**: In a decidable GCD domain whose nonzero quotient monoid has the chain condition, every nonzero element satisfies `IsAdequate'`. Used to instantiate Kaplansky's property in `PID`.
- **`oneDim_adequate'`**: In a decidable GCD domain of Krull dimension ≤ 1, every nonzero element satisfies `IsAdequate'`.
- **`bezout_1Dim<->adequate'`**: For decidable Bezout domains, having Krull dimension ≤ 1 is equivalent to every nonzero element being adequate.

#### Kaplansky from Adequacy

- **`adequate_kaplansky`**: In a decidable Bezout domain, if every nonzero element is `IsAdequate`, then the ring satisfies Smith's Kaplansky condition (`SmithRing.IsKaplansky`), enabling Smith normal form for matrices.
