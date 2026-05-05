### Arith.Bool

This module provides lattice and order instances for `Bool`.

#### BoolLattice Instance

- **`BoolLattice`**: Instance of `BoundedDistributiveLattice` for `Bool`.
  - `<=` is defined by the `<=` datatype (see below).
  - `meet` is `and`, `join` is `or`.
  - `top` is `true`, `bottom` is `false`.
  - Includes proofs of reflexivity, transitivity, antisymmetry, meet/join laws, and distributivity.
  - **`<=` (datatype)**: Ordering on `Bool` with constructors `false<=_` (`false <= _` for any `Bool`) and `true<=true` (`true <= true`).

#### BoolPoset Instance

- **`BoolPoset`**: Instance of `Dec` (decidable linear order) for `Bool`.
  - `<` is defined by the `<` datatype (see below).
  - Provides irreflexivity, transitivity, and decidable trichotomy.
  - **`<` (datatype)**: Strict ordering on `Bool` with single constructor `false<true` (`false < true`).
