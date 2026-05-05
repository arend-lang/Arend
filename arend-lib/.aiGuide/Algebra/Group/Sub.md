### Algebra.Group.Sub

Subgroups, normal subgroups, and subgroup-related constructions for groups and additive groups.

#### Subgroup Classes

- **`SubGroup`**: Extends `SubMonoid` over a `Group`, adding closure under inversion via `contains_inverse`.
- **`DecSubGroup`**: Extends `SubGroup` and `DecSubMonoid` — a subgroup with decidable membership.
- **`NormalSubGroup`**: Extends `SubGroup` with the normality condition `isNormal`: closed under conjugation `g h g⁻¹` for any `g` in the ambient group.
- **`FinSubGroup`**: Extends `DecSubGroup` over a `FinGroup` — a finite subgroup.
- **`SubAddGroup`**: Extends `SubAddMonoid` over an `AddGroup`, adding closure under negation via `contains_negative`.

#### Induced Structures

- **`SubGroup.cStruct`**: Builds a `CGroup` (commutative group) structure on a `SubGroup` of a `CGroup`, lifting commutativity.
- **`SubAddGroup.abStruct`**: Builds an `AbGroup` structure on a `SubAddGroup` of an `AbGroup`, lifting `+`-commutativity.
- **`SubAddGroup.max`**: The maximal subgroup of an `AddGroup` (containing every element).

#### Alternative Definition

- **`anotherSubgroupDefinition`**: Constructs a `SubGroup` from a `SubSet` `X` given a single closure axiom `X.contains (inverse x * y)` (whenever `x, y ∈ X`) plus `X.contains ide`. Includes helpers `helper`, `inv` (closure under inversion), and `contains_*'` used to derive the standard subgroup axioms.

#### Order on Subgroups

- **`SubGroupPreorder`**: `Preorder` instance on `SubGroup G` where `H <= K` iff every element contained in `H` is also contained in `K`. Provides reflexivity and transitivity.

#### Kernel / Quotient Lemma

- **`kernelEquivProp`**: For a `NormalSubGroup N` of `G`, if `a ∈ N` then `in~ a` equals the identity in the quotient `N.quotient`. Uses the helper `help-condition` showing `a ~ 1` in `N.equivalence`.
