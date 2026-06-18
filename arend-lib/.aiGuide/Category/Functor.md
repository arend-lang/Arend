### Category.Functor

Functors between precategories, natural transformations, and the functor category construction.

A `Functor` is a map between precategories preserving identities and composition; this module bundles the structure as a class so that functors can be composed, restricted to opposite categories, and lifted to isomorphisms. Natural transformations are recorded as the `NatTrans` record with a naturality square, and together they yield the functor (pre)category `FunctorPrecat` / `FunctorCat`, whose univalence is established when the target is a category. The module also provides the diagonal family precategory `FamPrecat` and refinements of functors by faithfulness/fullness, where `FullyFaithfulFunctor` exposes a two-sided inverse on hom-sets that respects identities and composition.

#### Functors

- **`Functor`**: Class of functors between precategories `C` and `D` with object map `F : C -> D`, morphism map `Func`, and the laws `Func-id` and `Func-o`.
- **`Functor.op`**: The induced functor `C.op -> D.op`.
- **`Functor.Func-iso`**: Functors send isomorphisms to isomorphisms.
- **`Id`**: Identity functor on a precategory.
- **`Comp`**: Composition of functors `G ∘ F`.
- **`Const`**: Constant functor sending every object to a fixed `d : D` and every morphism to `id d`.
- **`Const.natTrans`**: A morphism `f : d -> d'` lifted to a natural transformation between constant functors.

#### Transport Lemmas for Functors

- **`transport_Hom`**: Computes `coe` along simultaneous paths in source objects of `F` and `G` in terms of a naturality-style square.
- **`transport_Hom-right`**: Transport in `Hom z (F -)` along a path in `C` equals composition with `F` applied to the transported identity.
- **`transport_Hom_iso-right`**: When `C` is a category, the transport along `isotoid e` reduces to post-composition with `F.Func e.f`.
- **`transport_Hom_iso`**: Two-sided version: transport along isomorphism-induced paths in both `C` and `D` reduces to a naturality equation.

#### Natural Transformations

- **`NatTrans`**: Record of natural transformations `F => G` between functors `C -> D`, with components `trans` and the naturality square `natural`.
- **`NatTrans.op`**: The opposite natural transformation `G.op => F.op`.
- **`NatTrans.iso`**: A componentwise-iso natural transformation has an inverse natural transformation `G => F`.
- **`NatTrans.Comp-left`**: Whiskering a natural transformation `α : F => G` on the right by a functor `H : C -> D`, yielding `Comp F H => Comp G H`.
- **`NatTrans.Comp-right`**: Whiskering on the left by a functor `H : D -> E`, yielding `Comp H F => Comp H G`.

#### Functor Categories

- **`FunctorPrecat`**: Precategory of functors `C -> D` between small precategories, with natural transformations as morphisms and pointwise composition.
- **`FunctorCat`**: Upgrade of `FunctorPrecat` to a category when `D` is a (small) category, providing univalence.
- **`FunctorCat.mapIso`**: Extracts the pointwise iso in `D` from an iso of functors in the functor category.

#### Family (Discrete-Indexed) Categories

- **`FamPrecat`**: Precategory of `J`-indexed families `J -> D` with pointwise morphisms and composition.
- **`FamCat`**: Categorical refinement of `FamPrecat` when `D` is a category, with pointwise univalence.
- **`FamCat.mapIso`**: Extracts the pointwise iso in `D` from an iso in the family category.

#### Faithful, Full, and Fully Faithful Functors

- **`FaithfulFunctor`**: Extends `Functor` with `isFaithful`: injectivity of `Func` on hom-sets.
- **`FullFunctor`**: Extends `Functor` with `isFull`: surjectivity (up to truncation) of `Func` on hom-sets.
- **`FullyFaithfulFunctor`**: Extends both `FullFunctor` and `FaithfulFunctor` by requiring `Func` to be an `Equiv`, with derived `isFull` and `isFaithful`.
- **`FullyFaithfulFunctor.inverse`**: The two-sided inverse `Hom (F X) (F Y) -> Hom X Y` from the equivalence.
- **`FullyFaithfulFunctor.inverse-left`** / **`inverse-right`**: Round-trip equations between `Func` and `inverse`.
- **`FullyFaithfulFunctor.inverse-id`**: The inverse sends identity to identity.
- **`FullyFaithfulFunctor.inverse-o`**: The inverse preserves composition.
