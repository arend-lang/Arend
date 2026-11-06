### Analysis.Integration.IntegrationSpace.IntegrationSpaceCat

The categories of integration spaces and of complete integration spaces, and their adjunctions with measure rings.

The module makes integration spaces into a univalent category `IntegrationSpaceCat`, with `IntegrationSpaceHom` as morphisms, and complete integration spaces into the full subcategory `CompleteIntegrationSpaceCat`. It then assembles the adjunction between measure rings and integration spaces: simple functions with the integral go one way, the measure ring of Boolean elements goes the other, with unit `integrationUnit` and counit `integrationCounit`. The complete version restricts to complete measure rings and complete integration spaces, using the completion of simple functions.

#### Categories

- **`IntegrationSpaceCat`**: The category of integration spaces and `IntegrationSpaceHom`s; it is univalent.
- **`CompleteIntegrationSpaceCat`**: The full subcategory of `IntegrationSpaceCat` on the complete integration spaces.

#### Adjunctions with measure rings

- **`MeasureIntegrationAdjunction`**: An `Adjunction` between `MeasureRingCat` and `IntegrationSpaceCat`: the left adjoint sends a measure ring `B` to `SFuncIntegrationSpace B`, the right adjoint sends `X` to `X.MeasRing`.
- **`CompleteMeasureIntegrationAdjunction`**: The analogous `Adjunction` between `CompleteMeasureRingCat` and `CompleteIntegrationSpaceCat`: the left adjoint sends `B` to the completion of `SFuncIntegrationSpace B`, the right adjoint sends `X` to `X.CompleteMeasRing`.
