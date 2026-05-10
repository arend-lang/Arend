### Category.Coreflection

Coreflections (object-wise right adjoints) along a functor and their packaging as full right adjoints.

A coreflection of an object `B : C` along `L : D → C` is an object `Coreflected : D` together with a counit map `L Coreflected → B` that is universal: every map `L Z → B` factors uniquely through it. The module characterizes coreflections equivalently as terminal objects in the comma category `(L ↓ B)`, which makes propositionality automatic when `D` is a category. When a coreflection exists for every `B`, the assignment assembles into a functor `C → D` that is a genuine right adjoint to `L`, and this packaging (`RightAdjointCoreflection`) is shown to be equivalent to the standard counit-based formulation (`RightAdjointCounit`).

#### Comma Category Helpers

- **`comma-precat`**: The comma precategory `(L ↓ B)` for a functor `L : D → C` and object `B : C`, defined as `commaPrecat L (Const B)` over the trivial category.
- **`comma-cat`**: The comma category version when `D` is a `Cat`, giving a category structure on `(L ↓ B)`.

#### Coreflection Class

- **`Coreflection`**: A class parameterized by `L : Functor D C` and `B : C` providing:
  - `Coreflected : D` — the coreflected object,
  - `corefl-map : Hom (L Coreflected) B` — the counit at `B`,
  - `isCoreflection` — for every `Z : D`, the map `Hom Z Coreflected → Hom (L Z) B` sending `x ↦ corefl-map ∘ L.Func x` is an equivalence.
- **`to-comma`**: Packages a coreflection as an object `(Coreflected, *, corefl-map)` of `comma-precat L B`.
- **`coreflection-map`**: For any object `(x, s, d)` of the comma precategory, constructs the unique morphism into `to-comma` using the inverse of `isCoreflection` applied to `d`.
- **`to-comma-terminal`**: Exhibits `to-comma` as a terminal object of `comma-precat L B`, with uniqueness following from `Equiv.adjoint`.

#### Equivalence with Comma Terminals

- **`from-comma-terminal`**: Inverse construction — converts a terminal object of `comma-precat L b` into a `Coreflection L b`, using the universal `terminalMap'` to invert `corefl-map ∘ L.Func -`.
- **`terminal-in-comma`**: Builds a `Section {Coreflection L B} {terminal-obj (comma-precat L B)}`, witnessing that coreflections embed into (and retract from) terminal comma objects.
- **`terminal-in-comma.to-comma-isInj`**: Injectivity of `to-comma-terminal`: if two coreflections induce equal terminals, they are equal.
- **`isProp`**: When `D` is a category, `Coreflection L B` is propositional — derived from propositionality of terminal objects via `to-comma-isInj`.

#### Right Adjoint Packaging

- **`RightAdjointCoreflection`**: A class extending `Functor D ← C` (the right adjoint direction) consisting of an underlying `L : Functor D C` and an object-wise `coreflection : (Z : C) → Coreflection L Z`. Its functorial action `Func`, identity, and composition laws are derived from the universal property via `Equiv.ret` and `Equiv.adjoint`.
  - **`eval-trans`**: The counit natural transformation `Comp L \this ⇒ Id`, with components `corefl-map` at each object.
  - **`is-adjoint-counit`**: Confirms that `eval-trans Y ∘ L.Func -` is an equivalence, i.e., the data forms a counit of an adjunction.

#### Conversion to/from Standard Adjoints

- **`toAdjoint`**: Coercion `RightAdjointCoreflection → RightAdjointCounit`, using `eval-trans` as the counit `epsilon`.
- **`fromAdjoint`**: Coercion `RightAdjoint → RightAdjointCoreflection`, building per-object coreflections from `F.epsilon` and the unit/counit equivalence `F.eta_epsilon-equiv`.
- **`isProp`**: When `D` is a category, `RightAdjointCoreflection C D L` is propositional, reduced pointwise to `Coreflection.isProp`.
