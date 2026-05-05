### Algebra.Pointed.Sub

Subsets of pointed sets that are closed under the distinguished element (multiplicative or additive identity).

#### Multiplicative Sub-Pointed Sets

- **`SubPointed`**: Class extending `SubSet` with `S : Pointed`, requiring `contains_ide : contains 1`. Represents a subset closed under the multiplicative identity.
- **`SubPointed.max`**: The maximal `SubPointed` over a `Pointed` `A`, containing every element (built from `DecSubSet.max`).
- **`DecSubPointed`**: Class extending both `SubPointed` and `DecSubSet`; a `SubPointed` with decidable membership.

#### Additive Sub-Pointed Sets

- **`SubAddPointed`**: Class extending `SubSet` with `S : AddPointed`, requiring `contains_zro : contains 0`. Represents a subset closed under the additive identity.
- **`SubAddPointed.max`**: The maximal `SubAddPointed` over an `AddPointed` `A`, containing every element (built from `DecSubSet.max`).
