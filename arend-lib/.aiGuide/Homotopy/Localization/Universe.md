### Homotopy.Localization.Universe

Abstract framework for localization of types at a universe of "local" types in homotopy type theory.

A `Universe` is a predicate `isLocal` selecting a class of types, and a `Localization` of a type `S` is a local type `S'` with a unit `inL : S -> S'` satisfying the universal property that precomposition with `inL` gives an equivalence `(S' -> Z) ≃ (S -> Z)` for every local `Z`. A `ReflUniverse` provides a chosen localization for every type, yielding a reflector `LType` with unit `lEta`. The module develops the calculus of this reflector: lifting maps along `inL`, proving uniqueness of localizations, and showing that locality is preserved under standard type-theoretic constructions (products, pullbacks, paths, Π-types, equivalences). It also establishes Yoneda-style elimination principles into local type families.

#### Core Classes

- **`Universe`**: A class carrying a predicate `isLocal : \hType -> \Prop` distinguishing the local types.
- **`Local`**: A type `S` together with a proof `local : isLocal S` that it lies in the universe.
- **`Localization`**: For `S : \hType` and a local `S'`, the data of a unit `inL : S -> S'` together with `local-univ`, the universal property that `-o inL : (S' -> Z) -> (S -> Z)` is an equivalence for every local `Z`.
- **`ReflUniverse`**: Extends `Universe` with a chosen `localization` for every `\hType`, making the universe reflective.

#### Lifting and Uniqueness

- **`Localization.lift`**: Given `f : S -> Z` with `Z` local, produces the unique extension `S' -> Z` via the universal property.
- **`Localization.lift.const`**: A constant lift is constant: `lift (\lam _ => z) x' = z`.
- **`Localization.lift-prop`**: Computation rule: `lift f (inL x) = f x`.
- **`Localization.remove_inL`**: Function extensionality at `inL`: two maps `f, g : S' -> Z` into a local type that agree on the image of `inL` are equal pointwise.
- **`Localization.remove_inL-coh`**: Coherence: `remove_inL` recovers the input pointwise equality on points of the form `inL x`.
- **`Localization.levelProp`**: Localizations of a fixed type are unique up to canonical equality, exhibiting `Localization X` as a (propositional) attribute.

#### Reflector

- **`LType`**: The localization functor on objects: `LType A = Localization.S' {localization A}`.
- **`lEta`**: The unit of the reflector: `lEta a = inL a` for the canonical localization.
- **`lmap`**: Functorial action on maps: `(A -> B) -> (LType A -> LType B)`, defined by lifting `inL `o` f`.
- **`lmap.id-prop`**: `lmap id` is the identity on `LType A`.

#### Recognition Lemmas for Localizations

- **`sectionIsEquiv`**: If `inL : X -> LType X` admits a section, it is an equivalence.
- **`localizationOfLocalType`**: If `X` is already local, then `inL : X -> LType X` is an equivalence.
- **`localizationWithRetraction`**: If `inL : X -> LType X` has a section, then `X` itself is local.
- **`localizationFactor`**: Given `f : X -> Y` into a local `Y` and `g : Y -> LType X` factoring `inL` through `f`, `g` admits a retraction (namely `lift f`).
- **`localizationFactorSection`**: Strengthens the previous: if `g` is already a section, the factorization makes `g` an equivalence.
- **`localizationFactorEmbedding`**: Strengthens further: if `g` is an embedding, the factorization makes `g` an equivalence.

#### Closure Properties of Local Types

- **`contrLocal`**: Every contractible type is local.
- **`unitLocal`**: The unit type `\Sigma` is local.
- **`pullbackLocal`**: Pullbacks of local types over a local cospan are local.
- **`productLocal`**: Binary products of local types are local.
- **`pathLocal`**: Path types `x = y` in a local type are local.
- **`piLocal`**: Π-types `\Pi (x : A) -> B x` are local whenever each `B x` is local.
- **`equivLocal`**: The type `Equiv {A} {B}` of equivalences between local types is local.

#### Elimination and Yoneda Principles

- **`universe-elim`**: Dependent elimination into a family `B : LType A -> \hType` whose total space is local: precomposition with `lEta` gives an equivalence `(\Pi (x : LType A) -> B x) ≃ (\Pi (a : A) -> B (lEta a))`.
- **`localYoneda`**: A local Yoneda lemma: `(\Pi (a' : A) -> LType (a = a') -> P a') ≃ P a` whenever each `P a'` is local.

#### Equivalences of Localizations

- **`localization-equiv`**: Builds a quasi-equivalence `LType A ≃ LType B` from maps `f : A -> LType B`, `g : B -> LType A` together with proofs that the round-trips agree with the units, using `lift` and `remove_inL` to discharge coherence.
