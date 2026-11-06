### Category.CartesianClosed

Cartesian closed precategories: cartesian categories equipped with exponential objects (internal hom).

A cartesian closed precategory extends `CartesianPrecat` with an internal hom `CHom X Y`, an evaluation morphism `CHom-eval`, and a universal property saying that postcomposing with evaluation is an equivalence `Hom Z (CHom X Y) -> Hom (Bprod Z X) Y`. This yields currying (`curry`) and uncurrying (`uncurry`) as mutually inverse operations. The module also instantiates this structure on `\Set u`, where `CHom X Y` is the function space `X -> Y`.

#### Main Class

- **`CartesianClosedPrecat`**: Extends `CartesianPrecat` with an internal hom `CHom : Ob -> Ob -> Ob`, an evaluation morphism `CHom-eval : Hom (Bprod (CHom X Y) X) Y`, and `CHom-univ`, stating that `g |-> CHom-eval ∘ prodMap g id` is an equivalence from `Hom Z (CHom X Y)` to `Hom (Bprod Z X) Y`. This makes `CHom X -` right adjoint to `- × X`.
- **`CartesianClosedPrecat.curry`**: Curry `f : Hom (Bprod Z X) Y` to `Hom Z (CHom X Y)` using the inverse of the equivalence `CHom-univ`.
- **`CartesianClosedPrecat.uncurry`**: Uncurry `g : Hom Z (CHom X Y)` to `Hom (Bprod Z X) Y`, as `CHom-eval ∘ prodMap g id`.
- **`CartesianClosedPrecat.curry_uncurry`**: `curry (uncurry g) = g`.
- **`CartesianClosedPrecat.uncurry_curry`**: `uncurry (curry f) = f`.

#### Set-Theoretic Model

- **`SetCartesianClosed`**: Instance making `\Set u` cartesian closed: `CHom X Y` is the function type `X -> Y`, `CHom-eval` applies a function to an argument, and `CHom-univ` is the usual currying equivalence.
