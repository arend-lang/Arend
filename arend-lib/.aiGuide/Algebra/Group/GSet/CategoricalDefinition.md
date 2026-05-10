### Algebra.Group.GSet.CategoricalDefinition

Categorical reformulation of monoid actions as functors out of a one-object delooping category.

A monoid action on an object `c` of a precategory `C` is equivalent to a functor from the delooping `BM` (the one-object precategory whose endomorphisms form `M`) into `C` sending the unique object to `c`. This module makes that classical equivalence precise: it defines `MonoidCatAction` as a monoid homomorphism `M → End c`, constructs the delooping `DeloopM M` as a `Precat`, and proves the two presentations are equivalent. This bridges the algebraic notion of action used in `Algebra.Group.GSet` with the categorical notion used in `Category.Functor`.

#### Categorical Action

- **`MonoidCatAction`**: Class for an action of a monoid `M` on an object `c : C` in a precategory, packaged as a `MonoidHom M (End c)` into the endomorphism monoid. Provides `functor`, the corresponding functor out of the delooping.

#### Delooping

- **`DeloopM`**: Instance making a monoid `M` into a one-object `Precat`: the unique object is `()`, hom-sets are `M`, identity is `M.ide`, composition is monoid multiplication.

#### Equivalence with Functors

- **`functor->action`**: Inverse direction: given a functor `f : Functor (DeloopM M) C`, recovers a `MonoidCatAction M` with object `f ()` and action `Func` as a monoid homomorphism (using `Func-id` and `Func-o`).
- **`Action<->Functor`**: The `Equiv` between `MonoidCatAction M` and `Functor (DeloopM M) C`, witnessing that monoid actions in `C` are exactly functors from the delooping into `C`.
