### Homotopy.Torus

Defines the torus as a higher inductive type and establishes its equivalence with the product of two circles, computing its loop space as `Int × Int`.

#### Type Definition

- **`Torus`**: HIT for the 2-torus with constructors `point`, two loops `line1`/`line2 : I` (both endpoints at `point`), and a 2-cell `face : I -> I -> Torus` whose boundary alternates between `line1` and `line2`.

#### Equivalence with Sphere Product

- **`TorusSphere-equiv`**: Quasi-equivalence `QEquiv {Torus} {\Sigma Sphere1 Sphere1}` showing the torus is equivalent to `S¹ × S¹`.
- **`TorusSphere-equiv.SphereTorus`**: Map `\Sigma Sphere1 Sphere1 -> Torus` sending `(base1, base1) -> point`, `loop`s to `line1`/`line2`, and the product 2-cell to `face`.
- **`TorusSphere-equiv.TorusSphere`**: Inverse map `Torus -> \Sigma Sphere1 Sphere1` sending the torus constructors to the corresponding pairs of circle constructors.

#### Loop Space

- **`coordinate`**: Path from `point` to `(base1, base1)` along the equivalence-induced equality of types.
- **`coordinate.Torus=Sphere`**: Type equality `Torus = \Sigma Sphere1 Sphere1` obtained from the equivalence via univalence.
- **`OmegaTorus`**: The loop space `point = point` of the torus at its basepoint.
- **`Loop_S1^2`**: Equality `OmegaTorus = \Sigma Int Int`, computing the fundamental group of the torus as `Z × Z` via the chain `OmegaTorus = Omega(S¹ × S¹) = OmegaS¹ × OmegaS¹ = Int × Int`.
