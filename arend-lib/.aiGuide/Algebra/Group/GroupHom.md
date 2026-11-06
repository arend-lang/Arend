### Algebra.Group.GroupHom

Group homomorphisms in both multiplicative and additive notation, together with an injectivity criterion.

This module specializes `MonoidHom` to groups: since groups already have inverses, preservation of the identity follows automatically from preservation of multiplication, so `func-ide` is derived rather than required as a field. The additive case provides the injectivity-via-trivial-kernel criterion. Parallel `GroupHom` and `AddGroupHom` records mirror each other, with conversion functions tying them through `AddGroup.toGroup`/`fromGroup` so results proved in one notation transfer to the other.

#### Multiplicative Group Homomorphisms

- **`GroupHom`**: Record extending `MonoidHom` between groups; identity preservation is automatic via `cancel_*-left`.
- **`func-inverse`**: A homomorphism preserves inverses: `func (inverse a) = inverse (func a)`.
- **`GroupHom.func-conjugate`**: A homomorphism preserves conjugation: `func (conjugate g h) = conjugate (func g) (func h)`.
- **`GroupHom.func-commutator`**: A homomorphism preserves commutators: `func (commutator g h) = commutator (func g) (func h)`.
- **`IsIsomorphism`**: A homomorphism is an isomorphism iff it is both injective and surjective.

#### Multiplicative Identity and Composition

- **`GroupHom.id`**: The identity homomorphism `GroupHom G G`.
- **`GroupHom.compose`** (alias **`GroupHom.compose`**): Composition of group homomorphisms, `\infixl 8`.

#### Additive Group Homomorphisms

- **`AddGroupHom`**: Record extending `AddMonoidHom` between additive groups; preservation of `zro` is derived.
- **`func-negative`**: Preservation of additive inverses: `func (negative x) = negative (func x)`.
- **`func-minus`**: Preservation of subtraction: `func (x - y) = func x - func y`.
- **`injective`**: Injectivity criterion via trivial kernel in additive form (`func a = 0` implies `a = 0`).
- **`func-*i`**: Preservation of integer scalar multiplication: `func (n *i x) = n *i func x`.

#### Additive Identity, Composition, and Conversions

- **`AddGroupHom.id`**: The identity additive group homomorphism.
- **`toGroupHom`**: Converts an `AddGroupHom` to a `GroupHom` between the corresponding multiplicative groups via `AddGroup.toGroup`.
- **`fromGroupHom`**: Converts a `GroupHom` to an `AddGroupHom` between the corresponding additive groups via `AddGroup.fromGroup`.

- **`conjugateHom`**: For `g : E` in a group, the inner automorphism `GroupHom E E` given by `x |-> g x g^{-1}`.
