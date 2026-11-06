### Category.SubcatPred

Subcategories of a precategory given by predicates on objects and morphisms, and the generating functors defined through them.

The record `SubcatPred C` describes a (not necessarily full) subcategory of `C` by propositional predicates `ob` and `hom` that are closed under domains, codomains, identities and composition. It yields the subcategory itself with its faithful inclusion functor, is univalent when `C` is, and can be pulled back along a functor. The module also defines generating functors: those functors `L` such that the only subcategory of the codomain containing the image of `L` is the whole category (the left maps of an orthogonal factorization system on univalent categories whose right maps are faithful functors that are embeddings on objects).

#### Subcategory predicates

- **`SubcatPred`**: A subcategory of `C` given by `ob : C -> \Prop` and `hom : Hom x y -> \Prop`, with `hom-dom` and `hom-cod` (a selected morphism has selected domain and codomain), `ob-id` (identities of selected objects are selected) and `hom-o` (selected morphisms are closed under composition).
- **`SubcatPred.hom-transport`**: The predicate `hom` is invariant under transport along paths `x = x'` and `y = y'` of the domain and the codomain.
- **`SubcatPred.toPrecat`**: The precategory of the subcategory: objects are objects of `C` satisfying `ob`, morphisms are morphisms of `C` satisfying `hom`.
- **`SubcatPred.incl`**: The inclusion of `toPrecat` into `C`, a `FaithfulFunctor`.
- **`SubcatPred.toCat`**: The subcategory of a univalent category `C : Cat` is again univalent.
- **`SubcatPred.pullback`**: The preimage of a `SubcatPred` of `D` under a functor `F : Functor C D`, selecting objects `x` with `ob (F x)` and morphisms `f` with `hom (F.Func f)`.

#### Generating functors

- **`IsGeneratingFunctor`**: A functor `L : Functor A B` of univalent categories is generating if every `SubcatPred B` that contains `L.Func f` for all morphisms `f` of `A` contains every morphism of `B`.
