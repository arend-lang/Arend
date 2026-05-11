### Homotopy.Hopf

The H-space structure and the Hopf fibration construction in homotopy type theory.

This module formalizes H-spaces (pointed types with a unital binary operation up to homotopy) and uses them to build the Hopf fibration as a type family over the suspension `Susp A` of a connected H-space `A`. The key construction encodes the Hopf bundle's classifying property: connectivity of `A` upgrades the unit laws into genuine equivalences `(x *)` and `(* x)`, which serve as the gluing data for the dependent type `hopf`. The total space is then identified with the join `A * A` via a flattening lemma for pushouts, recovering the classical fact that the total space of the Hopf fibration `S¹ → S³ → S²` is `S³`.

#### H-Space Structures

- **`HSpace`**: Class extending `Pointed` with a binary operation `*` and left/right unit laws (`base-left`, `base-right`) up to homotopy.
- **`HSpaceConn`**: Class extending `HSpace` and `Connected0`; an H-space whose underlying type is 0-connected.

#### Equivalences from Connectivity

- **`HSpaceConn-left`**: For a connected H-space `A` and `x : A`, left multiplication `(x *)` is an equivalence.
- **`HSpaceConn-left.lem`**: The general statement: given truncated proof `TruncP (base = x)`, `(x *)` is an equivalence — the connectivity hypothesis is used to extract this for every `x`.
- **`HSpaceConn-right`**: Right multiplication `(__ * x)` is an equivalence on a connected H-space.
- **`HSpaceConn-right.lem`**: General version assuming `TruncP (base = x)`.

#### The Circle as an H-Space

- **`Circle_HSpace`**: Instance making `Sphere1` (the circle as a HIT) into a connected H-space, with `base1` as unit and a multiplication built from a self-loop on the circle.
- **`Circle_HSpace.circle-loop`**: For each `y : Sphere1`, a loop `y = y`; defined by circle-induction using `Cube2.map` to handle the `loop` case via path algebra on `ploop`.
- **`Circle_HSpace.mult`**: The multiplication on `Sphere1`, defined by induction: `base1 * y = y` and the `loop` case uses `circle-loop`.
- **`Circle_HSpace.mult-right`**: The right unit law `mult x base1 = x`, by circle induction.
- **`Sphere1_HSpace`**: Transport of `Circle_HSpace` along the equivalence `Sphere1 ≃ Sphere 1`, giving the standard sphere `Sphere 1` an H-space structure.

#### The Hopf Construction

- **`hopf`**: The Hopf type family `Susp A -> \Type` over the suspension of a connected H-space `A`. Both poles map to `A`, and the meridian `pglue a` is sent to the equivalence-to-path of `HSpaceConn-left a`. This is the dependent type whose total space is the Hopf fibration.

#### Total Space as a Join

- **`hopf.total-equiv`**: The total space `\Sigma (x : Susp A), hopf x` equals `Join A A`. Proved by combining `PushoutData.flattening` for `hopf` with an explicit equivalence `total_join-equiv`.
- **`hopf.total-equiv.total`**: The pushout presentation of the total space, with maps `(p.1, p.2) ↦ ((), p.2)` and `(p.1, p.2) ↦ ((), p.1 * p.2)`.
- **`hopf.total-equiv.total_join-equiv`**: Quasi-equivalence between `total A` and `Join A A`.
- **`hopf.total-equiv.totalJoin`**: Forward map sending the pushout's gluing `(a, a')` to the join's gluing `(a', a * a')`.
- **`hopf.total-equiv.joinTotal`**: Inverse map; on the join's gluing it uses the section of `(* a)` from `HSpaceConn-right` to find a preimage under right-multiplication.
- **`hopf.total-equiv.joinTotalJoin`**: One round-trip identity, with the gluing case discharged via half-adjoint coherence (`f_sec`) and `Jl`-induction.
- **`hopf.total-equiv.totalJoinTotal`**: The other round-trip; uses the half-adjoint coherence `f_ret_f=f_sec_f` to align the two sections.

#### Hopf over the 2-Sphere

- **`hopfS2`**: Specialization `hopf x` for `x : Sphere 2`, using the H-space structure on `Sphere 1`.
- **`hopfS2.total-equiv`**: Equational chain identifying the total space with `Sphere 3`: `(\Sigma (x : Sphere 2) (hopfS2 x)) = Join (Sphere 1) (Sphere 1) = Sphere 3`. This is the classical Hopf fibration `S¹ → S³ → S²`, formalized in HoTT.
