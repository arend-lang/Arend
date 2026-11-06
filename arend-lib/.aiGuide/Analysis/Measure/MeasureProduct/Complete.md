### Analysis.Measure.MeasureProduct.Complete

The product measure ring `A ⊠ B`, defined as the completion of the tensor product of two measure rings, with its universal property and functoriality.

The tensor product `A ⊗ B` of Analysis.Measure.MeasureProduct is a pseudo-measure ring; its completion `CMeasureRingTensor` is a complete measure ring, called the product measure ring. The key analytic step is that the tensor product of two homomorphisms with dense images again has dense image (`tensorMap-dense`): a rectangle of the target is approximated by a rectangle of the source, using the estimate `meas-rect-dist` for the distance between two rectangles (`approx-rect`). Consequently the product depends only on the completions of the factors (`measureTensor-completion-equiv`). The universal property is obtained by extending measure bimorphisms to the completion (`measureTensor-lift`), and the tensor product of homomorphisms is extended to a homomorphism of products (`measureTensor-map`).

#### Density of the Tensor Product of Homomorphisms

- **`meas-rect-dist`**: The measure of the sum `a ⊗ b + a' ⊗ b'` of two rectangles is at most `μ(a) ν(b + b') + μ(a + a') ν(b')`.
- **`approx-rect`**: If `f : A -> A'` and `g : B -> B'` are dense homomorphisms, every rectangle `a' ⊗ b'` of the target is approximated by the image under `f ⊗ g` of a rectangle.
- **`tensorMap-dense`**: The tensor product `f ⊗ g` of two dense measure ring homomorphisms is dense.

#### The Product Measure Ring

- **`CMeasureRingTensor`**: The product measure ring `A ⊠ B`, the completion (`MeasureRingCompletion`) of the tensor product `A ⊗ B` of pseudo-measure rings, a `CompleteMeasureRing`.
- **`crect`**: The rectangle `a ⊠ b`, the image of `rect a b` in the completion.
- **`measureTensor-lift`**: The extension of a measure bimorphism `A × B -> C` into a complete measure ring `C` to a homomorphism `A ⊠ B -> C`.
- **`measureTensor-lift_rect`**: The extension `measureTensor-lift h` sends `a ⊠ b` to `h a b`.
- **`measureTensor-hom-ext`**: Two homomorphisms out of `A ⊠ B` into a complete measure ring that agree on all rectangles are equal.

#### Functoriality

- **`measureTensor-map`**: The homomorphism `f ⊠ g : A ⊠ B -> A' ⊠ B'` induced by measure ring homomorphisms `f` and `g`, sending `a ⊠ b` to `f a ⊠ g b`.
- **`measureTensor-map_rect`**: `(f ⊠ g)(a ⊠ b) = f a ⊠ g b`.
- **`measureTensor-map-equiv`**: If `f` and `g` have dense images, then `f ⊠ g` is an isomorphism.
- **`measureTensor-completion-equiv`**: The product depends only on the completions of the factors: `A ⊠ B ≅ Ā ⊠ B̄`, via the completion maps.
