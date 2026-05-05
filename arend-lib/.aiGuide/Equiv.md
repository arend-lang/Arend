### Equiv (root file)

Core equivalence types: sections, retractions, equivalences, embeddings, surjections.

#### Map, Section, Retraction

- **`Map`**: Class with `A`, `B`, `f : A -> B`.
- **`Section`**: Extends `Map` with `ret` and `ret_f : ret ∘ f = id`. Has `=Fiber`, `levelProp`.
- **`Retraction`**: Extends `Map` with `sec` and `f_sec : f ∘ sec = id`. Has `=Fiber`, `levelProp`, `isContr`.

#### Equiv and QEquiv

- **`Equiv`**: Extends `Section` and `Retraction` (biinvertible map). Has `levelProp`, `equals`, `fromInjSurj`.
- **`QEquiv`**: Extends `Equiv` with `sec = ret` (single inverse). Has `fromEquiv`, `fromEquiv'`.
- **`idEquiv`**: Identity equivalence.
- **`symQEquiv`**: Symmetric (inverse) equivalence.
- **`transQEquiv`**: Composition of `QEquiv`s.
- **`transEquiv`**: Composition of `Equiv`s.

#### Two-Out-of-Three

- **`TwoOutOfThree`** module: `leftFactor`, `leftFactorPath`, `rightFactor`, `rightFactorPath`, `rightEmbedding`, `parallelEquiv`, `leftEquiv`, `rightEquiv`, `compositeEquiv`.

#### Pre/Post-Composition Equivalences

- **`-o_Equiv`**: Pre-composition with an `Equiv` is an `Equiv`.
- **`o-_Equiv`**: Post-composition with an `Equiv` is an `Equiv`.

#### Path and Sigma Equivalences

- **`piEquiv`**: `(f = f') ≃ (\Pi (a) -> f a = f' a)` (function extensionality).
- **`sigmaEquiv`**: `(p = p') ≃ \Sigma (s : p.1 = p'.1) (transport B s p.2 = p'.2)`.
- **`piSigmaEquiv`**, **`piSigmaIdEquiv`**: Equivalences between dependent functions and sigma types with path constraints.
- **`emptyEquiv`**: Two empty types are equivalent.

#### Embedding and Surjection

- **`Embedding`**: Extends `Map` with `isEmb : \Pi (a a') -> Retraction (pmap f)`. Has `levelProp`, `fromInjection`, `diag-equiv`, `projection`, `fibers`, `embeddingFiber-isProp`.
- **`transEmbedding`**: Composition of embeddings.
- **`>->`**: Infix notation for `Embedding`.
- **`Surjection`**: Extends `Map` with `isSurjMap`.
- **`->>`**: Infix notation for `Surjection`.
- **`ESEquiv`**: Extends `Embedding` and `Surjection` to `Equiv`. Has `fromEquiv` coercion.
