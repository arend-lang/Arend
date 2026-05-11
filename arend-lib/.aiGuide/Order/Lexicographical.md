### Order.Lexicographical

Lexicographic orderings on product and list types built from decidable linear orders.

This module lifts decidable linear orders to two standard composite structures: pairs `\Sigma A B` and lists `List A`. The pair ordering compares first components, falling back to the second only when first components are equal. The list ordering, defined inductively as `<L`, treats `nil` as smaller than any nonempty list and otherwise compares head-first with tie-breaking on tails. Both instances package the resulting strict order with irreflexivity, transitivity, and trichotomy proofs to yield a decidable linear order (`Dec`).

#### Lexicographic Product

- **`LexicographicalProduct`**: `Dec` instance on `\Sigma A B` for decidable linear orders `A B`. Defines `a < b` as `a.1 < b.1` or (`a.1 = b.1` and `a.2 < b.2`), with the required strict-order and trichotomy laws.

#### Lexicographic List

- **`LexicographicalList`**: `Dec` instance on `List A` for a decidable linear order `A`, using `<L` as the strict order and supplying irreflexivity, transitivity, and trichotomy.
- **`<L`**: Inductive lexicographic strict order on `List A` (`\Prop`-valued, infix at level 4). Constructors:
  - **`nil<::`**: `nil <L (a :: as)` — the empty list precedes any nonempty list.
  - **`<head`**: `a < b -> (a :: as) <L (b :: bs)` — strict order at the head.
  - **`<tail`**: `a = b -> as <L bs -> (a :: as) <L (b :: bs)` — equal heads, recurse on tails.
