### Algebra.Ring.Graded.Localization

Constructs the homogeneous localization of a graded commutative ring at a submonoid, consisting of fractions where numerator and denominator share the same grading degree.

#### Homogeneous Localization

- **`HomogenLocRing`**: Given a graded commutative ring `R : GradedCRing` and a submonoid `S : SubMonoid R`, builds the commutative ring of homogeneous fractions as a subring of `LocRing S`. Returns a `CRing` via `SubRing.cStruct`.

#### Subring Structure

- **`HomogenLocRing.subRing`**: The underlying `SubRing (LocRing S)` whose elements are localization classes `inl~ y` for which numerator `y.1` and denominator `y.2` are both homogeneous of the same degree `n`. Provides closure proofs for `0`, `1`, addition, multiplication, and negation, computing the appropriate combined degrees (e.g., degree `n + m` for sums and products).

#### Constructors

- **`fromSType`**: Builds an element of `HomogenLocRing S` from a localization pair `y : LocRing.SType S` together with witnesses `y1h : isHomogen y.1 n` and `y2h : isHomogen y.2 n` that both components are homogeneous of degree `n`.
- **`fromSType.equality`**: Lemma showing that `fromSType y y1h y2h` equals the pair `(x, ...)` whenever `x = inl~ y` in the localization, used to rewrite homogeneous-localization elements in terms of their representing fractions.
