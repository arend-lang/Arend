### Homotopy.Square

Commutative squares of types and their pullback universal property.

A `Square` packages four types `U, V, X, Y` with maps forming a commutative diagram (`vy ∘ uv = xy ∘ ux` pointwise via `sqcomm`). The module provides functorial operations `pull` (precompose along the `U` corner) and `push` (postcompose along the `Y` corner), and characterizes pullback squares via the universal property: a square is a `Pullback` exactly when `pull` induces an equivalence between maps `Z -> U` and squares with `Z` in the upper-left corner. Standard examples — sigma pullbacks, products as pullbacks over the unit type, and path types as pullbacks of points — instantiate the abstraction.

#### Squares

- **`Square`**: Record bundling four types `U, V, X, Y`, four maps `ux : U -> X`, `vy : V -> Y`, `uv : U -> V`, `xy : X -> Y`, and a commutativity witness `sqcomm : \Pi (u : U) -> vy (uv u) = xy (ux u)`.
- **`Square.pull`**: Precomposes a square with `f : U' -> s.U`, producing a new square with `U` corner replaced by `U'`. Functorial action on the upper-left vertex.
- **`Square.push`**: Postcomposes a square with `f : s.Y -> Y'`, producing a new square with `Y` corner replaced by `Y'`. Functorial action on the lower-right vertex.

#### Pullbacks

- **`Pullback`**: Class extending a `Square` with `pullback-univ`: an equivalence between `Z -> square.U` and squares-into-the-cospan with apex `Z`. Encodes the universal property characterizing `square` as the pullback of `vy` and `xy`.

#### Standard Pullbacks

- **`sigmaPullback`**: The canonical pullback of `vy : V -> Y` and `xy : X -> Y` realized as `\Sigma (v : V) (x : X) (vy v = xy x)`, with projections as the `uv` and `ux` maps.
- **`productPullback`**: The product `\Sigma A B` exhibited as the pullback of the unique maps `A -> \Sigma` and `B -> \Sigma` over the unit type.
- **`pathPullback`**: The path type `x = y` exhibited as the pullback of `x, y : \Sigma -> A`, presenting identity types as pullbacks of points.
