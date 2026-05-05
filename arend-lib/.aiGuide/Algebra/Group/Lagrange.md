### Algebra.Group.Lagrange

Lagrange's theorem: the order of a subgroup divides the order of the finite group containing it.

#### Coset Decomposition

- **`lagrange-gen`**: For a subgroup `H` with choice on its coset set, produces (truncated) an equivalence `H.S ≃ Σ (Cosets) (IGroup)`, decomposing the group as the disjoint union of cosets indexed by the underlying group structure of `H`.

#### Lagrange's Theorem

- **`lagrange`**: For a finite subgroup `H` of a finite group, constructs an `LDiv (finCard H.IFinGroup) (finCard H.S)`, witnessing that `|H|` divides `|G|`. The quotient is the cardinality of the coset set, obtained via `Cosets-fin`.
- **`lagrange.Cosets-fin`**: For a decidable subgroup `H` of a Kuratowski-finite set, the coset set `H.Cosets` is finite. Used to enumerate cosets and obtain the index `[G : H]`.
