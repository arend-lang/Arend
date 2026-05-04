### Homotopy.Localization.Connected

Connected types and maps with respect to a universe of local types in higher modal homotopy theory.

#### Connectedness

- **`isConnectedType`**: A type `X` is connected (relative to universe `U`) if every `Local` type is local with respect to the nullification at `X`.
- **`isConnected=>contr`**: Connectedness implies the localization `LType X` is contractible (in a `ReflUniverse`).
- **`contr=>isConnected`**: Conversely, contractibility of `LType X` implies `X` is connected.
- **`Connected`**: Class bundling a universe `U`, a type `X`, and a proof that `X` is connected.

#### Joins of Connected Types

- **`connected_join`**: The join `Join X Y` is connected with respect to `nullTypeUniverse (Join M N)` whenever `X` is `M`-connected and `Y` is `N`-connected.
- **`connected_join.local_join`**: Characterizes locality with respect to `Join A A'` via existence of a unique extension along `f : A' -> B` (equivalence of locality predicates).
- **`connected_join.connected_local_join-left`**: If `X` is `M`-connected and `Y` is `Join M N`-local, then `Y` is `Join X N`-local.
- **`connected_join.connected_local_join-right`**: If `Y` is `N`-connected and `X` is `Join M N`-local, then `X` is `Join M Y`-local.

#### Connected Maps

- **`isConnectedMap`**: A map `f : A -> B` is connected when each fiber `Fib f b` is a connected type.
