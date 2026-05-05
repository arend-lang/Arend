### Order.LexicographicalArray

Lexicographic strict order on fixed-length arrays over a decidable linear order, making `Array A n` itself a decidable linear order.

#### Instance

- **`LexicographicalArray`**: For `n : Nat` and `A : Dec`, instance `Dec (Array A n)` whose strict order compares arrays lexicographically; reuses irreflexivity, transitivity, and trichotomy from the underlying order on `A`.

#### Order Relation

- **`<A`**: Inductive `\Prop`-valued strict order on `Array A n`, defined by recursion on `n`. On non-empty arrays `a :: as` vs `b :: bs`, holds either via `<head` (when `a < b`) or via `<tail` (when `a = b` and `as <A bs`); empty arrays are incomparable.
