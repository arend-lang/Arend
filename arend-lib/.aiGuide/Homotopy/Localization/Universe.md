### Homotopy.Localization.Universe

Defines universes of local types and their localizations, providing the categorical infrastructure for reflective subuniverses (modalities) along with closure and elimination principles.

#### Core Classes

- **`Universe`**: A class carrying a predicate `isLocal : \hType -> \Prop` selecting which types are "local" with respect to this universe.
- **`Local`**: A type `S` together with a proof `local : isLocal S` that it lies in the universe `U`.
- **`Localization`**: For a type `S`, a local type `S'` with a unit `inL : S -> S'` such that precomposition `-o inL : (S' -> Z) -> (S -> Z)` is an equivalence for every local `Z`. Encodes the universal property of localization.
- **`Localization.levelProp`**: Localizations of a fixed type are unique up to equality — proves `Localization` is a proposition for each `X`.
- **`ReflUniverse`**: Extends `Universe` with `localization : \Pi (A : \hType) -> Localization A`, making the subuniverse reflective.

#### Localization Operators

- **`LType`**: The localization `S'` of a type `A` in a reflective universe.
- **`lEta`**: The localization unit `A -> LType A`.
- **`lmap`**: Functorial action on localizations: lifts `f : A -> B` to `LType A -> LType B`.
- **`lmap.id-prop`**: `lmap id = id` on localized types (functoriality on identities).

#### Recognition Principles for Local Types

- **`sectionIsEquiv`**: If the unit `inL` admits a section, it is an equivalence (so the type is already local up to equivalence).
- **`localizationOfLocalType`**: The unit `inL` is an equivalence whenever the source is already local.
- **`localizationWithRetraction`**: A type whose unit has a section is itself local.
- **`localizationFactor`**: Given a factorization `g o f = inL` through a local `Y`, produces a retraction of `g`.
- **`localizationFactorSection`**: Strengthens the above: if `g` is also a section, it is an equivalence.
- **`localizationFactorEmbedding`**: If `g` is an embedding factoring `inL`, then `g` is an equivalence.

#### Closure Properties of Local Types

- **`contrLocal`**: Every contractible type is local.
- **`unitLocal`**: The unit type `\Sigma` is local.
- **`pullbackLocal`**: Pullbacks of local types over local types are local.
- **`productLocal`**: Binary products of local types are local.
- **`pathLocal`**: Path types `x = y` in a local type are local.
- **`piLocal`**: Dependent products `\Pi (x : A) -> B x` are local whenever each `B x` is.
- **`equivLocal`**: The type of equivalences `Equiv {A} {B}` between local types is local.

#### Elimination and Universal Properties

- **`universe-elim`**: Dependent elimination principle — for `B : LType A -> \hType` with locally total space, restricting along `lEta` gives an equivalence `(\Pi (x : LType A) -> B x) ≃ (\Pi (a : A) -> B (lEta a))`.
- **`localYoneda`**: A localization-flavored Yoneda lemma: `(\Pi a' -> LType (a = a') -> P a') ≃ P a` for any family `P` of local types.
- **`localization-equiv`**: Builds a `QEquiv` between `LType A` and `LType B` from maps `f : A -> LType B`, `g : B -> LType A` whose lifts compose to the identity unit on each side.
