### Category.Topos.Sheaf

Separated presheaves and sheaves on sites, valued in arbitrary precategories or in `Set`.

A `VPresheaf` (from `Category.Topos.Presheaf`) is a contravariant functor `F : C.op -> D`. A matching family for `F` over a family `P` of morphisms into `x` is a compatible family of morphisms `d -> F (P j).1`; `F` is separated if the map `h |-> (F.Func (P j).2 ∘ h)` from `Hom d (F x)` to matching families over every covering presieve is injective, and a sheaf (`IsSheaf F`) if it is an equivalence. A `VSheaf` is a `SeparatedVPresheaf` on a `Site` satisfying `IsSheaf F`. The module also provides direct image along site morphisms, transfer through limit-preserving/reflecting functors, and the specialization to preorder sites (`IsPreorderSheaf`).

#### Matching Families

- **`MatchingFamily`**: Record of compatible families over `P : J -> ObOver x` with values in `d`: `family j : Hom d (F (P j).1)` such that `F.Func g ∘ family j = F.Func g' ∘ family j'` whenever `(P j).2 ∘ g = (P j').2 ∘ g'`. Coerces to `family`.
- **`MatchingFamily.matchingFamily`**: The canonical map sending `h : Hom d (F x)` to its matching family `F.Func (P j).2 ∘ h`.
- **`PresieveMatchingFamily`**: Matching families over a presieve `P` of `x`, i.e. over the family indexed by `Given P`.
- **`PresieveMatchingFamily.matchingFamily`**: The canonical map `Hom d (F x) -> PresieveMatchingFamily F x P d`.

#### Separated Presheaves

- **`IsSeparatedPresheaf`**: `F : Functor C.op D` on a `Site` is separated: for every covering presieve `S` of every `x`, `PresieveMatchingFamily.matchingFamily F x S d` is injective.
- **`SeparatedVPresheaf`**: Class extending `VPresheaf` with `C : Site` and a proof `isSeparated : IsSeparatedPresheaf F`.
- **`SeparatedVPresheaf.separated-covering-inj`**: For a `Covering x U`, the map `Hom d (F x) -> PresieveMatchingFamily F x U d` is injective.
- **`SeparatedVPresheaf.separated-covering`**: Two morphisms `g, g' : Hom d (F x)` that agree after `F.Func u.2 ∘ -` for every `u` in a covering presieve `U` are equal.
- **`SeparatedVPresheaf.covering-comp`**: If matching families over `P` and over covers `Q i` of each `(P i).1` are uniquely realized (`IsEquiv` of `matchingFamily`), then so are matching families over the composite family indexed by `\Sigma (i : I) (J i)`.
- **`SeparatedVPresheaf.covering-refined`**: If `P` covers `x` and refines `Q` (each `P i` factors through some `Q j`), then `matchingFamily` for `P` being an equivalence implies it is one for `Q`.

#### Sheaves

- **`IsSheaf`**: `F : Functor C.op D` on a `Site` is a sheaf: for every covering presieve `S` of every `x`, `PresieveMatchingFamily.matchingFamily F x S d` is an equivalence, i.e. every matching family is induced by a unique `Hom d (F x)`.
- **`IsSheaf.makeSheaf`**: Packages a proof of `IsSheaf F` as a `VSheaf D C F`.
- **`IsSheaf.SheafCond`**: The sheaf condition for a single presieve `S` of `x` and object `d`: `matchingFamily` is an equivalence.
- **`IsSheaf.SetSheafCond`**: For a set-valued `F`, the sheaf condition for a presieve at the one-point set `\Sigma` implies it at every set `D`.
- **`IsSheaf.sheaf-lim`**: `SheafCond F S d` is equivalent to the map `conePullback cone d` being an equivalence, i.e. to `F x` being a limit of the diagram `coneF` over the presieve.
- **`IsSheaf.sheaf-lim.cone`**: The cone from `F x` over the diagram `coneF : Functor coneJ D` of the presieve `S`, with components `Func (j.2.2 ∘ j.4)`.
- **`VSheaf`**: Class extending `SeparatedVPresheaf`, with `isSheaf : IsSheaf F`; separatedness follows from injectivity of the sheaf-condition equivalence.
- **`Sheaf`**: `VSheaf` with `D := SetCat`, i.e. set-valued sheaves on a site.
- **`VSheafCat`**: Category of `VSheaf D C` as a full subcategory of `VPresheafCat D C`.
- **`VSheaf.sheaf-covering`**: For any `Covering x U`, `PresieveMatchingFamily.matchingFamily F x U d` is an equivalence.
- **`VSheaf.sheaf-matchingFamily`**: For a family `P` with `Covering x (TSetIm P)`, `MatchingFamily.matchingFamily F x P d` is an equivalence.
- **`VSheaf.sheaf-matchingFamily-surj`**: Every matching family `mf` over a covering family `P` is induced by some `h : Hom d (F x)` with `F.Func (P j).2 ∘ h = mf j`.

#### Restriction and Direct Image

- **`VSheaf.restrict`**: Restricts a sheaf on a locale `L` to a sheaf on `L.restrict a` by composing with the restriction functor.
- **`VSheaf.direct_image`**: Pushforward of a sheaf along a `SiteHom f : C' -> C` — yields `Comp F f.op` as a sheaf on `C'`.
- **`VSheaf.direct_image_locale`**: Direct image along a `FrameHom L' -> L`, producing a sheaf on `L'` from one on `L`.

#### Transfer Along Functors

- **`sheaf-preserve`**: If `G : D -> E` preserves all limits of small diagrams `Functor J D`, then `Comp G F` is a sheaf whenever `F : VSheaf D C` is.
- **`sheaf-reflect`**: If `G` reflects all such limits and `Comp G F` is an `IsSheaf` functor into `E`, then `F` itself satisfies `IsSheaf`.

#### Preorder Sites

- **`IsPreorderSheaf`**: The sheaf condition for a `PreorderSite`: for every basic cover `S` of `x` consisting of elements below `x`, `SheafCond F x S d` holds.
