### Category.CartesianClosed

Cartesian closed precategories: cartesian categories equipped with exponential objects (internal hom).

A cartesian closed precategory extends `CartesianPrecat` by requiring, for each object `X`, that the binary-product functor `- × X` has a right adjoint — the exponential `exp X -`. This adjunction yields the standard apparatus of internal hom: currying (`transpose`), uncurrying (`antitranspose`), evaluation, internal composition, and names of morphisms as global elements of exponentials. The module also instantiates this structure on `SetBicat`, where exponentials are ordinary function spaces `X -> Z`, packaged via a coreflection that converts to the adjoint presentation.

#### Main Class

- **`CartesianClosedPrecat`**: Extends `CartesianPrecat` with a field `exp (X : Ob) : isExponential X` providing, for every `X`, a right adjoint to `- × X`. This gives the category internal homs.

#### Exponential Predicates

- **`isExponential`**: For `X : C`, the type of right adjoints to `C.bprodFunctorRight X` (i.e. `- × X ⊣ exp X -`).
- **`isExponentiable`**: For `Y : C`, the property that for every `X` there is a coreflection of `- × X` at `Y` — a pointwise/object-local form of exponentiability.
- **`is-exponentiable`**: Method on `CartesianClosedPrecat` extracting the coreflection witness for each object from the global adjunction.

#### Currying and Evaluation

- **`transpose`**: Curry `f : Hom (X × Y) Z` to `Hom X (exp Y Z)` via the unit of the adjunction.
- **`antitranspose`**: Uncurry `f : Hom X (exp Y Z)` to `Hom (X × Y) Z` via the counit (`epsilon`) and `prodMap`.
- **`eval-map`**: The evaluation morphism `Hom (exp Y Z × Y) Z`, given by the counit `epsilon` of the adjunction.
- **`eval-transpose`**: The β-rule for currying: `eval-map ∘ prodMap (transpose g) (id Y) = g`.
- **`eval-map-eq`**: For `f : Hom X Y`, evaluating the name of `f` against `id X` recovers `f` (after pairing with the terminal map).

#### Internal Composition and Names

- **`internal-comp`**: The internal composition morphism `Hom (exp Y Z × exp X Y) (exp X Z)`, defined by transposing the obvious double-evaluation through the associator.
- **`name`**: Sends `f : Hom X Y` to its name `Hom terminal (exp X Y)`, using `transpose` after pairing with the inverse of the left-terminal projection.
- **`global-elements-iso`**: An equivalence `Hom X Y ≃ Hom terminal (exp X Y)` via `name`, with inverse `antitranspose ∘ terminal-prod-left.f`. Witnesses that exponentials classify morphisms by their global elements.

#### Internal Hom as a Functor

- **`internal-homFunctor`**: The bifunctor `exp : C^op × C → C`, sending `(X, Y)` to `exp X Y` and `(f, g)` to `transpose (g ∘ eval-map ∘ prodMap (id _) f)`. Functoriality proofs are left as holes.

#### Set-Theoretic Model

- **`SetCartesianClosed`**: Instance making `SetBicat` cartesian closed, with `exp X` obtained by converting the coreflection `power-coreflection` into an adjoint via `RightAdjointCoreflection.toAdjoint`.
- **`exp-coreflection`**: Bundles, for each `X`, the family of coreflections of `- × X` into a `RightAdjointCoreflection` over `SetBicat`.
- **`power-coreflection`**: For sets `X`, `Z`, the coreflection witnessing that `X -> Z` is the exponential, with `corefl-map = apply` (function application). Includes a `curry` definition along with `f_sec` and `curry-eq` showing currying is a section of evaluation.

#### Set-Level Helpers

- **`from_terminal` / `to_terminal`**: Conversions between `terminal.apex` in `SetBicat` and the unit type `\Sigma`.
- **`name'` / `unname'`**: Set-level analogs of `name`: turn an element `x : X` into a map `terminal.apex -> X` and vice versa.
- **`terminal-obj-prop`**: `terminal.apex` is a proposition in `SetBicat`.
- **`global-elements-iso`** (in `\where`): The equivalence `X ≃ Hom terminal.apex X` for sets.
- **`name-inj`**: Injectivity of `name'`: equal names imply equal elements.
- **`name-f`**: Naturality of names under post-composition: `f ∘ name' x = name' (f x)`.
- **`unname-adjoint`**: From `name' f = g` deduce `f = unname' g`, via the adjoint equivalence.

#### Product Computation Lemmas (Set-Level)

- **`bprod-ext`**: Extensionality for elements of a binary product in `SetBicat`: equal projections imply equal pairs.
- **`proj1-pair-applied` / `proj2-pair-applied`**: Compute `proj_i (pair f g z)` as `f z` / `g z`.
- **`proj1-prodMap-applied` / `proj2-prodMap`**: Compute `proj_i (prodMap f g p)` as `f (proj1 p)` / `g (proj2 p)`.
- **`proj1-unname-pair` / `proj2-unname-pair`**: Projecting from `unname' (pair (name' a) (name' b))` yields `a` and `b` respectively, used to verify the curry/uncurry round-trip.
