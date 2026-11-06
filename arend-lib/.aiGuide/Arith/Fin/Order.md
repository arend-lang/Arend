### Arith.Fin.Order

Linear order instance on the finite types `Fin n`.

This module equips each `Fin n` with the structure of a decidable linear order by inheriting the strict order from `Nat`. The comparison `i < j` on `Fin n` is defined directly as the underlying natural number comparison via `NatSemiring.<`, with irreflexivity, transitivity, and trichotomy lifted from the corresponding properties on `Nat`. This makes `Fin n` immediately usable wherever a `LinearOrder.Dec` is required, such as in sorting, indexing, and finite combinatorial arguments.

#### Order Instance

- **`FinOrder`**: `\instance FinOrder (n : Nat) : LinearOrder.Dec (Fin n)` — registers `Fin n` as a decidable linear order with `<` given by the natural-number strict order, so any `Fin n` can be used in contexts requiring a decidable linear order.
