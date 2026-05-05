### Algebra.Group.GSet.CategoricalDefinition

Categorical formulation of monoid actions as functors from the one-object delooping category, establishing the equivalence between monoid actions and functors out of `BM`.

#### Categorical Action Structure

- **`MonoidCatAction`**: Class packaging a monoid action in a category: a precategory `C`, a monoid `M`, an object `c : C`, and a monoid homomorphism `act : MonoidHom M (End c)` into the endomorphism monoid of `c`.

#### Delooping Construction

- **`DeloopM`**: The one-object precategory `BM` associated to a monoid `M`, with a single trivial object, hom-set equal to `M`, identity given by `M.ide`, and composition given by monoid multiplication. Instance of `Precat`.

#### Action–Functor Correspondence

- **`functor->action`**: Converts a functor `f : Functor (DeloopM M) C` into a `MonoidCatAction M`, taking the action's object to `f ()` and using the functor's action on morphisms (`Func`, `Func-id`, `Func-o`) as the underlying monoid homomorphism.
- **`Action<->Functor`**: Equivalence `MonoidCatAction M ≃ Functor (DeloopM M) C` showing that monoid actions in `C` on a chosen object correspond bijectively to functors from `BM` to `C`. Provides both directions together with the round-trip identities.
