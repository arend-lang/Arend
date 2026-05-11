### Homotopy.Pointed

Pointed types and pointed maps as the basic setting for homotopy theory.

This module defines the class of pointed types (types equipped with a distinguished basepoint) and the type of basepoint-preserving maps between them. A pointed type extends `InhSpace`, with inhabitedness witnessed by the basepoint itself. The module also provides the bridge to algebraic pointed structures by sending a pointed type to its set-truncation, viewed as an `Algebra.Pointed.Pointed`, and lifting pointed maps to pointed homomorphisms on truncations.

#### Pointed Types

- **`Pointed`**: Class extending `InhSpace` with a chosen `base : E`; inhabitedness `isInh` is automatically witnessed by `inP base`.
- **`Pointed.make`**: Constructor producing a `Pointed` instance from a type `E` and an element `e : E`.
- **`UnitPointed`**: Canonical instance making the unit type `\Sigma` pointed at `()`.

#### Pointed Maps

- **`->*`**: Type of basepoint-preserving maps `A ->* B`, defined as `\Sigma (f : A -> B) (f base = base)`.
- **`->*.ext`**: Extensionality for pointed maps: given a pointwise homotopy `p : \Pi x -> f.1 x = g.1 x` and compatibility `p base *> g.2 = f.2` of basepoint witnesses, concludes `f = g`.

#### Set-Truncation as Algebraic Pointed

- **`PointedTrunc0`**: Sends a `Pointed X` to the algebraic pointed structure (`Algebra.Pointed`) on its 0-truncation `Trunc0 X`, with distinguished element `in0 X.base`.
- **`PointedTrunc0-Func`**: Functorial action on pointed maps: a pointed map `f : X ->* Y` induces a `PointedHom` between the truncated algebraic pointed structures via `Trunc0.map`.
