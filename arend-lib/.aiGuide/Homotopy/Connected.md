### Homotopy.Connected

Path-connected (0-connected) inhabited spaces, where any two points are merely equal.

#### Classes

- **`Connected0`**: Extends `InhSpace` with `isConn0 : (x y : E) -> TruncP (x = y)`, asserting that the propositional truncation of the identity type between any two points is inhabited.

#### Instances

- **`UnitConnected0`**: The unit type `\Sigma` is 0-connected, with the trivial inhabitant and reflexivity-based connectedness.
- **`PushoutConnected0`**: The pushout `PushoutData f g` of `f : A -> B`, `g : A -> C` is 0-connected when `A` is inhabited and `B`, `C` are 0-connected; connecting paths are built by combining paths in `B`/`C` with the pushout glue path `pglue` (or its inverse) to bridge the two sides.
