### Category.Topos.Presheaf.PrelocalPredicate

Prelocal predicates on a presheaf, i.e. predicates on sections stable under restriction, and their correspondence with subobjects.

A `PrelocalPredicate S` on a presheaf `S` over `C` is a family of propositions on the sections `S a` that is preserved by the restriction maps of `S`. Such a predicate determines a sub-presheaf together with an embedding into `S`, and the module proves that prelocal predicates on `S` are equivalent to subobjects of `S`. It also shows that existential projection and equality are prelocal predicates in the cases needed for relations on presheaves.

#### Prelocal predicates

- **`PrelocalPredicate`**: A predicate `pred : \Pi {a : C} -> S a -> \Prop` on the sections of a presheaf `S` such that `pred x` implies `pred` of every restriction `S.F.Func f x`.
- **`PrelocalPredicate.toSubFunctor`**: The functor `C.op -> SetCat` sending `a` to the sections of `S a` satisfying the predicate.
- **`PrelocalPredicate.toSubPresheaf`**: The sub-presheaf of `S` of sections satisfying the predicate, as a `Presheaf`.
- **`PrelocalPredicate.embedding`**: The natural transformation from `toSubFunctor` to `S` given by inclusion of sections.
- **`PrelocalPredicate_Subobj`**: A `QEquiv` between prelocal predicates on `S` and subobjects of `S`.

#### Predicates on products

- **`exists-prelocalPredicate`**: From a prelocal predicate `P` on the product presheaf of `S` and `T`, the predicate `x` such that there merely exists `y : T a` with `P (x, y)` is prelocal on `S`.
- **`equality-prelocalPredicate`**: The equality `s.1 = s.2` is a prelocal predicate on the product presheaf of `S` with itself.
