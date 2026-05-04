### Homotopy.Localization.Modality

Defines modalities as reflective universes closed under dependent sums, providing the dependent elimination principle characteristic of modal type theory.

#### Classes

- **`Modality`**: Extends `ReflUniverse` with the closure condition `isModality`, requiring that for any local type `A` and family of local types `B : A -> Local`, the dependent sum `\Sigma (a : A) (B a)` is again local. This is the defining property distinguishing a modality from a mere reflective subuniverse.

#### Elimination

- **`modality-elim`**: Dependent elimination for modalities. Given a family `B : LType A -> Local` of local types over the localization of `A`, restriction along the unit `lEta : A -> LType A` yields an equivalence `(\Pi (x : LType A) -> B x) ≃ (\Pi (a : A) -> B (lEta a))`. This is the universal property: every dependent map out of `A` into a local family extends uniquely to the localization.
