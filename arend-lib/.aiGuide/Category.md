### Category (root file)

Core category theory definitions.

#### Precategories and Categories

- **`Precat`**: Class with `Ob`, `Hom`, `id`, `∘` (composition), `id-left`, `id-right`, `o-assoc`.
- **`Cat`**: Extends `Precat` with `univalence : Equiv idtoiso`.
  - **`univalenceFromEquiv`**, **`makeUnivalence`**: Helpers to construct univalence.

#### Morphism Classes

- **`Map`**: Class with `C : Precat`, `dom`, `cod`, `f : Hom dom cod`.
- **`Mono`**: Extends `Map` with `isMono` (left-cancellation). Has `comp` for composition.
- **`isEpi`**: Right-cancellation property for a morphism.
- **`SplitMono`**: Extends `Mono` with `hinv` and `hinv_f`.
- **`Iso`**: Extends `SplitMono` with `f_hinv`. Has `equals`, `levelProp`, `hinv-unique`, `rightFactor`, `leftFactor`, `composite`.
- **`idIso`**: Identity isomorphism.
- **`oIso`**: Composition of isomorphisms.

#### Discrete Categories

- **`DiscretePrecat`**: Precategory on a type with `Hom = Trunc0 (=)`.

#### Structure Identity Principle

- **`SIP`**: Given a `Cat C`, a structure `Str`, and a notion of homomorphism `isHom`, transports structures along isomorphisms.

#### Graphs and Free Categories

- **`Graph`**: Class with `V : \Set` and `E : V -> V -> \Set`.
- **`TrivialCat`**: The terminal category (one object, one morphism).
