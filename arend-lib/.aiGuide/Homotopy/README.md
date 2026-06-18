Now I have enough to write the README.

### Homotopy

This directory formalizes synthetic homotopy theory in HoTT: pointed types, loop spaces, suspensions and spheres, pushouts, fibers, h-levels, truncations, modalities, and concrete higher inductive types like the circle, torus, and Eilenberg–MacLane spaces.

#### Spaces and Pointed Types

- **`Space.md`** — `BaseSpace` and `InhSpace` classes, the foundational wrappers for a carrier type, optionally with a propositional inhabitedness witness.
- **`Pointed.md`** — Pointed types and basepoint-preserving maps `A ->* B`, plus the bridge to algebraic pointed structures via 0-truncation.
- **`Connected.md`** — 0-connected spaces (inhabited with merely-equal points), with closure under pushouts.

#### Diagrams: Squares, Cubes, Pullbacks, Pushouts

- **`Square.md`** — Commutative squares of types, the `Pullback` universal property, and standard pullback constructions (sigma, product, path types).
- **`Cube.md`** — 2- and 3-dimensional path cubes (`Cube2`, `Cube3`) with the algebraic characterization of squares as path equations between boundary composites.
- **`Pushout.md`** — Pushouts both as a universal property and as a HIT (`PushoutData`), with the flattening lemma and path-space characterization along embeddings.

#### Homotopy Levels and Truncation

- **`HLevel.md`** — n-types under two parallel indexings (`-1+` and `-2+`), with closure under Π, Σ, retracts, and embeddings.
- **`Truncation.md`** — The n-truncation HIT `Trunc_-1+`, its dependent eliminator, and the encode-decode characterization of paths in higher truncations.
- **`Fibration.md`** — Homotopy fibers `Fib f b` and total spaces, with componentwise path characterization for fibers.
- **`Image.md`** — Rijke's image factorization of a map as surjection-then-embedding via iterated joins (`MImage`, `YImage`).

#### Suspensions, Spheres, and Joins

- **`Suspension.md`** — The unreduced suspension `Susp A` as a pushout of constants into the unit type, with poles, meridians, and a recursor.
- **`Sphere.md`** — The n-sphere `Sphere n` defined as iterated suspension of `Empty`, pointed at `north`.
- **`Join.md`** — The join `A * B` as a pushout, with symmetry, associativity, and the equivalences `Join (Sphere 0) A ≃ Susp A` and `Join (Sphere n) A = iterated suspension`.

#### Loop Spaces

- **`Loop.md`** — Loop spaces, the suspension–loop adjunction, sphere maps as iterated loops, and h-level characterization via iterated `Omega^ n`.
- **`EckmannHilton.md`** — The Eckmann–Hilton argument: abstract algebraic version plus its application showing `Ω²X` is commutative.

#### Concrete Higher Inductive Types

- **`Torus.md`** — The 2-torus as a HIT, its equivalence with `Sphere1 × Sphere1`, and the loop space computation `Ω(Torus) = ℤ × ℤ`.
- **`K1.md`** — The Eilenberg–MacLane space `K(G,1)` as a HIT, with encode-decode proof that its loop space recovers `G`.
- **`Hopf.md`** — H-spaces, the Hopf type-family construction over `Susp A`, and the formalization of the classical Hopf fibration `S¹ → S³ → S²`.

#### Subdirectories

- **`Localization/`** — Reflective subuniverses, modalities, accessible localizations, separated types, connectedness/equivalences relative to a modality, and the Blakers–Massey theorem.
- **`Sphere/`** — The circle `S¹` as a direct HIT (`Sphere1`) with the encode-decode computation `Ω(S¹) = ℤ`.
