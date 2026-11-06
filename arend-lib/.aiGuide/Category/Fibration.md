### Category.Fibration

Cocartesian morphisms and cocartesian fibrations (Grothendieck opfibrations) of precategories.

The module defines when a morphism of the total category is cocartesian with respect to a functor `F`, and when `F` is a cocartesian fibration, i.e. every morphism of the base out of the image of an object has a cocartesian lift. Both notions are propositions.

#### Cocartesian morphisms and fibrations

- **`IsCocartesianMor`**: For a functor `F : Functor C D` and a morphism `f : Hom x y` of `C`, the proposition that `f` is `F`-cocartesian: for every `h : Hom x z` and `k : Hom (F y) (F z)` with `k ∘ F.Func f = F.Func h`, the type of morphisms `g : Hom y z` with `g ∘ f = h` and `F.Func g = k` is contractible.
- **`IsCocartesianFunctor`**: The proposition that a functor `F` is a cocartesian fibration: for every object `x` of `C` and every morphism `phi : Hom (F x) d` there merely exist an object `y`, a morphism `f : Hom x y` with `F y = d` whose transport equals `phi`, and `f` is `F`-cocartesian.
