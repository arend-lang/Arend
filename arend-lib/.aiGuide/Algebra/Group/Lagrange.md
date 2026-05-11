### Algebra.Group.Lagrange

Lagrange's theorem: the order of a subgroup divides the order of the finite group containing it.

This module formalizes the classical Lagrange theorem by exhibiting a bijection between the ambient group and the product of its coset space with the subgroup. The general version `lagrange-gen` produces a (truncated) equivalence `H.S ≃ Cosets × H` assuming choice on cosets, while `lagrange` specializes this to finite subgroups, packaging the result as a left-divisibility witness `LDiv` between cardinalities. Finiteness of the coset space is established separately via `Cosets-fin`, which uses decidability of the subgroup and a Kuratowski-finite cover of the carrier.

#### Main Theorems

- **`lagrange-gen`**: Given a `SubGroup` `H` and a choice principle `Choice H.Cosets`, produces a truncated quasi-equivalence `H.S ≃ Σ H.Cosets H.IGroup` between the ambient group's carrier and the dependent sum of cosets paired with elements of `H`.
- **`lagrange`**: For a `FinSubGroup` `H`, produces an `LDiv (finCard {H.IFinGroup}) (finCard {H.S})` witnessing that `|H|` divides `|G|`. The quotient is realized as the cardinality of the coset space.

#### Auxiliary Constructions

- **`lagrange.Cosets-fin`**: For a `DecSubGroup` `H` whose underlying carrier is Kuratowski-finite (`KFinSet H.S`), establishes that the coset set `H.Cosets` is a `FinSet`. Used to compute `finCard` of the coset space in `lagrange`.
