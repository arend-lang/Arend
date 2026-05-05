### Order.Biordered

Order structures combining a strict order with a partial order, where the two relations interact coherently.

#### Classes

- **`BiorderedSet`**: Extends `StrictPoset` and `Poset`. A set carrying both a strict order `<` and a non-strict order `<=` linked by mixed transitivity laws and the implication `< => <=`.
  - **`<-transitive-right`** (`<∘r`): Mixed transitivity `a1 <= a2 -> a2 < a3 -> a1 < a3`.
  - **`<-transitive-left`** (`<∘l`): Mixed transitivity `a1 < a2 -> a2 <= a3 -> a1 < a3`.
  - **`<=-less`**: Strict order implies non-strict: `a1 < a2 -> a1 <= a2`.

- **`BiorderedLattice`**: Extends `BiorderedSet` and `Lattice`. A biordered set whose lattice meet/join interact correctly with the strict order.
  - **`<_meet-univ`**: Universal property of meet w.r.t. `<`: `x < y -> x < z -> x < y ∧ z`.
  - **`<_join-univ`**: Universal property of join w.r.t. `<`: `x < z -> y < z -> x ∨ y < z`.
