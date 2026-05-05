### Category.Coreflection

Coreflections (universal arrows from a functor `L : D → C` to an object `B : C`) and their equivalence with right adjoints expressed pointwise.

#### Comma Category Abbreviations

- **`comma-precat`**: The comma precategory `(L ↓ b)` for a functor `L : D → C` and an object `b : C`, built as `commaPrecat L (Const b)` over the trivial category.
- **`comma-cat`**: Same as `comma-precat` but for `D : Cat`, yielding a category structure.

#### Coreflection

- **`Coreflection`**: A class capturing a coreflection of `B : C` along `L : D → C`. Provides an object `Coreflected : D` and a counit-like map `corefl-map : Hom (L Coreflected) B` such that precomposition `corefl-map ∘ L.Func -` gives an equivalence `Hom Z Coreflected ≃ Hom (L Z) B` (the `isCoreflection` property).

#### Correspondence with Terminal Objects in the Comma Category

- **`from-comma-terminal`**: Constructs a `Coreflection L b` from a terminal object of `comma-precat L b`, using the universal map of the comma category as the inverse of `corefl-map ∘ L.Func -`.
- **`terminal-in-comma`**: Establishes a `Section` from `Coreflection L B` to `terminal-obj (comma-precat L B)`, packaging `to-comma-terminal` as `f` and `from-comma-terminal` as its retraction.
- **`to-comma-isInj`**: Injectivity of `to-comma-terminal`: if two coreflections produce equal terminal-object data in the comma category, they are equal.
- **`isProp`** (for `Coreflection`): When `D` is a category, `Coreflection L B` is a proposition, since terminal objects in `comma-cat L B` are unique.

#### Right Adjoints via Coreflections

- **`RightAdjointCoreflection`**: A class extending `Functor` packaging a right adjoint of `L : D → C` as a pointwise family of coreflections `coreflection : (Z : C) → Coreflection L Z`. The action on objects is given by `Coreflected`, and on morphisms by transporting via the coreflection equivalence.
- **`toAdjoint`**: Coercion `RightAdjointCoreflection → RightAdjointCounit`, exposing the coreflection data as a counit-based adjunction.
- **`fromAdjoint`**: Coercion `RightAdjoint → RightAdjointCoreflection`, packaging a right adjoint's counit `epsilon` and the unit/counit equivalence as a coreflection at every object.
- **`isProp`** (for `RightAdjointCoreflection`): When `D` is a category, any two `RightAdjointCoreflection`s for the same `L` are equal, by pointwise propositionality of coreflections.
