### Set.Fin.BoundedPigeonhole

- **`BoundedPigeonholeSet`**: Extends `NoetherianSet` with a bound `card : Nat` such that `boundedPigeonhole` provides the collision for any `f : Fin (suc card) -> E`; `isNoetherianSet` is derived from it via `bounded-bar`.
- **`BoundedPigeonholeSet.boundedPigeonhole`**: Field giving the collision witness for `f : Fin (suc card) -> E`: there merely exist `i /= j` with `f i = f j`.
- **`BoundedPigeonholeSet.boundedPigeonhole<`**: Ordered version of `boundedPigeonhole` with `i < j`.
- **`pigeonhole-surj`**: A surjection `f : A -> B` from a `BoundedPigeonholeSet A` transports the pigeonhole structure to `B` with the same `card`.
