### Algebra.Monoid.MonoidCat

- **`MonoidBicat.modToMonoid`**: Converts a `Model theory` to a `Monoid` by reading `ide` from the nullary operation and `*` from the binary operation.
- **`MonoidBicat.monoidToMod`**: Converts a `Monoid` into a `Model` of the theory; its `\where`-functor `monoidToMod.functor : Functor MonoidCat (ModelCat theory)` provides the inverse direction of the equivalence.

- **`MonoidCat`**: The category `Cat Monoid` whose homs are `MonoidHom`, with composition lifted through pointwise application. Includes a forgetful functor `MonoidCat.forget : Functor MonoidCat SetCat`.
- **`AddMonoidCat`**: The additive analogue: `Cat AddMonoid` with `AddMonoidHom` morphisms and a forgetful functor `AddMonoidCat.forget : Functor AddMonoidCat SetCat`.
- **`MonoidBicat`**: Promotes `MonoidCat` to a `BicompleteCat`: limits are computed directly as `LimitMonoid`, colimits are transported from `ModelCat theory` along `catEquiv`.
- **`MonoidBicat.theory`**: The single-sorted algebraic `Theory` of monoids: function symbols `Fin 2` (arities 0 and 2), no predicates, axioms expressing left/right unit and associativity.
- **`MonoidBicat.catEquiv`**: A `CatEquiv` between `ModelCat theory` and `MonoidCat`, with left adjoint `monoidToMod.functor`, unit `id`, and counit interpreting a monoid model as a monoid.
- **`LimitMonoid`**: The limit of a diagram `F : Functor J MonoidCat` as a monoid on the limit set of the underlying sets, with componentwise operations; `LimitMonoid.limProj` and `LimitMonoid.limMap` give the projections and the induced map from a cone, and `LimitMonoid.inv-char` says an element is invertible iff each component is.
