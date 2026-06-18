### Homotopy.Localization.Equiv

Characterizes the maps that become equivalences after localization at a universe of local types.

A map `f : A -> B` is a *local equivalence* when precomposition with `f` induces an equivalence on function spaces into every local type `Z`. This module establishes the basic theory: every connected map (with respect to the universe) is a local equivalence, between local types this notion coincides with ordinary equivalence, and over a reflective universe local equivalences are exactly the maps inverted by the localization functor `lmap`. The `Extension` submodule reformulates the equivalence condition as a unique-extension property, the standard orthogonality-style description of localizations.

#### Core Definition

- **`isLocalEquiv`**: A map `f : A -> B` is a local equivalence (with respect to a `Universe` of local types) if for every `Z : Local`, precomposition `-o f : (B -> Z) -> (A -> Z)` is an `Equiv`.

#### Extension Submodule

- **`Extension.ext`**: The fiber `Fib (-o f) g` of precomposition by `f` over a map `g : A -> C` — i.e., the type of extensions of `g` along `f`.
- **`Extension.ext-equiv`**: A `QEquiv` repackaging `ext f g` as `\Pi (b : B) -> \Sigma (c : C) (\Pi (a : A) -> f a = b -> g a = c)`, presenting an extension as a pointwise choice of value together with coherence over the fibers of `f`.
- **`Extension.contr-equiv`**: If for every `g : A -> C` and every `b : B` the type of pointwise extensions is contractible, then `-o f : (B -> C) -> (A -> C)` is an `Equiv`. This is the orthogonality criterion used to prove maps are local equivalences.

#### Main Lemmas

- **`connected_isLocalEquiv`**: Every connected map (in the sense of `isConnectedMap` for the universe `U`) is a local equivalence — connectivity implies orthogonality to all local types.
- **`localTypesEquiv`**: For maps `f : A -> B` between local types, `isLocalEquiv f = Equiv f`. Local equivalence reduces to ordinary equivalence on the local subuniverse.
  - **`localTypesEquiv.dir`**: The forward direction: a local equivalence between local types is an `Equiv`.
- **`localEquivMap`**: Over a `ReflUniverse` (a universe with a localization functor `lmap`), `isLocalEquiv f = Equiv (lmap f)`. A map is a local equivalence exactly when its localization is an equivalence, justifying the name.
