### Algebra.Group.Fin

Order of elements in groups whose underlying set satisfies the pigeonhole principle.

This module establishes that every element of a `PigeonholeSet`-based group has finite order, and packages this into a computable `order` function when the group additionally has decidable equality. The pigeonhole principle guarantees that the sequence of powers `g^n` must eventually repeat, yielding some `n > 0` with `g^n = 1`. From the truncated existence proof, a search procedure (via `Sequence`'s `search`) extracts the *minimal* such exponent, giving a well-defined notion of order with its characteristic universal property.

#### Existence of Finite Order

- **`hasFiniteOrder`**: For any element `g` of a group `G` whose carrier is a `PigeonholeSet`, there merely exists a positive `n` with `g^n = 1`. Truncated existence statement derived from the pigeonhole principle applied to the sequence of powers.

#### Order Function

- **`order`**: Given `g : G` in a group with pigeonhole carrier and decidable equality, returns the minimal positive natural number `n` such that `g^n = 1`. Defined via `\sfunc` so its computational behavior is controlled.
- **`order.aux`**: Internal sigma packaging the order together with proofs of positivity, the power equation `g^(order g) = 1`, and minimality among all positive exponents satisfying the equation. Uses `search` over naturals with decidable predicates and the truncation eliminator on `hasFiniteOrder`.

#### Characteristic Properties

- **`order>0`**: The order of any element is strictly positive.
- **`order_pow`**: The defining equation `g^(order g) = 1`.
- **`order-min`**: Minimality: if `m > 0` and `g^m = 1`, then `order g <= m`. Together with `order>0` and `order_pow`, this characterizes `order g` uniquely.
