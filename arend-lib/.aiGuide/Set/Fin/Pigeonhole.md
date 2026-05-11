### Set.Fin.Pigeonhole

Pigeonhole principle for finite and bounded-pigeonhole sets.

This module formalizes the pigeonhole principle as a class-based abstraction: a set is a `PigeonholeSet` when every `Nat`-indexed sequence in it has a repeated value, and a `BoundedPigeonholeSet` when the bound `finCard` makes any function out of `Fin (suc finCard)` collide. The bounded version implies the unbounded one by precomposing with the inclusion `Fin → Nat`. The module also derives that injective endofunctions on bounded-pigeonhole sets are equivalences, and provides the classical pigeonhole result for functions `Fin m → Fin n` with `n < m` via decidable search.

#### Classes

- **`PigeonholeSet`**: Extends `BaseSet`. A set such that any sequence `f : Nat -> E` has two distinct indices mapping to the same value.
- **`BoundedPigeonholeSet`**: Extends `PigeonholeSet` with a bound `finCard : Nat` such that `boundedPigeonhole` provides the collision for any `f : Fin (suc finCard) -> E`; the unbounded `pigeonhole` is derived by restriction.

#### Field/Method Names

- **`pigeonhole`**: Field of `PigeonholeSet`: any `f : Nat -> E` has indices `i /= j` with `f i = f j`.
- **`pigeonhole<`**: Strengthened form ensuring the witnesses satisfy `i < j`.
- **`isEquiv`**: An injective self-map `f : E -> E` on a `PigeonholeSet` is an `Equiv`.
- **`finCard`**: Field of `BoundedPigeonholeSet`: the cardinality bound (the actual size is `suc finCard`).
- **`boundedPigeonhole`**: Field giving the collision witness for `f : Fin (suc finCard) -> E`.
- **`boundedPigeonhole<`**: Ordered version of `boundedPigeonhole` with `i < j`.

#### Top-Level Lemmas

- **`pigeonhole-surj`**: A surjection `f : A -> B` from a `BoundedPigeonholeSet A` transports the pigeonhole structure to `B` with the same `finCard`.
- **`pigeonhole-fin`**: Classical pigeonhole: any `f : Fin m -> Fin n` with `n < m` has two distinct inputs with the same image.

#### Helpers (in `pigeonhole-fin.\where`)

- **`search-pair`**: For `f : Fin n -> A` over a `DecSet`, decides whether a colliding pair `(i, j)` exists.
- **`aux`**: If `f : Fin n -> A` is not injective (with `A` a `DecSet`), produces an explicit colliding pair.
