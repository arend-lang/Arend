### Homotopy.Localization.Accessible

Constructs accessible reflective universes and modalities from a family of generating maps, providing the standard presentation of localization at a small set of maps.

#### Family Specification

- **`Family`**: A class specifying a family of maps `F j : X j -> Y j` indexed by `j : J`, used as the generators for localization.
- **`universe`**: Builds a `Universe` instance from a `Family`, where `Z` is local iff precomposition with each `F j` is an equivalence `(Y j -> Z) -> (X j -> Z)`.

#### Free Localization Construction

- **`LData`**: Higher inductive type freely adjoining `ext` operations inverting each `F j`, with the path constructor `isExt` enforcing `ext f (F j x) = f x`. Constructors: `alpha` (unit), `ext`, `isExt`.
- **`dataExt`**: Universal extension of a map `h : A -> Z` (with `Z` local) to `LData A -> Z`.
- **`dataExt-unique`**: Uniqueness: any two maps `H1, H2 : LData A -> Z` agreeing on `alpha` are equal.
- **`alpha-equiv`**: The induced precomposition `(LData A -> Z) -> (A -> Z)` is an equivalence, witnessing the universal property of localization.

#### Hat Construction (Diagonal Family)

- **`DHat`**, **`CHat`**, **`FHat`**: For each `j : J`, augments the family with a second copy whose domain is the pushout `PushoutData (F j) (F j)`, used to upgrade local equivalences to embeddings.
- **`famHat`**: The augmented family combining `F j` and the pushout-codiagonal maps.
- **`pushout_pullback-equiv`**: For `f : X -> Y`, gives an equivalence between `Sigma (g1 g2 : Y -> Z) (g1 o f = g2 o f)` and `PushoutData f f -> Z`, identifying maps out of the pushout with pairs of maps agreeing on `X`.

#### Reflective Universe

- **`famUniverse`**: Packages `LData` (over `famHat`) as a `ReflUniverse`, the localization of any type at the family `F`. Locality at `inr j` is derived from the diagonal-embedding equivalence combined with `pushout_pullback-equiv`.

#### Nullification Modalities

- **`nullFamUniverse`**: Promotes the localization at maps `X j -> Sigma` (i.e., nullifying each `X j`) to a `Modality`, by showing locality is closed under dependent sums via `sigma-left`/`sigma-right` reasoning.
- **`nullFamUniverse.localDesc`**: Characterizes `Z` as local iff the constant map `Z -> (X j -> Z)` is an equivalence for each `j`.
- **`nullTypeUniverse`**: Specialization to a single type `M`, giving `M`-nullification as a modality.
- **`nullTypeUniverse.localDesc`**: `Z` is `M`-null iff the constant map `Z -> (M -> Z)` is an equivalence.
