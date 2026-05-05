### Algebra.Ring.QuotientProperties

Establishes the first isomorphism theorem for commutative rings: the quotient of a ring by the kernel of a homomorphism is isomorphic to its image.

#### Kernel-Image Homomorphism

- **`ringKerImageHom`**: Given a ring homomorphism `f : RingHom R S` between commutative rings, constructs the induced ring homomorphism `FactorRing (KernelC f) -> Image f` sending each equivalence class `[x]` to `f x` in the image subring. Verifies preservation of `+`, `*`, and the unit by lifting through the quotient.
  - **`isSurj`**: The induced map is surjective onto the image subring.
  - **`isInj`**: The induced map is injective (well-defined modulo the kernel).

#### First Isomorphism Theorem

- **`ringKerImageHom-iso`**: The canonical map `ringKerImageHom f` is an isomorphism in the category `CRingCat` of commutative rings, witnessing `R / ker f ≅ im f`.
