### Algebra.Group.Representation.Product

Product (biproduct) construction for linear representations of a group, providing the categorical product/coproduct in the category of `R`-linear `G`-representations.

#### Product Representation

- **`ProductLRepres`**: The product `A × B` of two linear representations `A B : LinRepres R G`, built on `ProductLModule R A B` with componentwise group action `g ** (a, b) = (g ** a, g ** b)`. All representation laws (`**-assoc`, `id-action`, `**-ldistr`, `**-*c`) are derived componentwise.

#### Injections (Coproduct Structure)

- **`in_1`**: Intertwining map `A → A × B` sending `a ↦ (a, 0)`; preserves addition, scalar multiplication, and the group action.
- **`in_2`**: Intertwining map `B → A × B` sending `b ↦ (0, b)`; the symmetric injection.

#### Projections (Product Structure)

- **`proj_1`**: Intertwining map `A × B → A` extracting the first component.
- **`proj_2`**: Intertwining map `A × B → B` extracting the second component.

#### Universal Property

- **`coprod-map`**: Given intertwining maps `i : A → C` and `j : B → C`, constructs the copairing `A × B → C` defined by `(a, b) ↦ i a + j b`. Witnesses the coproduct universal property in the category of representations.
