### Algebra.Group.GroupHom

Group homomorphisms for both multiplicative and additive groups, with identity and composition.

#### Multiplicative Group Homomorphisms

- **`GroupHom`**: Class of group homomorphisms, extending `MonoidHom` with `Dom` and `Cod` overridden to `Group`. The `func-ide` axiom (preservation of the identity) is derived automatically from `func-*` using left cancellation.
- **`GroupHom.id`**: The identity homomorphism `GroupHom G G`.
- **`GroupHom.compose`** (alias **`∘`**, infixl 8): Composition of group homomorphisms `GroupHom H K -> GroupHom G H -> GroupHom G K`.

#### Additive Group Homomorphisms

- **`AddGroupHom`**: Class of additive group homomorphisms, extending `AddMonoidHom` with `Dom` and `Cod` overridden to `AddGroup`. The `func-zro` axiom (preservation of zero) is derived from `func-+` via additive left cancellation.
- **`AddGroupHom.id`**: The identity additive group homomorphism `AddGroupHom G G`.

#### Conversions Between Multiplicative and Additive

- **`AddGroupHom.toGroupHom`**: Converts an `AddGroupHom G H` into a `GroupHom` between the multiplicative groups `AddGroup.toGroup G` and `AddGroup.toGroup H`.
- **`AddGroupHom.fromGroupHom`**: Converts a `GroupHom G H` into an `AddGroupHom` between the additive groups `AddGroup.fromGroup G` and `AddGroup.fromGroup H`.
