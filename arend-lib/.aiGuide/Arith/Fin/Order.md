### Arith.Fin.Order

This module provides a linear order instance for `Fin n`.

#### FinOrder Instance

- **`FinOrder`**: Instance of `LinearOrder.Dec` for `Fin n`, with `<` inherited from `NatSemiring.<`, irreflexivity and transitivity from `NatSemiring`, and decidable trichotomy via `fin_nat-inj`.
