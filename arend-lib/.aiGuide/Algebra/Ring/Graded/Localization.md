### Algebra.Ring.Graded.Localization

Localization of a graded commutative ring at a multiplicative subset, restricted to homogeneous fractions of equal degree.

This module constructs the homogeneous localization `HomogenLocRing S`, the subring of `LocRing S` consisting of fractions `a/b` where the numerator and denominator are homogeneous of the same degree. This construction is fundamental in algebraic geometry — it underlies the definition of `Proj` and the structure sheaf on projective schemes, where one needs to extract degree-zero pieces from localizations of graded rings. The module packages the subring data as a `CRing` via `SubRing.cStruct` and provides a constructor `fromSType` that builds elements from raw homogeneity witnesses.

#### Main Construction

- **`HomogenLocRing`**: Given a graded commutative ring `R : GradedCRing` and a submonoid `S : SubMonoid R`, the commutative ring of homogeneous fractions in `LocRing S` — pairs `(a, b)` with `b ∈ S` where both `a` and `b` are homogeneous of the same degree `n`. Built as `SubRing.cStruct subRing`.

#### Subring Definition

- **`subRing`**: The `SubRing (LocRing S)` whose carrier predicate asserts that an element `x` admits a representative `y : LocRing.SType S` together with a common degree `n` such that both `y.1` (numerator) and `y.2` (denominator) are homogeneous of degree `n`. Closure under zero, addition, identity, multiplication, and negation makes this a subring.

#### Constructors

- **`fromSType`**: Builds an element of `HomogenLocRing S` directly from a representative `y : LocRing.SType S` together with proofs `y1h : isHomogen y.1 n` and `y2h : isHomogen y.2 n`. Returns the pair `(inl~ y, witness)` where the witness packages the homogeneity data.
- **`fromSType.equality`**: Lemma showing that for any `x : LocRing S` with `x = inl~ y`, the element `fromSType y y1h y2h` equals the explicit pair `(x, inP (y, x=[y], n, y1h, y2h))`. Used to rewrite `fromSType`-built elements in terms of arbitrary equal representatives.
