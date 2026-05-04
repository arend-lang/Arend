### Homotopy.Square

Commutative squares of types and pullbacks, presented as records with universal properties.

#### Squares

- **`Square`**: A commutative square of types and maps with four corners `U, V, X, Y`, edges `ux : U -> X`, `vy : V -> Y`, `uv : U -> V`, `xy : X -> Y`, and a commutativity witness `sqcomm : vy (uv u) = xy (ux u)`.
- **`Square.pull`**: Precomposes a square's top-left corner along `f : U' -> U`, producing a new square with corner `U'`.
- **`Square.push`**: Postcomposes a square's bottom-right corner along `f : Y -> Y'`, producing a new square with corner `Y'`.

#### Pullbacks

- **`Pullback`**: A square equipped with a universal property: for every type `Z`, maps `Z -> U` are equivalent (via `pull`) to squares over the cospan `V -> Y <- X` with apex `Z`.

#### Concrete Pullbacks

- **`sigmaPullback`**: The standard pullback of `vy : V -> Y` and `xy : X -> Y` as the sigma type `\Sigma (v : V) (x : X) (vy v = xy x)`.
- **`productPullback`**: The product `A x B` realized as the pullback of the unique maps `A -> 1` and `B -> 1` over the unit type.
- **`pathPullback`**: The path type `x = y` realized as the pullback of the constant maps `1 -> A` at `x` and `y`.
