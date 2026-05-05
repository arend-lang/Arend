### Homotopy.Localization.Equiv

Characterizes local equivalences with respect to a universe of local types — maps that induce equivalences on function spaces into local types.

#### Core Definition

- **`isLocalEquiv`**: A map `f : A -> B` is a local equivalence (relative to a universe `U`) when precomposition `(-o f) : (B -> Z) -> (A -> Z)` is an equivalence for every local type `Z`.

#### Extension Module

- **`Extension.ext`**: The fiber of precomposition `(-o f)` at `g`, i.e. extensions of `g : A -> C` along `f : A -> B`.
- **`Extension.ext-equiv`**: Equivalence between extensions of `g` along `f` and the dependent type `\Pi (b : B) -> \Sigma (c : C) (\Pi (a : A) -> f a = b -> g a = c)`, exhibiting an extension as a pointwise choice of value with compatibility data.
- **`Extension.contr-equiv`**: If for every `g : A -> C` and `b : B` the type of pointwise extensions is contractible, then `(-o f) : (B -> C) -> (A -> C)` is an equivalence.

#### Local Equivalence Lemmas

- **`connected_isLocalEquiv`**: Every connected map (with respect to the universe `U`) is a local equivalence.
- **`localTypesEquiv`**: Between local types, being a local equivalence is the same as being an equivalence: `isLocalEquiv f = Equiv f`.
- **`localTypesEquiv.dir`**: Forward direction — a local equivalence between local types is an equivalence.
- **`localEquivMap`**: For a reflective universe, `f` is a local equivalence iff its localization `lmap f` is an equivalence: `isLocalEquiv f = Equiv (lmap f)`.
