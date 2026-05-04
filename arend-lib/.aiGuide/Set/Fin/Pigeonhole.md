### Set.Fin.Pigeonhole

The pigeonhole principle for finite sets: any function from a larger to a smaller cardinality has a collision.

#### Pigeonhole Classes

- **`PigeonholeSet`**: Extends `BaseSet`. A set `E` such that any function `Nat -> E` has two distinct inputs mapping to the same value (i.e., `E` is "finite enough" to force collisions on `Nat`).
- **`BoundedPigeonholeSet`**: Extends `PigeonholeSet`. Strengthens the principle to a concrete bound `finCard : Nat`: any function `Fin (suc finCard) -> E` has two distinct inputs with equal images. Derives the unbounded `pigeonhole` from `boundedPigeonhole` by restriction.

#### Transfer Lemmas

- **`pigeonhole-surj`**: Surjective image of a `BoundedPigeonholeSet` is again a `BoundedPigeonholeSet` with the same `finCard`. Used to transport the pigeonhole bound along surjections.

#### Finite Pigeonhole

- **`pigeonhole-fin`**: The classical statement: for `f : Fin m -> Fin n` with `n < m`, there exist distinct `i j : Fin m` with `f i = f j`.
- **`pigeonhole-fin.search-pair`**: For a function `f : Fin n -> A` into a decidable set, decides whether a colliding pair exists. Provides the constructive search underlying the pigeonhole proof.
- **`pigeonhole-fin.aux`**: From non-injectivity (`Not (IsInj f)`) of `f : Fin n -> A` into a decidable set, extracts a witnessing pair `i /= j` with `f i = f j`.
