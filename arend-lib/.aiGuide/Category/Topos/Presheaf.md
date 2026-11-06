### Category.Topos.Presheaf

Presheaf categories and their completeness.

This module establishes that the category of (V-)presheaves on a small precategory inherits limits from the target category, computed pointwise. Limits in `VPresheafCat D C` are built by taking, for each object `c : C`, the limit in `D` of the diagram evaluated at `c`, and assembling these into a presheaf via the universal property — the functorial action on morphisms `f : Y → X` is the unique mediating map between the pointwise limits. The cone and limit-map natural transformations are constructed by pointwise application, with naturality verified through `limUnique`. This pointwise construction is the standard tool for showing presheaf categories form toposes, with `PresheafCat C` as the special case `D = Set`.

#### Completeness of Presheaf Categories

- **`VPresheafComplete`**: Instance proving that `VPresheafCat D C` is complete whenever `D` is, for `C` a small precategory. Limits are computed pointwise in `D`.
- **`VPresheaf.functor-at-point`**: For each `c : C`, the diagram `J → D` obtained by evaluating `G` at `c`.
- **`VPresheafComplete.limit.L`**: The pointwise limit `D.limit (functor-at-point c)` in `D`.
- **`VPresheafComplete.limit.cone`**: For `f : Hom Y X` in `C`, the cone over `functor-at-point Y` with apex `L X`, used to define the functorial action on morphisms.
- **`VPresheafComplete.limit.apex`**: The presheaf whose value at `c` is `L c`, with `Func f` defined as the mediating map `limMap (cone f)`; functoriality follows from `limUnique`.
- **`VPresheafComplete.limit.cone-nat-map`**: The cone projection `apex → G j` as a natural transformation, with components given by pointwise cone maps.
- **`VPresheafComplete.limit.cone-at-point`**: Restricts a cone over `G` with apex a presheaf `H` to a cone over `functor-at-point X` with apex `H X`.
- **`VPresheafComplete.limit.lim-nat-map`**: The universal mediating natural transformation from a cone apex to `apex`, with components defined via pointwise `limMap`.

#### Presheaves and Presheaf Categories

- **`VPresheaf`**: A presheaf on `C` valued in `D`: a functor `C.op → D`, coerced to a functor.
- **`VPresheaf.toPresheaf`**: Turns a `D`-valued presheaf into a set-valued one by composing with a functor `U : D → SetCat`.
- **`Presheaf`**: Set-valued presheaves on `C`, that is `VPresheaf SetCat C`.
- **`VPresheafCat`**: The category of `D`-valued presheaves on `C`, as the full subcategory of the functor category `[C.op, D]` on the presheaves.
- **`PresheafCat`**: `VPresheafCat SetCat C` — the category of presheaves of sets on `C`.

#### Pointwise Limits

- **`evalVPresheaf`**: The evaluation functor `VPresheafCat D C → D` at an object `c : C`, sending a presheaf `P` to `P c`.
- **`vpresheafCone-fromPointwise`**: A cone over a diagram of presheaves is a limit cone as soon as its evaluation at every `c : C` is a limit cone in `D`.
- **`evalVPresheaf-preservesLimit`**: For `D` complete, evaluation at `c` preserves all limits of diagrams `G` of presheaves.
- **`vpresheafLimit-pointwise`**: If `L` is a limit of a diagram `G` of presheaves, then `L.apex c` is a limit of the evaluated diagram `G(c)`.
