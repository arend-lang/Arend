### Order.Elem

Lifts order structures from a type `X` to its subset `Elem U`, inheriting strict, partial, biordered, and linear order instances componentwise.

#### Order Instances on Subsets

- **`ElemStrictPoset`**: Strict poset structure on `Elem U` for `U : Set X` with `X : StrictPoset`, defined by `x < y := x.1 < y.1`; inherits irreflexivity and transitivity from `X`.
- **`ElemPoset`**: Poset structure on `Elem U` for `U : Set X` with `X : Poset`, defined by `x <= y := x.1 <= y.1`; antisymmetry uses `ext` to lift equality of underlying elements to equality in `Elem U`.
- **`ElemBiordered`**: `BiorderedSet` instance on `Elem U` combining `ElemStrictPoset` and `ElemPoset`, with cross-relation lemmas `<∘r`, `<∘l`, and `<=-less` lifted pointwise.
- **`ElemLinearOrder`**: `LinearOrder` instance on `Elem U` for `X : LinearOrder`, providing `<-comparison` (comparability against a third element) and `<-connectedness` (lifted via `ext`) on top of `ElemStrictPoset`.
