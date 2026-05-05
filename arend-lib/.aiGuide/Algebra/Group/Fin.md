### Algebra.Group.Fin

Finite-order elements in groups: existence and minimal order via the pigeonhole principle.

#### Order Existence

- **`hasFiniteOrder`**: In a group `G` with a `PigeonholeSet` structure, every element `g` has some positive `n` with `g^n = 1`.

#### Order Function

- **`order`**: For `g : G` (with `PigeonholeSet` and `DecSet` on `G`), returns the least positive `n : Nat` such that `g^n = 1`.
- **`order.aux`**: Witnesses the full specification of `order`: a positive exponent `n` with `g^n = 1` that is minimal among all such exponents. Built by searching naturals until finding a witness from `hasFiniteOrder`.

#### Order Properties

- **`order>0`**: The order is strictly positive: `order g > 0`.
- **`order_pow`**: Defining property: `g^(order g) = 1`.
- **`order-min`**: Minimality: any positive `m` with `g^m = 1` satisfies `order g <= m`.
