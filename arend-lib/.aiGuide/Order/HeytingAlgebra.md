### Order.HeytingAlgebra

Heyting algebras: bounded distributive lattices equipped with an implication (relative pseudo-complement) operation, forming a cartesian closed category structure on the underlying poset.

#### Preorder Reasoning

- **`=<=`**: Equational reasoning step combining a path with a `<=` relation: `x = y -> y <= z -> x <= z`. Useful in chained order proofs.

#### Heyting Algebra Structure

- **`HeytingAlebra`**: The class of Heyting algebras, extending `BoundedDistributiveLattice` and `CartesianClosedPrecat`. A bounded distributive lattice with an implication operation `-->` characterized by the adjunction `x ∧ a <= b ⟺ x <= a --> b`.
  - **`implies` (`-->`)**: The Heyting implication, with notation `\infixr 5 -->`: `E -> E -> E`.
  - **`exponent-left`**: One direction of the adjunction: from `x ∧ a <= b` derive `x <= a --> b`.
  - **`exponent-right`**: The reverse direction: from `x <= a --> b` derive `x ∧ a <= b`.
  - Inherits `top`, `top-univ`, `ldistr>=`, and `exp` (the cartesian closed exponential) by deriving them from the lattice and implication structure.
