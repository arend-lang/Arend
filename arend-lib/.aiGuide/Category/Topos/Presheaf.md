### Category.Topos.Presheaf

Completeness, cocompleteness, and subobject structure for presheaf categories.

This module establishes that the category of (V-)presheaves on a small precategory inherits limits and colimits from the target category, computed pointwise. Limits in `VPresheafCat D C` are built by taking, for each object `c : C`, the limit in `D` of the diagram evaluated at `c`, and assembling these into a presheaf via the universal property — the functorial action on morphisms `f : Y → X` is the unique mediating map between the pointwise limits. The cone and limit-map natural transformations are constructed by pointwise application, with naturality verified through `limUnique`. This pointwise construction is the standard tool for showing presheaf categories form bicomplete toposes, with `PresheafCat C` as the special case `D = Set`.

#### Completeness of Presheaf Categories

- **`VPresheafComplete`**: Instance proving that `VPresheafCat D C` is complete whenever `D` is, for `C` a small precategory. Limits are computed pointwise in `D`.
- **`VPresheafComplete.limit'`**: Constructs the limit of a functor `G : J → VPresheafCat D C` by assembling pointwise limits into a presheaf, with cone and limit maps given as natural transformations.
- **`VPresheafComplete.limit'.functor-at-point`**: For each `c : C`, the diagram `J → D` obtained by evaluating `G` at `c`.
- **`VPresheafComplete.limit'.L`**: The pointwise limit `D.limit (functor-at-point c)` in `D`.
- **`VPresheafComplete.limit'.cone`**: For `f : Hom Y X` in `C`, the cone over `functor-at-point Y` with apex `L X`, used to define the functorial action on morphisms.
- **`VPresheafComplete.limit'.apex`**: The presheaf whose value at `c` is `L c`, with `Func f` defined as the mediating map `limMap (cone f)`; functoriality follows from `limUnique`.
- **`VPresheafComplete.limit'.cone-nat-map`**: The cone projection `apex → G j` as a natural transformation, with components given by pointwise cone maps.
- **`VPresheafComplete.limit'.cone-at-point`**: Restricts a cone over `G` with apex a presheaf `H` to a cone over `functor-at-point X` with apex `H X`.
- **`VPresheafComplete.limit'.lim-nat-map`**: The universal mediating natural transformation from a cone apex to `apex`, with components defined via pointwise `limMap`.

#### Cocompleteness and Bicompleteness

- **`VPresheafCocomplete`**: Instance asserting cocompleteness of `VPresheafCat D C` (proof currently a hole).
- **`VPresheafCatBicomplete`**: Instance combining `VPresheafComplete` and `VPresheafCocomplete` to give bicompleteness of `VPresheafCat D C` when `D` is bicomplete.

#### Specialization to Set-Valued Presheaves

- **`PresheafCatComplete`**: Instance showing `PresheafCat C` is complete, obtained by specializing `VPresheafComplete` to `D = SetBicat`.
- **`PresheafCatBicomplete`**: The category `PresheafCat C` is bicomplete, via `VPresheafCatBicomplete` at `SetBicat`.

#### Subobjects

- **`SubPresheave`**: The poset of subobjects of a presheaf `P : PresheafCat C`, defined as the poset completion of the subobject preorder in `PresheafCat C`.
