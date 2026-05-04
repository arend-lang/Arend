### Homotopy.Pointed

Pointed types (types equipped with a distinguished basepoint) and basepoint-preserving maps between them.

#### Pointed Types

- **`Pointed`**: Class of pointed types, extending `InhSpace`. Carries a carrier `E` with a distinguished `base : E`; inhabitedness is witnessed by `inP base`.
- **`Pointed.make`**: Constructor producing a `Pointed` instance from a type `E` and an element `e : E`.
- **`UnitPointed`**: The unit type `\Sigma` as a pointed type, with `()` as basepoint.

#### Pointed Maps

- **`->*`**: Type of basepoint-preserving maps `A ->* B`: a pair of a function `f : A -> B` and a proof `f base = base`.
- **`->*.ext`**: Extensionality for pointed maps. Given a pointwise homotopy `p : \Pi (x : A) -> f.1 x = g.1 x` and a coherence `p base *> g.2 = f.2` between basepoint-preservation proofs, concludes `f = g`.

#### Interaction with Algebraic Pointed Types

- **`PointedTrunc0`**: Sends a `Pointed` type `X` to its set-truncation `Trunc0 X` viewed as an algebraic `AlgPointed` (from `Algebra.Pointed`), with identity element `in0 X.base`.
- **`PointedTrunc0-Func`**: Functorial action of `PointedTrunc0` on pointed maps: turns `f : X ->* Y` into a `PointedHom` between the truncated algebraic pointed types via `Trunc0.map`.
