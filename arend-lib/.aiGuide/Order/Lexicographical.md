### Order.Lexicographical

Lexicographical orderings on pairs and lists, lifting decidable linear orders componentwise.

#### Product Order

- **`LexicographicalProduct`**: Decidable linear order instance on `\Sigma A B` for `A B : Dec`. Compares first components, breaking ties by the second: `a < b` iff `a.1 < b.1 \/ (a.1 = b.1 /\ a.2 < b.2)`. Provides irreflexivity, transitivity, and trichotomy derived from the underlying orders.

#### List Order

- **`LexicographicalList`**: Decidable linear order instance on `List A` for `A : Dec`, using the lexicographic extension `<L`.
- **`<L`**: Inductive strict order on lists. `nil <L (b :: bs)` always holds (`nil<::`); for cons cells, either the heads compare strictly (`<head`) or the heads agree and the tails compare recursively (`<tail`).
