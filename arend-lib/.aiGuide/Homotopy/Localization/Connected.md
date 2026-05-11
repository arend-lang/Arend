### Homotopy.Localization.Connected

Notion of connectedness for types and maps relative to a localization universe.

A type `X` is connected (with respect to a universe `U`) when nullification at `X` preserves all `U`-local types, equivalently when its localization is contractible. The `Connected` class packages this property along with the consequence that constant functions `Z -> (X -> Z)` are equivalences for any local `Z`. The module also establishes how connectedness interacts with joins: the join of connected types is connected with respect to the join of their nullification generators, via a characterization of locality with respect to a join through dependent constancy data.

#### Connected Types

- **`isConnectedType`**: A type `X` is connected (relative to universe `U`) if every local `Z : Local` is also local for `nullTypeUniverse X` — i.e. nullifying at `X` does not change `Z`.
- **`isConnected=>contr`**: Connectedness implies that the localization `LType X` is contractible (in a `ReflUniverse`).
- **`contr=>isConnected`**: Conversely, contractibility of `LType X` implies `X` is connected.

#### The `Connected` Class

- **`Connected`**: Class bundling a type `X : \hType` with a proof `connected : isConnectedType X`.
- **`Connected.equiv`**: For any local `Z`, the constant-function map `Z -> (X -> Z)`, `\lam z _ => z`, is an equivalence — a connected `X` cannot be detected by any local target.

#### Connectedness and Joins

- **`connected_join`**: The join `Join X Y` is connected relative to `nullTypeUniverse (Join M N)` whenever `X` is connected relative to `M` and `Y` is connected relative to `N`.
- **`connected_join.local_join`**: Characterization lemma: `B` being local with respect to `Join A A'` is equivalent to, for every `f : A' -> B`, the type `\Sigma (b : B) (\Pi a, f a = b)` of "constant extensions of `f`" being local with respect to `A`.
- **`connected_join.connected_local_join-left`**: If `X` is connected over `M` and `Y` is local over `Join M N`, then `Y` is local over `Join X N` — connected types may be substituted on the left of a join in a locality hypothesis.
- **`connected_join.connected_local_join-right`**: Symmetric version: connected types may be substituted on the right of a join in a locality hypothesis.

#### Connected Maps

- **`isConnectedMap`**: A map `f : A -> B` is connected when each fiber `Fib f b` is a connected type — the fiberwise generalization of type connectedness.
