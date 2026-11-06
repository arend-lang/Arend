### Homotopy.Localization.Modality

Defines modalities as a strengthening of reflective subuniverses where local types are closed under dependent sums.

A `Modality` extends `ReflUniverse` with the requirement that for any local type `A` and family `B` of local types over `A`, the total space `\Sigma (a : A) (B a)` is again local. This Σ-closure is what distinguishes modalities from arbitrary reflective subuniverses, and it is equivalent to the localization having a dependent elimination principle (rather than just a non-dependent universal property). The module records this characterization as `modality-elim`, which exhibits the dependent eliminator as an equivalence of function spaces.

#### Main Definitions

- **`Modality`**: Class extending `ReflUniverse` with the field `isModality`, asserting that the subuniverse of local types is closed under dependent sums: given `A : Local` and `B : A -> Local`, the type `\Sigma (a : A) (B a)` is itself local.

#### Dependent Elimination

- **`modality-elim`**: For any modality `U` and family `B : LType A -> Local` of local types over the localization `LType A`, precomposition with the unit `lEta : A -> LType A` is an equivalence `(\Pi (x : LType A) -> B x) -> (\Pi (a : A) -> B (lEta a))`. This is the dependent universal property of localization that holds precisely when the reflective subuniverse is a modality.
