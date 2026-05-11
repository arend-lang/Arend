### Homotopy.Torus

Higher inductive definition of the torus and its identification with the product of two circles.

The torus is presented as a HIT with a base point, two loops `line1` and `line2`, and a 2-dimensional `face` constructor whose four boundary edges are filled by these loops, encoding the standard square presentation of the torus. The module then establishes the fundamental equivalence `Torus ≃ Sphere1 × Sphere1` by mutually inverse constructors mapping each torus generator to the corresponding pair of circle generators. As a consequence, the loop space `Ω(Torus)` is computed to be `Int × Int`, by chaining the equivalence with the known result `Ω(Sphere1) = Int` and the eta rule for sigma types.

#### Higher Inductive Type

- **`Torus`**: The 2-torus as a HIT with a point `point`, two loop constructors `line1`, `line2 : I → Torus` (each with both endpoints equal to `point`), and a 2-cell `face : I → I → Torus` whose boundary alternates between `line1` and `line2`, realizing the square presentation of `T²`.

#### Equivalence with the Product of Circles

- **`TorusSphere-equiv`**: A `QEquiv` between `Torus` and `\Sigma Sphere1 Sphere1`, witnessing that the torus is equivalent to the product of two circles.
  - **`SphereTorus`**: The inverse map `\Sigma Sphere1 Sphere1 → Torus`, sending `(base1, base1)` to `point`, loops on each component to `line1`/`line2`, and the diagonal 2-cell to `face`.
  - **`TorusSphere`**: The forward map `Torus → \Sigma Sphere1 Sphere1`, sending generators of the torus to the corresponding pairs of `base1`/`loop` on the two circles.

#### Identification of Base Points

- **`coordinate`**: A path in the universe-induced equality between the torus point and the basepoint `(base1, base1)` of the sphere product, obtained by transporting `point` along `Torus=Sphere`.
  - **`Torus=Sphere`**: The propositional equality `Torus = \Sigma Sphere1 Sphere1` obtained from `TorusSphere-equiv` via univalence (`QEquiv-to-=`).

#### Loop Space Computation

- **`OmegaTorus`**: The loop space of the torus, defined as `point = point`.
- **`Loop_S1^2`**: The identification `OmegaTorus = \Sigma Int Int`, computed by an equational chain that transports the loop space across `Torus=Sphere`, applies sigma eta to split it into a product of circle loop spaces, and then uses `Loop_S1 : OmegaS1 = Int` componentwise.
