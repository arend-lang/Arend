### Homotopy.Localization.BlakersMassey

The Blakers-Massey theorem for modal localizations: characterizes the connectivity of the pullback-to-pushout map for a span of types.

#### Main Theorem

- **`genBlakersMassey`**: The generalized Blakers-Massey theorem: for a `Data` configuration and basepoints `x0 : X`, `y0 : Y`, the comparison map `pbMap` from the pullback to the pushout is a connected map.

#### Auxiliary Lemmas

- **`surjective`**: Establishes connectedness of `pbMap` under the assumption that there merely exists `(y, Q x0 y)`, providing the surjectivity case used in the main proof.

#### Data Classes

- **`EquivData`**: Extends `ReflUniverse`. Packages two types `A`, `B` with families of connected types `M : A -> Connected` and `N : B -> Connected`, together with maps `f`, `g` between their total spaces and homotopies `p`, `q` showing the first components round-trip to identity. Used to express equivalence-like data on connected fibrations.
- **`POData`**: Pushout span data: two types `X`, `Y` and a relation `Q : X -> Y -> \hType` defining the span `X <- Σ(x,y), Q x y -> Y`.
- **`Data`**: Extends `ReflUniverse` and `POData`. Adds the cube/connectivity hypothesis `ch`: for any `q0 : Q x y`, `q1 : Q x y'`, `q2 : Q x' y`, the join of the two path-spaces (over `Y` carrying `q0` to `q1`, and over `X` carrying `q0` to `q2`) is connected. This is the key Blakers-Massey connectivity hypothesis on the relation.
- **`DataExt`**: Extends `Data` with chosen basepoints `x0 : X`, `y0 : Y` and a basepoint witness `q0 : Q x0 y0`.
