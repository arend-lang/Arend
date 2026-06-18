### Algebra.Group.GroupHom

Group homomorphisms in both multiplicative and additive notation, together with their kernels, images, and injectivity criteria.

This module specializes `MonoidHom` to groups: since groups already have inverses, preservation of the identity follows automatically from preservation of multiplication, so `func-ide` is derived rather than required as a field. The kernel of a group homomorphism is packaged as a `NormalSubGroup` (in the multiplicative case) or `SubAddGroup` (in the additive case), enabling the standard injectivity-via-trivial-kernel reasoning. Parallel `GroupHom` and `AddGroupHom` records mirror each other, with conversion functions tying them through `AddGroup.toGroup`/`fromGroup` so results proved in one notation transfer to the other.

#### Multiplicative Group Homomorphisms

- **`GroupHom`**: Record extending `MonoidHom` between groups; identity preservation is automatic via `cancel_*-left`.
- **`func-inverse`**: A homomorphism preserves inverses: `func (inverse a) = inverse (func a)`.
- **`Kernel`**: The normal subgroup `{x : Dom | func x = ide}` of the domain.
- **`TrivialKernel`**: Propositional statement that the kernel contains only `ide`.
- **`same-images-test`**: If `func a = func b`, then `inverse a * b` lies in the kernel — the standard reduction of equal-image pairs to kernel membership.
- **`Kernel-injectivity-test`**: A homomorphism with trivial kernel is injective.
- **`Kernel-injectivity-corrolary`**: An injective homomorphism has trivial kernel.
- **`IsIsomorphism`**: A homomorphism is an isomorphism iff it is both injective and surjective.

#### Multiplicative Identity and Composition

- **`id`**: The identity homomorphism `GroupHom G G`.
- **`compose`** (alias **`∘`**): Composition of group homomorphisms, `\infixl 8`.

#### Additive Group Homomorphisms

- **`AddGroupHom`**: Record extending `AddMonoidHom` between additive groups; preservation of `zro` is derived.
- **`func-negative`**: Preservation of additive inverses: `func (negative x) = negative (func x)`.
- **`func-minus`**: Preservation of subtraction: `func (x - y) = func x - func y`.
- **`injective`**: Injectivity criterion via trivial kernel in additive form (`func a = 0` implies `a = 0`).
- **`func-*i`**: Preservation of integer scalar multiplication: `func (n *i x) = n *i func x`.
- **`Kernel`**: The sub-additive-group `{x : Dom | func x = zro}` of the domain.
- **`Image`**: The sub-additive-group of `Cod` consisting of elements of the form `func y` for some `y : Dom`.

#### Additive Identity, Composition, and Conversions

- **`id`**: The identity additive group homomorphism.
- **`toGroupHom`**: Converts an `AddGroupHom` to a `GroupHom` between the corresponding multiplicative groups via `AddGroup.toGroup`.
- **`fromGroupHom`**: Converts a `GroupHom` to an `AddGroupHom` between the corresponding additive groups via `AddGroup.fromGroup`.
