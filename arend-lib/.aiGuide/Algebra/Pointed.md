### Algebra.Pointed

Pointed sets: types equipped with a distinguished basepoint, with both multiplicative and additive flavors.

This module provides the minimal scaffolding for structures with a distinguished element, used as the foundational layer for monoids, groups, and rings throughout the algebra hierarchy. The dual classes `Pointed` (with `ide`) and `AddPointed` (with `zro`) share the same underlying structure but differ in naming convention to reflect multiplicative versus additive usage; coercions allow seamless conversion between the two views. The `equals` lemma packages up extensionality for pointed sets, reducing equality to a carrier equality plus compatibility of the basepoint.

#### Classes

- **`Pointed`**: Extends `BaseSet` with a distinguished element `ide : E`, the multiplicative identity / generic basepoint.
- **`AddPointed`**: Extends `BaseSet` with a distinguished element `zro : E`, the additive identity / zero element.

#### Equality

- **`Pointed.equals`**: Extensionality for pointed sets: given a carrier equality `p : X = Y` in `\Set` and a proof `q` that `ide` is preserved across the transport, produces an equality `X = Y` of `Pointed` structures.

#### Coercions

- **`AddPointed.fromPointed`**: Coerces a `Pointed` to an `AddPointed` by reinterpreting `ide` as `zro`.
- **`AddPointed.toPointed`**: Coerces an `AddPointed` to a `Pointed` by reinterpreting `zro` as `ide`.
