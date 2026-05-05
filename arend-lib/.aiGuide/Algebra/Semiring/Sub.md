### Algebra.Semiring.Sub

Sub-structures of (pseudo) semirings, combining sub-additive-monoid and sub-multiplicative-semigroup/monoid structure.

#### Sub-Pseudo-Semirings

- **`SubPseudoSemiring`**: Sub-pseudo-semiring of a `PseudoSemiring`, extending `SubAddMonoid` and `SubSemigroup` with `S : PseudoSemiring`.
- **`SubPseudoSemiring.cStruct`**: Builds a `PseudoCSemiring` on the carrier of a `SubPseudoSemiring` of a `PseudoCSemiring`, inheriting `*-comm` pointwise.

#### Sub-Semirings

- **`SubSemiring`**: Sub-semiring of a `Semiring`, extending `SubPseudoSemiring` and `SubMonoid` with `S : Semiring`.
- **`SubSemiring.cStruct`**: Builds a `CSemiring` on the carrier of a `SubSemiring` of a `CSemiring`, combining the inherited `Semiring` with `SubPseudoSemiring.cStruct`.
- **`SubSemiring.max`**: The maximal sub-semiring of a `Semiring` `A`, built from the maximal `SubAddMonoid` and maximal `SubMonoid`.
