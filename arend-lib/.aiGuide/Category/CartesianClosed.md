### Category.CartesianClosed

Cartesian closed precategories: categories with finite products where the product functor `_ × X` has a right adjoint (the exponential `X → _`).

#### Core Definitions

- **`isExponential`**: `\Pi (X : C) -> RightAdjoint C C` whose left adjoint is `bprodFunctorRight X`. Witnesses that `_ × X` has a right adjoint, providing exponentials by `X`.
- **`isExponentiable`**: `\Pi (X : C) -> Coreflection (C.bprodFunctorRight X) Y`. Object-level version: `Y` is exponentiable if every `_ × X` has a coreflection at `Y`.
- **`CartesianClosedPrecat`**: Class extending `CartesianPrecat` with the field `exp : \Pi (X : Ob) -> isExponential X`, asserting that every object has an exponential right adjoint.

#### The Category of Sets is Cartesian Closed

- **`SetCartesianClosed`**: Instance of `CartesianClosedPrecat` for `SetBicat`, exhibiting the function space `X -> Z` as the exponential object.
- **`SetCartesianClosed.exp-coreflection`**: Builds a `RightAdjointCoreflection` from the per-object `power-coreflection`.
- **`SetCartesianClosed.power-coreflection`**: For sets `X, Z`, constructs `Coreflection (bprodFunctorRight X) Z` with carrier `X -> Z`, counit `apply`, and the curry/uncurry equivalence as the coreflection isomorphism.

#### Application and Currying (inside `power-coreflection`)

- **`apply`**: Evaluation map `(X -> Z) × X -> Z` sending `f` to `(proj1 f) (proj2 f)`. Serves as the counit of the adjunction.
- **`curry`**: `(Y × X -> Z) -> (Y -> X -> Z)`, defined via `name'`/`unname'` to package elements as global sections.
- **`f_sec`**: One half of the adjunction: `apply ∘ (curry g × id) = g`.
- **`curry-eq`**: Pointwise lemma `curry g (proj1 f) (proj2 f) = g f` used in proving `f_sec`.

#### Terminal Object and Global Elements in Sets

- **`from_terminal`**: Map `terminal.apex -> \Sigma` (the unique map to the unit type).
- **`to_terminal`**: Map `\Sigma -> terminal.apex`, the canonical terminal map.
- **`name'`**: Lifts an element `x : X` to a global section `terminal.apex -> X`.
- **`unname'`**: Recovers an element from a global section by applying it at `()`.
- **`terminal-obj-prop`**: `isProp terminal.apex` — the terminal object in `SetBicat` is contractible/propositional.
- **`global-elements-iso`**: `QEquiv` between `X` and `Hom terminal.apex X` via `name'`/`unname'`.
- **`name-inj`**: Injectivity of `name'`: `name' x = name' y` implies `x = y`.
- **`name-f`**: Naturality `f ∘ name' x = name' (f x)`.
- **`unname-adjoint`**: From `name' f = g` deduce `f = unname' g`.

#### Product Lemmas in Sets

- **`bprod-ext`**: Extensionality for elements of a binary product: equal projections imply equal pairs.
- **`proj1-pair-applied`**, **`proj2-pair-applied`**: Pointwise computation rules `proj_i (pair f g z) = f z` / `g z`.
- **`proj1-prodMap-applied`**, **`proj2-prodMap`**: Pointwise behavior of `prodMap f g`: projections commute with the parallel product.
- **`proj1-unname-pair`**, **`proj2-unname-pair`**: Projections of `unname' (pair (name' a) (name' b))` recover `a` and `b` respectively.
