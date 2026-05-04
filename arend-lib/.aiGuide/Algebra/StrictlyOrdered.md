### Algebra.StrictlyOrdered

Hierarchy of strictly ordered algebraic structures, from ordered additive monoids up through ordered fields and algebras, axiomatized in terms of a strict order `<` (with positivity predicates for groups/rings).

#### Ordered Additive Monoids

- **`OrderedAddMonoid`**: Strict poset compatible with addition. Extends `StrictPoset` and `AddMonoid`. Provides **`<_+-left`** and **`<_+-right`**: addition on either side preserves strict order.
- **`OrderedAbMonoid`**: Commutative version. Extends `OrderedAddMonoid` and `AbMonoid`; derives `<_+-right` from `<_+-left` via commutativity.
- **`BiorderedLatticeAbMonoid`**: Lattice-ordered abelian monoid. Extends `BiorderedLattice`, `JoinSemilatticeAbMonoid`, `MeetSemilatticeAbMonoid`, `AbMonoid`.
- **`LinearlyBiorderedAbMonoid`**: Linearly ordered (totally ordered) lattice-ordered abelian monoid. Extends `BiorderedLatticeAbMonoid` and `LinearLattice`.
- **`LinearlyOrderedAbMonoid`**: Linearly ordered abelian monoid with cancellation. Extends `LinearlyBiorderedAbMonoid` and `OrderedAbMonoid`. Provides **`<_+-cancel-left`** / **`<_+-cancel-right`**: strict order is reflected by addition. Derives `<=_+`, `meet_+-left`, `join_+-left` (compatibility of `+` with order, meet, and join).

#### Ordered Additive Groups

- **`PreorderedAddGroup`**: Additive group with a positivity predicate. Extends `AddGroup`. Fields: **`isPos : E -> \Prop`**, **`zro/>0`** (zero is not positive), **`positive_+`** (sum of positives is positive).
- **`OrderedAddGroup`**: Defines the strict order via positivity: `x < y := isPos (y - x)`. Extends `OrderedAddMonoid` and `PreorderedAddGroup`. Provides **`isNeg x := isPos (negative x)`** and the type-level operator **`<`** in its `\where` block.
- **`OrderedAbGroup`**: Commutative ordered group. Extends `OrderedAbMonoid`, `OrderedAddGroup`, `AbGroup`.
- **`LinearlyOrderedAbGroup`**: Linearly ordered abelian group with absolute value. Extends `OrderedAbGroup`, `LinearlyOrderedAbMonoid`, `AbsAbGroup`. Adds **`<_+-comparison`** (positivity of a sum splits over the summands), **`<_+-connectedness`** (neither positive nor negative implies zero). Derives cancellation, comparison, connectedness, and `abs>=0`.

#### Ordered Semirings

- **`OrderedSemiring`**: Semiring with order compatible with multiplication. Extends `Semiring` and `OrderedAbMonoid`. Fields: **`zro<ide`**, **`<_*_positive-left`** / **`<_*_positive-right`** (multiply by positive preserves `<`), **`<_*_negative-left`** / **`<_*_negative-right`** (multiply by negative reverses `<`).
- **`LinearlyOrderedSemiring`**: Adds cancellation. Extends `OrderedSemiring`, `LinearlyOrderedAbMonoid`, `PosetSemiring`. Provides **`<_*-cancel-left`** / **`<_*-cancel-right`**: from `x*y < x*z` deduce a sign for `x` together with the corresponding ordering of `y, z`. Derives `zro<=ide` and `<=_*_positive-{left,right}`.
- **`OrderedCSemiring`**: Commutative version. Extends `OrderedSemiring`, `CSemiring`, `OrderedAbMonoid`; derives the right-multiplication laws from the left ones via `*-comm`.
- **`LinearlyOrderedCSemiring`**: Linearly ordered commutative semiring. Extends `LinearlyOrderedSemiring` and `OrderedCSemiring`.
- **`LinearlyOrderedCSemiring.Dec`**: Decidable variant. Extends `OrderedCSemiring` and `LinearlyOrderedSemiring.Dec`.

#### Ordered Rings

- **`OrderedRing`**: Linearly ordered domain. Extends `Domain`, `LinearlyOrderedSemiring`, `LinearlyOrderedAbGroup`, `PosetRing`. Key fields: **`ide>zro`**, **`positive_*`** (product of positives), **`pos_*-cancel-left`** / **`pos_*-cancel-right`**, **`positive=>#0`** / **`negative=>#0`** (positive/negative implies apart from zero), **`#0=>eitherPosOrNeg`** (apart from zero implies positive or negative). Derives **`positive_*-cancel`**, **`negative_*-cancel`**, **`positive_negative_*`**, **`negative_positive_*`**, **`<_*_negative`** (product of two negatives is positive), and all operations of `<_*` and apartness (`#0`).
- **`OrderedRing.Dec`**: Decidable ordered ring. Extends `Domain.Dec`, `OrderedRing`, `LinearlyOrderedSemiring.Dec`. Adds **`+_trichotomy`** (every element is `<`, `=`, or `>` zero) from which `trichotomy`, `<_+-comparison`, `<_+-connectedness`, `pos_*-cancel-left`, and `nonZeroApart` are derived.
- **`OrderedCRing`**: Commutative ordered ring. Extends `OrderedRing`, `IntegralDomain`, `LinearlyOrderedCSemiring`; derives `pos_*-cancel-right` from the left version.
- **`OrderedCRing.Dec`**: Decidable commutative ordered ring. Extends `OrderedRing.Dec`, `OrderedCRing`, `LinearlyOrderedCSemiring.Dec`, `IntegralDomain.Dec`.

#### Ordered Fields

- **`OrderedField`**: Ordered commutative ring that is a field. Extends `OrderedCRing` and `Field`. Derives `zro/=ide`, **`locality`** (every `x` satisfies `#0 (x+1)` or `isNeg x`), `pos_*-cancel-left` (using inverses), and `negative=>#0` (via `Ring.negative_inv`).
- **`DiscreteOrderedField`**: Decidable ordered field. Extends `OrderedCRing.Dec`, `OrderedField`, `DiscreteField`. Derives `positive=>#0` and `+_trichotomy` from `eitherZeroOrInv`.

#### Ordered Algebras

- **`OrderedAAlgebra`**: Associative algebra over an ordered commutative ring. Extends `AAlgebra` and `OrderedRing`; overrides `R` to `OrderedCRing`. Fields **`coef_<`** / **`coef_<-inv`**: the coefficient embedding `coefMap` reflects and preserves strict order.
- **`OrderedCAlgebra`**: Commutative ordered algebra. Extends `OrderedAAlgebra`, `CAlgebra`, `OrderedCRing`; overrides `R` to `OrderedCRing`.
- **`OrderedFieldAlgebra`**: Ordered algebra whose total ring is an ordered field. Extends `OrderedCAlgebra` and `OrderedField`.
