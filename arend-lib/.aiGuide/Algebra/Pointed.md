### Algebra.Pointed

Pointed sets: types equipped with a distinguished element, with both multiplicative and additive naming conventions.

#### Classes

- **`Pointed`**: Extends `BaseSet` with a distinguished element `ide : E` (multiplicative naming, "identity").
- **`AddPointed`**: Extends `BaseSet` with a distinguished element `zro : E` (additive naming, "zero").

#### Equality and Coercions

- **`Pointed.equals`**: Equality of pointed sets from an equality of underlying sets `p : X = Y` together with a proof that the distinguished elements agree along `p` (`coe (p @) ide right = ide`).
- **`AddPointed.fromPointed`**: Coercion `Pointed -> AddPointed` reinterpreting `ide` as `zro`.
- **`AddPointed.toPointed`**: Coercion `AddPointed -> Pointed` reinterpreting `zro` as `ide`.
