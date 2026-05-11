### Algebra.Ring.QuotientProperties

The first isomorphism theorem for commutative rings: every ring homomorphism factors as a quotient by its kernel followed by inclusion of its image.

This module constructs the canonical isomorphism `R / ker(f) ≃ im(f)` induced by a commutative ring homomorphism `f : R → S`. The construction descends `f` to the factor ring of its kernel and lands in the image subring, then proves this descent is both injective and surjective, yielding an isomorphism in `CRingCat`. This formalizes the standard universal-algebraic fact that homomorphisms decompose through quotients via their kernels.

#### Induced Homomorphism

- **`ringKerImageHom`**: For `f : RingHom R S` between commutative rings, the induced ring homomorphism `FactorRing (RingHom.KernelC f) → f.Image.IRing` sending the class of `a` to `(f a, inP (a, idp))`. Well-definedness on equivalence classes uses that `x - y ∈ ker f` implies `f x = f y`.
  - **`ringKerImageHom.isSurj`**: The induced map is surjective onto the image subring.
  - **`ringKerImageHom.isInj`**: The induced map is injective (its kernel is trivial in the factor ring).

#### Isomorphism

- **`ringKerImageHom-iso`**: First isomorphism theorem: `ringKerImageHom f` is an isomorphism in `CRingCat` between `FactorRing (RingHom.KernelC f)` and `(RingHom.ImageC f).ICRing`, assembled from the surjectivity and injectivity lemmas above.
