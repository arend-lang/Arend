### Algebra.Semiring.Sub

Sub-semirings: subsets of a (pseudo-)semiring closed under addition, multiplication, zero, and one, inheriting the semiring structure.

This module combines `SubAddMonoid` (closure under addition and zero) with `SubSemigroup`/`SubMonoid` (closure under multiplication and one) to produce sub-semirings. Each sub-structure carries an induced semiring `I...` on the underlying subset, and commutative variants are provided via `cStruct` constructions in the `\where` blocks. The maximal sub-semiring (the full carrier) is given as a canonical example.

#### Sub-Pseudo-Semirings

- **`SubPseudoSemiring`**: Class of subsets of a `PseudoSemiring` closed under addition, zero, and multiplication. Extends `SubAddMonoid` and `SubSemigroup`, overriding the ambient structure `S` to a `PseudoSemiring`.
- **`SubPseudoSemiring.IPseudoSemiring`**: The induced `PseudoSemiring` structure on the subset, combining the sub-abelian-monoid structure with the sub-semigroup structure and inheriting distributivity and zero-absorption laws.
- **`SubPseudoSemiring.cStruct`**: Given a `PseudoCSemiring` `R` and a sub-pseudo-semiring `S` of `R`, produces the induced `PseudoCSemiring` structure on `S` by inheriting commutativity of multiplication.

#### Sub-Semirings

- **`SubSemiring`**: Class of subsets of a `Semiring` closed under addition, zero, multiplication, and one. Extends `SubPseudoSemiring` and `SubMonoid`, overriding `S` to a `Semiring`.
- **`SubSemiring.ISemiring`**: The induced `Semiring` structure on the subset, combining `IPseudoSemiring` with the multiplicative `IMonoid` structure (adding the unit).
- **`SubSemiring.cStruct`**: Given a `CSemiring` `R` and a sub-semiring `S`, produces the induced `CSemiring` structure on `S` by combining `ISemiring` with the commutative pseudo-semiring structure.
- **`SubSemiring.max`**: The maximal sub-semiring of any `Semiring` `A`, containing every element; built from `SubAddMonoid.max` and `SubMonoid.max`.
