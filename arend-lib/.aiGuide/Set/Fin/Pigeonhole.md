### Set.Fin.Pigeonhole

Pigeonhole principle for finite and bounded-pigeonhole sets.

This module formalizes the pigeonhole principle as a class-based abstraction: a set is a `PigeonholeSet` when every `Nat`-indexed sequence in it has a repeated value (the bounded variant `BoundedPigeonholeSet` now lives in `Set.Fin.BoundedPigeonhole`). The module also derives that injective endofunctions on pigeonhole sets are equivalences, and provides the classical pigeonhole result for functions `Fin m → Fin n` with `n < m` via decidable search.

#### Classes

- **`PigeonholeSet`**: Extends `BaseSet`. A set such that any sequence `f : Nat -> E` has two distinct indices mapping to the same value.

#### Field/Method Names

- **`pigeonhole`**: Field of `PigeonholeSet`: any `f : Nat -> E` has indices `i /= j` with `f i = f j`.
- **`pigeonhole<`**: Strengthened form ensuring the witnesses satisfy `i < j`.
- **`isEquiv`**: An injective self-map `f : E -> E` on a `PigeonholeSet` is an `IsEquiv f` (an equivalence).

#### Top-Level Lemmas

- **`pigeonhole-fin`**: Classical pigeonhole: any `f : Fin m -> Fin n` with `n < m` has two distinct inputs with the same image.

#### Helpers (in `pigeonhole-fin.\where`)

- **`search-pair`**: For `f : Fin n -> A` over a `DecSet`, decides whether a colliding pair `(i, j)` exists.
- **`aux`**: If `f : Fin n -> A` is not injective (with `A` a `DecSet`), produces an explicit colliding pair.
