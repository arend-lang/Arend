### Category.Topos.Presheaf

Completeness and cocompleteness of presheaf categories, computing limits pointwise from the target category.

#### Limits in Presheaf Categories

- **`VPresheafComplete`**: Instance showing that the category of `D`-valued presheaves on a small precategory `C` is complete whenever `D` is complete. Limits are computed pointwise.
- **`VPresheafComplete.limit'`**: Constructs a `Limit` for any functor `G : J -> VPresheafCat D C`, with apex, cone maps, and universal map all defined pointwise.
- **`VPresheafComplete.functor-at-point`**: For each `c : C`, the functor `J -> D` obtained by evaluating `G` at `c`; used to take limits componentwise.
- **`VPresheafComplete.L`**: The pointwise limit `D.limit (functor-at-point c)` at object `c`.
- **`VPresheafComplete.cone`**: For `f : Hom Y X`, builds a cone over `functor-at-point Y` with apex `L X` by precomposing with `Func f`; provides the action of the limit presheaf on morphisms.
- **`VPresheafComplete.apex`**: The limit presheaf itself, packaging the pointwise limits `L c` into a functor `C^op -> D` via `cone`.
- **`VPresheafComplete.cone-nat-map`**: The natural transformation `apex -> G j` projecting the pointwise limit at component `j`.
- **`VPresheafComplete.cone-at-point`**: Restricts a cone `z-cone : Cone G z` to a cone over `functor-at-point X` with apex `z X`.
- **`VPresheafComplete.lim-nat-map`**: The unique mediating natural transformation `z -> apex` induced by a cone `z-cone : Cone G z`.

#### Cocompleteness and Bicompleteness

- **`VPresheafCocomplete`**: Instance asserting cocompleteness of `VPresheafCat D C` when `D` is cocomplete (proof omitted).
- **`VPresheafCatBicomplete`**: Instance combining `VPresheafComplete` and `VPresheafCocomplete` to show `VPresheafCat D C` is bicomplete when `D` is bicomplete.

#### Set-Valued Presheaves

- **`PresheafCatComplete`**: Specialization showing the ordinary presheaf category `PresheafCat C` is complete, instantiating `VPresheafComplete` at `SetBicat`.
- **`PresheafCatBicomplete`**: Specialization showing `PresheafCat C` is bicomplete, instantiating `VPresheafCatBicomplete` at `SetBicat`.

#### Subobjects

- **`SubPresheave`**: The poset of subobjects of a presheaf `P : PresheafCat C`, defined as the poset completion of the subobject preorder in `PresheafCat C`.
