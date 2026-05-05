### Category.Functor

Functors between precategories, natural transformations, functor categories, and special classes of functors (faithful, full, fully faithful).

#### Functors

- **`Functor`**: Class of functors between precategories `C` and `D`. Bundles object map `F : C -> D`, morphism map `Func`, and the laws `Func-id` and `Func-o` (preservation of identities and composition).
- **`Functor.transport_Hom`**: For functors `F, G` into `E`, computes `coe` of a hom in `Hom (F x1) (G x2)` along paths `p1, p2` in the source categories, given a naturality-style square.
- **`Functor.transport_Hom-right`**: `transport (Hom z) (pmap F p) g = F.Func (transport (Hom x) p (id x)) ∘ g`; transports a hom along the image of a path under `F`.
- **`Functor.transport_Hom_iso-right`**: Same as above but where the path comes from `isotoid` of an iso `e`; reduces to composing with `F.Func e.f`.
- **`Functor.transport_Hom_iso`**: Two-sided version using `isotoid` on both sides; transports a hom across isos in `C` and `D` via their images under `F` and `G`.

#### Standard Functors

- **`Id`**: The identity functor `C -> C`.
- **`Comp`**: Composition of functors: `Comp G F` applies `F` then `G`.
- **`Const`**: The constant functor `C -> D` at an object `d : D`, sending every morphism to `id d`.
- **`Const.natTrans`**: A morphism `f : d -> d'` in `D` induces a natural transformation `Const d => Const d'`.

#### Natural Transformations

- **`NatTrans`**: Class of natural transformations between functors `F, G : C -> D`. Provides component map `trans` and the naturality square `natural`.
- **`NatTrans.Comp-left`**: Whiskering on the left: precompose a natural transformation with a functor `H : C -> D`.
- **`NatTrans.Comp-right`**: Whiskering on the right: postcompose a natural transformation with a functor `H : D -> E`.

#### Functor Categories

- **`FunctorPrecat`**: The precategory of functors `C -> D` and natural transformations between them, for small `C, D`. Identity and composition are defined componentwise.
- **`FunctorCat`**: Promotes `FunctorPrecat` to a category when `D` is a (univalent) small category, by lifting pointwise isos to a functor isomorphism via `mapIso`.
- **`FunctorCat.mapIso`**: Extracts a pointwise iso in `D` from an iso of functors in `FunctorPrecat`.

#### Family (Discrete-Indexed) Categories

- **`FamPrecat`**: The precategory `J -> D` of `J`-indexed families in `D`, with morphisms given by families of morphisms.
- **`FamCat`**: The corresponding category structure when `D` is a category, via pointwise univalence.
- **`FamCat.mapIso`**: Extracts a pointwise iso in `D` from an iso in `FamPrecat`.

#### Special Functor Classes

- **`FaithfulFunctor`**: Extends `Functor` with `isFaithful`: `Func` is injective on hom-sets.
- **`FullFunctor`**: Extends `Functor` with `isFull`: every morphism `Hom (F X) (F Y)` is merely the image of some morphism in `C`.
- **`FullyFaithfulFunctor`**: Extends both `FullFunctor` and `FaithfulFunctor`; characterized by `isFullyFaithful`, asserting that `Func` is an equivalence on hom-sets, from which fullness and faithfulness are derived.
