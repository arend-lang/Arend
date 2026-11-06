### Category.PreAdditive

Pre-additive and additive precategories: categories enriched in abelian groups.

A pre-additive precategory equips each hom-set with an abelian group structure such that composition is bilinear on both sides. This is the categorical generalization of a ring: rings correspond exactly to pre-additive categories with a single object, a correspondence made precise here via univalence. Adding finite products (cartesian structure) on top yields an additive precategory, the standard setting for homological algebra.

#### Classes

- **`PreAdditivePrecat`**: Extends `Precat`. A precategory enriched in abelian groups, providing `AbHom : AbGroup (Hom X Y)` on every hom-set together with left and right bilinearity laws (`l-bilinear`, `r-bilinear`) stating that composition distributes over hom-set addition on either side.
- **`AdditivePrecat`**: Extends `PreAdditivePrecat` and `CartesianPrecat`. A pre-additive precategory that also has finite products, the standard setting for additive/homological constructions.

#### Rings as One-Object Pre-Additive Categories

- **`Ring=OneObjectPreAdd`**: A `QEquiv` between `PreAdditivePrecat (\Sigma)` and `Ring`, identifying rings with pre-additive categories on a single (unit) object: `f` is `PreAdditiveCategory-OneObject`, `ret` is `Ring_toCat`, and the round trip from `Ring` is checked using `natCoefCoincide`.
- **`Ring_toCat`**: Sends a ring `R` to the pre-additive precategory on `\Sigma` whose unique hom-set is `R`, with composition given by ring multiplication and the abelian group structure given by ring addition.
- **`PreAdditiveCategory-OneObject`**: The inverse direction, recovering a ring `Ring (X.Hom () ())` from a pre-additive precategory `X` on `\Sigma`, with multiplication from composition, unit from `id`, and addition from `AbHom`.
- **`natCoefCoincide`**: Auxiliary lemma showing that the natural-number coefficient `natCoef n` computed in the ring obtained from a one-object pre-additive category agrees with the original ring's `natCoef`, used to discharge the `f_sec` round-trip equation of `Ring=OneObjectPreAdd`.
