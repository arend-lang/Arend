### Order.LexicographicalArray

Lexicographic ordering on fixed-length arrays over a decidable linear order.

This module lifts the decidable linear order of an element type `A : Dec` to arrays of length `n` by comparing entries left-to-right: the first differing position determines the order. The relation `<A` is defined by recursion on the array structure, with two constructors capturing the two cases where the head is strictly smaller or where the heads are equal and the tails recurse. The resulting `LexicographicalArray` instance equips `Array A n` with a decidable linear order, making arrays usable wherever a `Dec` instance is required (e.g., as keys in ordered structures).

#### Order Relation

- **`<A`**: Lexicographic strict order on `Array A n`, defined inductively. For `suc n` with `a :: as` and `b :: bs`, either `<head` (the heads satisfy `a < b`) or `<tail` (the heads are equal `a = b` and the tails satisfy `as <A bs`). The empty-array case is excluded by the eliminator, so no two length-0 arrays are related.

#### Decidable Linear Order Instance

- **`LexicographicalArray`**: `\instance` providing `Dec (Array A n)` for any `n : Nat` and `A : Dec`, using `<A` as the strict order. Supplies the three required structural laws — `<-irreflexive`, `<-transitive`, and `trichotomy` (with respect to the induced strict poset structure) — so that arrays inherit a fully decidable linear order from `A`.
