### Homotopy.Diagonal

The diagonal of a map `r : X -> Y` and its fibers, with the criterion for the diagonal to be truncated.

The diagonal of `r` is the map from `X` to the type of pairs `(u, v)` together with a path `r u = r v`. The module identifies the fibers of the diagonal with the fibers of the action `pmap r` of `r` on paths, and deduces that the diagonal of `r` is `k`-truncated exactly when all the maps `pmap r` on path spaces are.

#### Diagonal

- **`Diagonal`**: The type `\Sigma (u v : X) (r u = r v)`, the codomain of the diagonal map of `r`.
- **`diagonal`**: The diagonal map `x |-> (x, x, idp)` from `X` to `Diagonal r`.

#### Fibers and truncation

- **`diag-fib`**: For `p : r u = r v`, a `QEquiv` between the fiber of `diagonal r` over `(u, v, p)` and the fiber of `pmap r` over `p`.
- **`truncMap-diagonal`**: `diagonal r` is a `k`-truncated map (`IsTruncMap k`) if and only if for all `u v : X` the map `pmap r {u} {v}` is.
