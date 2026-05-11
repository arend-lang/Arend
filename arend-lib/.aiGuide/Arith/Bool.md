### Arith.Bool

Order-theoretic structure on the booleans, exhibiting `Bool` as both a bounded distributive lattice and a decidable linear order.

This module equips the two-element type `Bool` with its canonical order `false <= true`, derived from a custom inductive `<=` relation. The lattice structure interprets logical conjunction `and` as meet and disjunction `or` as join, with `false` as bottom and `true` as top — the standard Boolean algebra viewed as a distributive lattice. A separate strict-order instance provides decidable trichotomy, making `Bool` usable wherever a `LinearOrder` or decidable poset is required.

#### Lattice Structure

- **`BoolLattice`**: Instance of `BoundedDistributiveLattice Bool`, with `meet = and`, `join = or`, `top = true`, `bottom = false`. Provides the lattice/Boolean-algebra view of `Bool`.
- **`BoolLattice.<=`**: Inductive non-strict order on `Bool` with constructors `false<=_` (so `false <= y` for any `y`) and `true<=true`.

#### Linear Order Structure

- **`BoolPoset`**: Instance of `Dec Bool` (a decidable linear order), giving `Bool` strict comparison, irreflexivity, transitivity, and trichotomy.
- **`BoolPoset.<`**: Inductive strict order on `Bool` with the single constructor `false<true`.
