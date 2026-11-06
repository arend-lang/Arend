### Homotopy.Connected

Path-connected (0-connected) spaces in homotopy type theory.

A space is 0-connected when it is inhabited and any two points are merely equal (i.e., propositionally truncated path-equality). The module packages this as a class extending `InhSpace`, and provides the basic closure properties: the unit type is connected, and the pushout of connected spaces over an inhabited space remains connected. These instances furnish the building blocks for constructing connected spaces from simpler ones.

#### Class

- **`Connected0`**: Extends `InhSpace` with the field `isConn0 (x y : E) : TruncP (x = y)`, asserting that any two points are merely path-equal.

#### Instances

- **`UnitConnected0`**: The singleton type `\Sigma` is 0-connected.
- **`PushoutConnected0`**: The pushout `PushoutData f g` of maps `f : A -> B`, `g : A -> C` is 0-connected whenever `A` is inhabited and `B`, `C` are 0-connected.
