### Analysis.Measure.MeasureProduct.Monoidal

The symmetric monoidal structure on the tensor product of measure rings and on the product measure ring: symmetry, unit and associativity isomorphisms.

The module follows integrals-measures.tex, Cor. product-props(3). Because the tensor product `A ⊗ B` is not quotiented by null elements, the symmetry, the unit laws and the associativity hold exactly, for arbitrary pseudo-measure rings, as isomorphisms that send rectangles to rectangles. The unit is the two-element measure ring `UnitMeasureRing` with `μ(1) = 1`. Each isomorphism is constructed from a measure bimorphism using the universal property of the tensor product, and its inverse is shown to be a two-sided inverse by checking on rectangles. The structure is then transferred to the product `A ⊠ B` by completing the maps (`measureTensorSwap`, `measureTensorUnit`, `measureTensorAssoc`).

#### The Unit Measure Ring

- **`UnitMeasureRing`**: The two-element Boolean ring `Bool = {0, 1}` as a `MeasureRing` with measure `μ(1) = 1` and `μ(0) = 0`; it is the unit of the tensor product. Its `\where` block shows that the measure is rational.

#### Symmetry

- **`tensorSwap`**: The symmetry `A ⊗ B -> B ⊗ A`, `a ⊗ b => b ⊗ a`, defined from the bimorphism `tensorSwap.swapBimorphism`.
- **`tensorSwap-rect`**: `tensorSwap` sends `a ⊗ b` to `b ⊗ a`.
- **`tensorSwap-swap`**: Swapping twice is the identity.
- **`tensorSwap-equiv`**: `tensorSwap` is an isomorphism.

#### Unit Laws

- **`unitL`**: The left unitor `A -> 2 ⊗ A`, `a => 1 ⊗ a`. Its `\where` block defines the scalar action `smul` of `2` on `A`, the bimorphism `counit` `(e, a) => e a`, and the `inverse` homomorphism `2 ⊗ A -> A`.
- **`unitL-ret`** / **`unitL-sec`**: `unitL.inverse` is a left and a right inverse of `unitL`.
- **`unitL-equiv`**: The left unitor is an isomorphism.
- **`unitL-inverse-equiv`**: The inverse of the left unitor is an isomorphism.
- **`unitR`**: The right unitor `A -> A ⊗ 2`, `a => a ⊗ 1`, the left unitor followed by the symmetry.
- **`unitR-char`**: `unitR a = a ⊗ 1`.
- **`unitR-equiv`**: The right unitor is an isomorphism.
- **`unitR-inverse`**: The inverse of the right unitor, `A ⊗ 2 -> A`, `a ⊗ e => e a`.
- **`unitR-inverse-rect`**: `unitR-inverse (a ⊗ e) = e a`.
- **`unitR-inverse-equiv`**: The inverse of the right unitor is an isomorphism.

#### Associativity

- **`assocBoolL`**: For a fixed `c`, the Boolean bimorphism `(a, b) => a ⊗ (b ⊗ c)` into `A ⊗ (B ⊗ C)`.
- **`assocBoolR`**: For a fixed `a`, the Boolean bimorphism `(b, c) => (a ⊗ b) ⊗ c` into `(A ⊗ B) ⊗ C`.
- **`assocR`**: The associator `(A ⊗ B) ⊗ C -> A ⊗ (B ⊗ C)`, `(a ⊗ b) ⊗ c => a ⊗ (b ⊗ c)`, obtained from a measure bimorphism built from `assocBoolL`.
- **`assocR-rect`**: `assocR` sends `(a ⊗ b) ⊗ c` to `a ⊗ (b ⊗ c)`.
- **`assocL`**: The inverse associator `A ⊗ (B ⊗ C) -> (A ⊗ B) ⊗ C`, `a ⊗ (b ⊗ c) => (a ⊗ b) ⊗ c`, obtained from a measure bimorphism built from `assocBoolR`.
- **`assocL-rect`**: `assocL` sends `a ⊗ (b ⊗ c)` to `(a ⊗ b) ⊗ c`.
- **`assocLR`** / **`assocRL`**: `assocL` and `assocR` are mutually inverse.
- **`assocR-equiv`** / **`assocL-equiv`**: `assocR` and `assocL` are isomorphisms.

#### Transfer to the Product Measure Ring

- **`measureTensorSwap`**: The symmetry `A ⊠ B ≅ B ⊠ A`, the completion of `tensorSwap`.
- **`measureTensorSwap_rect`**: `measureTensorSwap` sends `a ⊠ b` to `b ⊠ a`.
- **`measureTensorSwap-equiv`**: `measureTensorSwap` is an isomorphism.
- **`measureTensorUnit`**: The unit isomorphism `Ā ≅ 2 ⊠ A` from the completion of `A`, the completion of `unitL`.
- **`measureTensorUnit-char`**: `measureTensorUnit` sends the image of `a` in the completion to `1 ⊠ a`.
- **`measureTensorUnit-equiv`**: `measureTensorUnit` is an isomorphism.
- **`measureTensorAssoc`**: The associator `(A ⊠ B) ⊠ C ≅ A ⊠ (B ⊠ C)`, assembled from `assocR` and the completion maps. Its `\where` block shows that `(A ⊗ B) ⊠ C ≅ (A ⊠ B) ⊠ C` (`inner-equiv`).
- **`measureTensorAssoc_rect`**: `measureTensorAssoc` sends `(a ⊠ b) ⊠ c` to `a ⊠ (b ⊠ c)`.
- **`measureTensorAssoc-equiv`**: `measureTensorAssoc` is an isomorphism.
