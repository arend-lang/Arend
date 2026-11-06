### Algebra.Group.Representation.Product

Direct product (biproduct) of two linear representations of a group over a ring.

Given two linear representations `A` and `B` of a group `G` over a ring `R`, this module constructs their product representation `A × B` whose underlying module is the product `LModule` and whose action is componentwise: `g ** (a, b) = (g ** a, g ** b)`. The module also provides the universal data of a biproduct in the category of linear representations: canonical injections, projections, and a copairing operation, which exhibit the product simultaneously as a categorical product and coproduct (via the standard injection/projection structure).

#### Product Construction

- **`ProductLRepres`**: The product representation of `A B : LinRepres R G`. Built on `ProductLModule R A B` with the diagonal action `g ** (a, b) = (g A.** a, g B.** b)`, satisfying associativity, identity, left-distributivity, and compatibility with scalar multiplication.

#### Injections (Coproduct Structure)

- **`ProductLRepres.in_1`**: The canonical injection `A → A × B` as an intertwining map, sending `a` to `(a, 0)`.
- **`ProductLRepres.in_2`**: The canonical injection `B → A × B` as an intertwining map, sending `b` to `(0, b)`.

#### Projections (Product Structure)

- **`ProductLRepres.proj_1`**: The first projection `A × B → A` as an intertwining map, sending `(a, b)` to `a`.
- **`ProductLRepres.proj_2`**: The second projection `A × B → B` as an intertwining map, sending `(a, b)` to `b`.

#### Universal Property

- **`ProductLRepres.coprod-map`**: Copairing: given intertwining maps `i : A → C` and `j : B → C`, produces the unique intertwining map `A × B → C` defined by `(a, b) ↦ i a + j b`, witnessing the coproduct universal property.
