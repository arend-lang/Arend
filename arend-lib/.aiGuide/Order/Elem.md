### Order.Elem

Order structures inherited by subsets via the underlying carrier's order.

This module lifts standard order classes from a type `X` to `Elem U`, the type of elements of a subset `U : Set X`. Each instance defines the order on pairs by projecting to the first component (`.1`), so the subset inherits the ambient order pointwise. This provides a uniform way to view any subset of an ordered structure as an ordered structure in its own right, which is useful when restricting attention to a sub-collection while preserving order-theoretic reasoning.

#### Inherited Order Instances

- **`ElemStrictPoset`**: Strict poset structure on `Elem U` for `U : Set X` with `X : StrictPoset`. Defines `x < y` as `x.1 < y.1` and inherits irreflexivity and transitivity from `X`.
- **`ElemPoset`**: Poset structure on `Elem U` for `U : Set X` with `X : Poset`. Defines `x <= y` as `x.1 <= y.1` and inherits reflexivity, transitivity, and antisymmetry from `X`.
- **`ElemBiordered`**: `BiorderedSet` structure on `Elem U` combining `ElemStrictPoset` and `ElemPoset`. Inherits the compatibility axioms (`<-transitive-right`, `<-transitive-left`, `<=-less`) tying `<` and `<=` together from the ambient biordered set.
- **`ElemLinearOrder`**: Linear order structure on `Elem U` for `U : Set X` with `X : LinearOrder`. Built on `ElemStrictPoset`, inheriting comparison and connectedness from `X`.
