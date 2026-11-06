### Analysis.Measure.MeasureRing.MeasureRingCompletion

The completion of a measure ring, with its universal property.

For a `PseudoMeasureRing` `X`, `MeasureRingCompletion X` is a `CompleteMeasureRing` built on the completion of `X` as a normed abelian group (`RegularCauchyFilter X`): addition is extended from `X`, multiplication is extended by uniform continuity, and the measure is the extension of `meas`, which agrees with the completed norm. The ring axioms and measure axioms are inherited from `X` by density. The inner module `MeasureRingCompletion` contains these constructions (`measRing`, `*-cover`, `meas-cover` and their characterizations on points).

`measureCompletion` is the dense embedding of `X` into its completion. A homomorphism into a complete measure ring extends uniquely along a dense homomorphism (`dense-measure-lift`), in particular along `measureCompletion` (`measureCompletion-lift`): this is the universal property of the completion. Functoriality is `measureCompletion-map`, which turns a homomorphism with dense image into an isomorphism of completions.

#### The completion

- **`MeasureRingCompletion`**: The completion of a measure ring `X` as a `CompleteMeasureRing`, with the operations and measure extended continuously from `X`.
- **`measureCompletion`**: The inclusion `MeasureRingHom X (MeasureRingCompletion X)`; its image is dense (`measureCompletion.dense`).

#### Universal property and functoriality

- **`dense-measure-lift`**: Extension of a homomorphism `g : MeasureRingHom X Z` into a complete measure ring `Z` along a dense homomorphism `f : MeasureRingHom X Y` to a homomorphism `Y -> Z`; its `char` and `unique` give the agreement on the image of `f` and uniqueness.
- **`measureCompletion-lift`**: The unique extension of `g : MeasureRingHom X Z` into a complete measure ring `Z` to a homomorphism from `MeasureRingCompletion X`; `char` gives the value on `measureCompletion x`, `unique` gives uniqueness.
- **`measureCompletion-map`**: The homomorphism of completions induced by a homomorphism of measure rings `X -> Y`; `char` says it commutes with the embeddings.
- **`measureCompletion-map-equiv`**: A homomorphism of measure rings with dense image induces an isomorphism (equivalence) of completions.
