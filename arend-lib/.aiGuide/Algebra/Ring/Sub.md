### Algebra.Ring.Sub

Subrings of (pseudo) rings: predicates closed under ring operations, with constructions for commutative variants and ring homomorphism images.

#### Subring Classes

- **`SubPseudoRing`**: Subset of a `PseudoRing` closed under addition, negation, and multiplication. Extends `SubPseudoSemiring` and `SubAddGroup`.
- **`SubRing`**: Subset of a `Ring` (with unit) closed under all ring operations, including `1`. Extends `SubPseudoRing` and `SubSemiring`.
- **`CSubRing`**: Subring of a commutative ring `CRing`. Extends `SubRing`.

#### Commutative Structure Promotion

- **`SubPseudoRing.cStruct`**: Promotes a `SubPseudoRing` of a `PseudoCRing` to a `PseudoCRing` structure on the induced subring, transporting `*-comm`.
- **`SubRing.cStruct`**: Promotes a `SubRing` of a `CRing` to a `CRing` structure, layering on `SubPseudoRing.cStruct`.

#### Constructions

- **`SubRing.max`**: The maximal subring of a ring `R` (containing all elements), built from `SubSemiring.max` and `SubAddGroup.max`.
- **`ringHomImage`**: The image of a ring homomorphism `f : RingHom` as a `SubRing` of `f.Cod`, with membership defined as `∃ (x : f.Dom) (f x = y)` and closure proofs from the homomorphism laws (`func-zro`, `func-+`, `func-ide`, `func-*`, `func-negative`).

#### Contractibility Lemmas

- **`SubRing.isContr`**: If the ambient ring `R` is contractible, then the induced ring `S.IRing` of any subring is contractible.
- **`SubRing.comm-isContr`**: Commutative analogue: contractibility of `R : CRing` transfers to `cStruct S`.
