### Analysis.Measure.MeasureRing.MeasureRingCat

The categories of pseudo measure rings and of complete measure rings.

`MeasureRingCat` is the category whose objects are `PseudoMeasureRing` and whose morphisms are `MeasureRingHom`; its univalence says that isomorphic measure rings are equal. `CompleteMeasureRingCat` is the full subcategory on the complete measure rings. In both, isomorphisms are exactly the bijective homomorphisms, and the inverse of a bijective homomorphism is again a homomorphism.

#### Categories

- **`MeasureRingCat`**: The univalent category of `PseudoMeasureRing` with `MeasureRingHom` as morphisms.
- **`MeasureRingCat.Iso<->IsEquiv`**: A measure ring homomorphism is an isomorphism in `MeasureRingCat` iff it is an equivalence (bijective).
- **`MeasureRingCat.inverse`**: The inverse homomorphism of a bijective measure ring homomorphism.
- **`MeasureRingCat.inverse-equiv`**: The inverse of a bijective measure ring homomorphism is itself bijective.
- **`CompleteMeasureRingCat`**: The full subcategory of `MeasureRingCat` on `CompleteMeasureRing`.
- **`CompleteMeasureRingCat.Iso<->IsEquiv`**: A homomorphism of complete measure rings is an isomorphism in `CompleteMeasureRingCat` iff it is bijective.
