### Algebra.Semiring

Defines semiring algebraic structures: rings without additive inverses, combining additive commutative monoids with multiplicative (commutative) monoids/semigroups linked by distributivity.

#### Core Classes

- **`PseudoSemiring`**: Extends `AbMonoid` and `Semigroup`. A semiring without a multiplicative identity, requiring left and right distributivity (`ldistr`, `rdistr`) and that zero is absorbing on both sides (`zro_*-left`, `zro_*-right`).
- **`Semiring`**: Extends `PseudoSemiring` and `Monoid`. Adds a multiplicative identity along with a natural number coefficient embedding `natCoef : Nat -> E` satisfying `natCoefZero` (`natCoef 0 = zro`) and `natCoefSuc` (`natCoef (suc n) = natCoef n + ide`); default implementations are provided.
- **`NonZeroSemiring`**: Extends `Semiring` with the nontriviality axiom `zro/=ide` (`zro /= ide`).

#### Commutative Variants

- **`PseudoCSemiring`**: Extends `PseudoSemiring` and `CSemigroup`. Commutative pseudo-semiring; derives `rdistr` from `ldistr` (and vice versa) and `zro_*-right` from `zro_*-left` (and vice versa) using multiplicative commutativity.
- **`CSemiring`**: Extends `Semiring`, `PseudoCSemiring`, and `CMonoid`. A commutative semiring with multiplicative identity.
