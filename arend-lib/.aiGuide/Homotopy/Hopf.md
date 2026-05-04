### Homotopy.Hopf

The Hopf construction: H-spaces, connected H-spaces, and the Hopf fibration over the suspension of an H-space, including the classical S³ → S² case.

#### H-Space Structures

- **`HSpace`**: Class extending `Pointed` with a binary operation `*` and left/right unit laws (`base-left`, `base-right`) showing `base` acts as a two-sided identity.
- **`HSpaceConn`**: Class extending `HSpace` and `Connected0`, an H-space whose underlying type is 0-connected.

#### Translation Equivalences

- **`HSpaceConn-left`**: For a connected H-space `A` and any `x : A`, left multiplication `x * __` is an equivalence.
- **`HSpaceConn-left.lem`**: Helper showing that for any H-space, if `base = x` is merely inhabited then `(x *)` is an equivalence (transports invertibility from `base`).
- **`HSpaceConn-right`**: For a connected H-space `A` and any `x : A`, right multiplication `__ * x` is an equivalence.
- **`HSpaceConn-right.lem`**: Helper showing `(__ * x)` is an equivalence whenever `base = x` merely holds.

#### Circle as an H-Space

- **`Circle_HSpace`**: Instance making the circle `Sphere1` into a connected H-space with `base1` as unit and a multiplication defined by induction on the circle.
- **`Circle_HSpace.circle-loop`**: For each `y : Sphere1`, a self-path `y = y`; on `base1` it is the loop, and on `loop` it is filled by a 2-cube combining `ploop` four ways.
- **`Circle_HSpace.mult`**: Circle multiplication: `base1 * y = y` and the `loop` case uses `circle-loop`.
- **`Circle_HSpace.mult-right`**: Right unit law `mult x base1 = x` for the circle.
- **`Sphere1_HSpace`**: Instance transporting `Circle_HSpace` along the equivalence `Sphere1 ≃ Sphere 1` to give `Sphere 1` an H-space structure.

#### The Hopf Fibration

- **`hopf`**: The Hopf construction as a type family `Susp A -> \Type`: both poles are `A`, and the path over `pglue a` is the univalent encoding of left-multiplication-by-`a` as an equivalence.

#### Total Space and Join

- **`hopf.total-equiv`**: The total space `Σ (x : Susp A), hopf x` equals the join `Join A A`; combines `PushoutData.flattening` with the equivalence below.
- **`hopf.total-equiv.total`**: The total space presented as a pushout of `Σ A A` over `(_, a₂)` and `(_, a₁ * a₂)`.
- **`hopf.total-equiv.total_join-equiv`**: A `QEquiv` between `total A` and `Join A A`.
- **`hopf.total-equiv.totalJoin`**: Forward map sending `pglue (a, a')` to `pglue (a', a * a')` in the join.
- **`hopf.total-equiv.joinTotal`**: Inverse map, using `HSpaceConn-right` to invert right multiplication when constructing the pushout glue.
- **`hopf.total-equiv.joinTotalJoin`**: Right inverse proof, by path induction on the section/retraction data of the half-adjoint equivalence `(* a)`.
- **`hopf.total-equiv.totalJoinTotal`**: Left inverse proof, using the coherence `f_ret_f = f_sec_f` of the half-adjoint equivalence `(* a')`.

#### Hopf Fibration over S²

- **`hopfS2`**: The Hopf fibration specialized to `Sphere 2`, defined as `hopf x` via the `Sphere1_HSpace` instance.
- **`hopfS2.total-equiv`**: Identifies the total space of `hopfS2` with `Sphere 3`, by chaining `hopf.total-equiv` with `Join_Sphere` to get `Σ (x : Sphere 2), hopfS2 x = Sphere 3` — the classical Hopf fibration `S³ → S²`.
