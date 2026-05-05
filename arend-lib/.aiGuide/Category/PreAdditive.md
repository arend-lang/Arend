### Category.PreAdditive

Pre-additive and additive categories: categories enriched over abelian groups, with the equivalence between rings and one-object pre-additive categories.

#### Classes

- **`PreAdditivePrecat`**: Extends `Precat`. A pre-additive precategory where each hom-set `Hom X Y` carries an `AbGroup` structure (`AbHom`) and composition is bilinear with respect to addition (`l-bilinear`, `r-bilinear`).
- **`AdditivePrecat`**: Extends `PreAdditivePrecat` and `CartesianPrecat`. A pre-additive precategory that additionally has finite products (terminal object and binary products).

#### Rings as One-Object Pre-Additive Categories

- **`Ring=OneObjectPreAdd`**: Equality `PreAdditivePrecat (\Sigma) = Ring`, established via `QEquiv-to-=`. Witnesses that rings are exactly pre-additive categories with a single object (where the hom-set is the underlying ring).
- **`PreAdditiveCategory-OneObject`**: Given a one-object pre-additive precategory `X : PreAdditivePrecat (\Sigma)`, produces a `Ring` on `X.Hom () ()` with multiplication given by composition, identity by `X.id ()`, and addition inherited from `AbHom`.
- **`Ring_toCat`**: Given a ring `R`, builds a one-object `PreAdditivePrecat (\Sigma)` whose unique hom-set is `R`, with composition as ring multiplication and the abelian group structure from `R`.
- **`natCoefCoincide`**: Lemma that the natural number coefficient `natCoef n` computed in the pre-additive category constructed from a ring `b` agrees with `b.natCoef n`; proved by induction on `n` using `natCoefZero` and `natCoefSuc`.
