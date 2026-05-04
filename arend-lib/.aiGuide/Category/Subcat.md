### Category.Subcat

Constructions of subcategories, full subcategories on predicates, and reflective subcategories with their interaction with limits.

#### Subcategory Constructions

- **`subPrecat`**: Given a map `f : X -> C` from a type into a precategory, builds a precategory on `X` with `Hom x y := Hom (f x) (f y)`. The basic construction underlying full subcategories.
- **`subPrecat.embedding`**: The canonical fully faithful functor `subPrecat f -> C` sending `x` to `f x`.
- **`subPrecat.pred`**: Full subcategory determined by a predicate `P : C -> \Prop`, defined as `subPrecat (Total.proj P)`.
- **`subPrecat.pred.embedding`**: Fully faithful inclusion of a predicate-defined full subcategory into `C`.
- **`subPrecat.funcLift`**: Lifts a functor `G : C -> D` through `f : X -> D` along `g : C -> X`, given a natural family of isomorphisms `f (g a) ≅ G a`, producing a functor `C -> subPrecat f` over `g`.
- **`subCat`**: Promotes `subPrecat` to a (univalent) `Cat` when `e : Embedding` is a type-theoretic embedding into a univalent category `C`, transporting univalence through the embedding.

#### Isomorphisms in Subcategories

- **`subCat-iso`**: An iso in `subPrecat f` yields an iso between the images in `C` (reflection of isos by the embedding).
- **`subCat-iso.conv`**: Converse — an iso in `C` between `f x` and `f x'` lifts to an iso in `subPrecat f`.

#### Reflective Subcategories

- **`ReflectiveSubPrecat`**: Class extending `FullyFaithfulFunctor` and `RightAdjointUnit`. Encodes a fully faithful functor `F : C -> D` with a left-adjoint reflector via the data:
  - **`reflector : D -> C`** — the reflection functor on objects.
  - **`reflectorMap (X : D) : Hom X (F (reflector X))`** — the unit/reflection map.
  - **`isReflective`**: Equivalence `Hom (reflector X) Y ≃ Hom X (F Y)` given by post-composing with the reflector map; the universal property of the reflection.
  - Supplies the left adjoint `LAdj`, the unit `eta`, and the adjunction equation `eta-adjoint` automatically from the above.
- **`ReflectiveSubPrecat.fromRightAdjoint`**: Builds a `ReflectiveSubPrecat` from any `RightAdjoint` whose underlying functor is fully faithful, using the existing unit and the unit/counit equivalence.

#### Limits in Reflective Subcategories

- **`reflectiveSubPrecatColimit`**: Given a reflective subcategory `I : C -> D`, a diagram `F : J -> C`, and a colimit `c` of `Comp I F` in `D`, produces a limit of `F.op` in `C` with apex `I.reflector c.apex`. Expresses that reflective subcategories inherit limits from the ambient category via the reflector.
