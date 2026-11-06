### Algebra.Semiring.SubSemiring

- **`SubPseudoSemiring`**: Class of subsets of a `PseudoSemiring` closed under addition, zero, and multiplication. Extends `SubAddMonoid` and `SubSemigroup`, overriding the ambient structure `S` to a `PseudoSemiring`.
- **`SubSemiring`**: Class of subsets of a `Semiring` closed under addition, zero, multiplication, and one. Extends `SubPseudoSemiring` and `SubMonoid`, overriding `S` to a `Semiring`.
- **`SubSemiring.max`**: The maximal sub-semiring of any `Semiring` `A`, containing every element; built from `SubAddMonoid.max` and `SubMonoid.max`.

#### Induced Semiring Structures

- **`IPseudoSemiring`**: The instance making a `SubPseudoSemiring` into a `PseudoSemiring` on its carrier, from `IAbMonoid` and `ISemigroup`, with distributivity and zero-absorption inherited via `ext`.
- **`IPseudoCSemiring`**: The induced pseudo-semiring of a sub-pseudo-semiring of a commutative one is again commutative (`PseudoCSemiring`).
- **`ISemiring`**: The instance making a `SubSemiring` into a `Semiring` on its carrier, from `IPseudoSemiring` and `IMonoid`; `ISemiring.embed` is the inclusion as a `SemiringHom`.
- **`ICSemiring`**: The induced semiring of a sub-semiring of a commutative semiring is again commutative (`CSemiring`).

#### Images of Semiring Homomorphisms

- **`ImageSemiring`**: The image of a `SemiringHom f` as a `SubSemiring f.Cod`, built from `ImageAddMonoid` and `ImageMonoid`.
- **`ImSemiring`**: The image of `f` regarded as a semiring, `ISemiring (ImageSemiring f)`.
- **`ImSemiringLeftHom`**: The corestriction `SemiringHom f.Dom (ImSemiring f)` of `f` onto its image.
