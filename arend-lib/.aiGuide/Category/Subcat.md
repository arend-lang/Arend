### Category.Subcat

Subcategories of a precategory carved out by a map from an arbitrary type, plus reflective subcategories and how they inherit (co)limits.

This module builds subcategories without insisting they be full predicate-cuts: a subcategory is presented by any function `f : X -> C`, with `X` providing the objects and homs lifted from `C`. The full-subcategory case is recovered as `subPrecat.pred P` for a proposition `P`. Reflective subcategories are then formalized as fully faithful functors equipped with a reflector and unit, packaged so that the adjunction follows from this data alone (`toAdjunction`). The final lemma shows that reflective subcategories inherit limits by reflecting colimits in the ambient category.

#### Subcategory Construction

- **`subPrecat`**: Given `f : X -> C`, builds a `Precat` on `X` whose homs are `Hom (f x) (f y)`. The basic way to present a subcategory by an indexing map.
- **`subPrecat.embedding`**: The canonical inclusion `subPrecat f -> C` as a `FullyFaithfulFunctor` over `f`.
- **`subPrecat.pred`**: The full subcategory on objects satisfying a predicate `P : C -> \Prop`, defined as `subPrecat (Total.proj P)`.
- **`subPrecat.pred.embedding`**: Inclusion of a predicate-defined full subcategory.
- **`subPrecat.funcLift`**: Lifts a functor `G : C -> D` through a subcategory presentation: given `g : C -> X` with `f ∘ g` isomorphic to `G` objectwise (via `p`), produces a functor `C -> subPrecat f` covering `g`.

#### Subcategories of Univalent Categories

- **`subCat`**: When `C` is a `Cat` (univalent) and the indexing `e : Embedding` into `C` is an embedding, `subPrecat e` is itself a `Cat`. Univalence is inherited along the embedding.
- **`subCat-iso`**: An iso in `subPrecat f` transports to an iso between the images in `C`.
- **`subCat-iso.conv`**: Conversely, an iso between images in `C` lifts to an iso in `subPrecat f`.

#### Reflective Subcategories

- **`ReflectiveSubPrecat`**: A class extending `FullyFaithfulFunctor`, presenting `F : C -> D` as a reflective inclusion. Carries a `reflector : D -> C`, a unit `reflectorMap : X -> F (reflector X)`, and the `isReflective` quasi-equivalence `Hom (reflector X) Y ≃ Hom X (F Y)` (given by `Func __ ∘ reflectorMap X`).
- **`ReflectiveSubPrecat.toAdjunction`**: The adjunction (`Adjunction.FromReflector D C`) between the reflector `D -> C` and the inclusion, with unit `reflectorMap` and adjointness given by `isReflective`.

#### Limits via Reflection

- **`reflectiveSubPrecatColimit`**: Constructs a limit of `F.op : J.op -> I.C.op` from a colimit of `Comp I F` in the ambient category `D`. The apex is `I.reflector` of the ambient colimit's apex, with cone maps and universal property obtained by transporting through the reflective adjunction. This is the standard mechanism by which reflective subcategories inherit limits/colimits from their ambient category.
