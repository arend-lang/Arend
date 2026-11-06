### Category.Fibration.PosetElements

The displayed precategory of elements of a functor into posets, whose total category projects to the base as a cocartesian fibration.

For a functor `F` from a precategory `D` to `PosetCat`, `PosetElements F` is the displayed precategory over `D` whose fiber over `d` is the poset `F d`, with displayed morphisms `x -> y` over `f` given by `F.Func f x <= y`. Its `\where` block contains the projection of the total category to `D` and proves that this projection is a cocartesian fibration, using the notions of `Category.Fibration`.

#### Category of elements

- **`PosetElements`**: The displayed precategory over `D` for a functor `F : Functor D PosetCat`: objects over `d` are elements of the poset `F d`, and a displayed morphism over `f` from `x` to `y` is the proposition `F.Func f x <= y`.
- **`PosetElements.proj`**: The projection functor from the total precategory of `PosetElements F` to `D`.
- **`PosetElements.lift-cocartesian`**: For an arrow `f : Hom d d'` of the base and `x : F d`, the lift `(f, <=-refl)` from `(d,x)` to `(d', F.Func f x)` is `IsCocartesianMor` for `proj F`.
- **`PosetElements.proj-cocartesian`**: The projection `proj F` is a cocartesian fibration, i.e. satisfies `IsCocartesianFunctor`.
