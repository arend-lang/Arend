### Algebra.Domain.PID

Principal ideal domains: integral domains in which every finitely generated ideal is principal and the divisibility chain condition holds.

A `PID` is presented as the intersection of `SmithDomain` (Bezout + adequacy giving Smith normal form) and `NoetherianCRing`, with the Noetherian property derived from a divisibility chain condition on the quotient monoid of nonzero elements modulo units. The module's key technical contribution is the notion of an *adequate* element (and its variant `IsAdequate'`), which captures the ability to extract a coprime factorization with respect to any other element. Through a chain of lemmas, divisibility chains in GCD domains imply `IsAdequate'`, which implies `IsAdequate`, which implies Kaplansky's condition needed for Smith normal form. The module also establishes that Bezout domains of Krull dimension ≤ 1 are exactly those satisfying adequacy on nonzero elements, giving a clean bridge between dimension theory and the Smith decomposition.

#### Main Class

- **`PID`**: Principal ideal domain, extending `SmithDomain` and `NoetherianCRing`. Takes a divisibility chain condition `divChain` on the quotient monoid of nonzero elements, and derives Noetherianness via `Ideal.fromMonoidChainCondition` and Kaplansky's condition via the adequacy chain.
- **`PID.is1Dim`**: A PID has Krull dimension at most 1.

#### Adequate Elements

- **`IsAdequate`**: Predicate on `a : M` saying that for every `b`, there exists a divisor `c` of `a` coprime to `b` and universal among such divisors (every divisor of `a` coprime to `b` divides `c`).
- **`IsAdequate'`**: Weaker variant: for every `b`, `a` factors as `a1 * a2` where `a1` divides some power `b^n` and `a2` is coprime to `b`.
- **`adequate'_adequate`**: In a `GCDMonoid`, `IsAdequate'` implies `IsAdequate`.

#### Adequacy from Divisibility Chains

- **`unitless_adequate'`**: Every element of a `UnitlessGCDMonoid` with the divisibility chain condition is `IsAdequate'`.
- **`monoid_adequate'`**: Lifts the previous result to a `CancelGCDMonoid` via its quotient monoid `DivQuotientMonoid`.
- **`domain_adequate'`**: For a decidable GCD domain with the divisibility chain condition on nonzero elements modulo units, every nonzero element is `IsAdequate'`.

#### Dimension and Adequacy

- **`oneDim_adequate'`**: In a decidable GCD domain of Krull dimension ≤ 1, every nonzero element is `IsAdequate'`.
- **`bezout_1Dim<->adequate'`**: For a decidable Bezout domain, Krull dimension ≤ 1 is equivalent to `IsAdequate'` holding on all nonzero elements.

#### Adequacy Implies Kaplansky

- **`adequate_kaplansky`**: In a decidable Bezout domain, if every nonzero element is `IsAdequate`, then `SmithRing.IsKaplansky` holds, providing the input needed for Smith normal form reduction.
