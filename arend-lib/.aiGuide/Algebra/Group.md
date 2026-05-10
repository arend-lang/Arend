### Algebra.Group

Group structures and their additive/abelian/commutative variants, built on top of monoids.

This module defines the core algebraic hierarchy for groups: a `Group` extends `CancelMonoid` with an `inverse` operation satisfying the standard left/right inverse laws, and cancellation is derived rather than postulated. Parallel additive (`AddGroup`) and commutative (`CGroup`, `AbGroup`) variants are provided, along with coercions between additive and multiplicative presentations. The module also develops integer powers (`ipow`, `*i`), a constructive apartness extension (`With#`) for groups where the relation `#0` distinguishes elements from zero, and decidable variants (`Dec`, `FinGroup`).

#### Multiplicative Groups

- **`Group`**: Class extending `CancelMonoid` with `inverse : E -> E` and laws `inverse-left`, `inverse-right`. Cancellation, identity, and inverse laws are mutually derivable; default implementations close the loop. Includes `op` for the opposite group.
- **`Group.Dec`**: Group with decidable equality (`extends Group, DecSet`).
- **`CGroup`**: Commutative group, extending `Group` and `CancelCMonoid`; `inverse-right` follows from commutativity.
- **`FinGroup`**: Finite group with decidable equality (`extends Group, Group.Dec, FinSet`).

#### Integer Powers and Inverse Lemmas

- **`ipow`**: Integer power `ipow a x` — `pow a n` for positive, `pow (inverse a) n` for negative.
- **`ipow_+`**, **`ipow_*`**, **`ipow_negaitve`**, **`ipow_ide`**: Standard exponent laws for `ipow`.
- **`inverse-isInv`**: `inverse (inverse x) = x`.
- **`inverse_ide`**: `inverse ide = ide`.
- **`inverse_*`**: `inverse (x * y) = inverse y * inverse x`.
- **`inverse_pow`**: `inverse (pow x n) = pow (inverse x) n`.
- **`makeInv`**: Packages `inverse a` as a `Monoid.Inv` witness.
- **`check-for-inv`**: From `x * y = ide` conclude `y = inverse x`.
- **`equality-check`**, **`equality-corrolary`**: Translate between `g = h` and `inverse g * h = ide`.

#### Group Equality and Translations

- **`Group.inverse-equality`**: Inverses agree between two group structures sharing identity and multiplication.
- **`Group.equals`**: Lifts an equality of underlying monoids to an equality of groups.
- **`Group.make-inverse-left`**, **`Group.make-ide-left`**: Bootstrapping helpers used to derive missing identity/inverse laws from one-sided versions.
- **`Group.translate-is-Equiv`**: Left translation `(h *)` is a quasi-equivalence with inverse `(inverse h *)`.

#### Group-Theoretic Operations

- **`/`**: Division `x / y = x * inverse y`.
- **`conjugate`**: `g * h * inverse g`.
- **`conjugate-via-id`**: `conjugate ide g = g`.

#### Additive Groups

- **`AddGroup`**: Class extending `AddMonoid` with `negative`, `negative-left`, `negative-right`.
- **`AddGroup.fromGroup`**, **`AddGroup.toGroup`**: Coercions between `Group` and `AddGroup` views of the same data.
- **`AddGroup.negative-equality`**: Negation agrees between two `AddGroup` structures sharing zero and addition.
- **`-`**: Subtraction `x - y = x + negative y`.

#### Cancellation and Negation Lemmas

- **`cancel-left`**, **`cancel-right`**: Additive cancellation.
- **`negative-isInv`**: `negative (negative x) = x`.
- **`negative_+`**: `negative (x + y) = negative y - x`.
- **`negative_-`**: `negative (x - y) = y - x`.
- **`negative_zro`**: `negative zro = zro`.
- **`minus_zro`**: `x - zro = x`.
- **`fromZero`**, **`toZero`**: Convert between `x = y` and `x - y = zro`.
- **`*n_negative`**: `n *n negative a = negative (n *n a)`.
- **`diff_+`**: `(z - y) + (y - x) = z - x`.

#### Integer Scalar Multiplication

- **`*i`**: Integer scalar multiplication on an `AddGroup`.
- **`*i_ipow`**: `x *i a = ipow a x` under the multiplicative view.
- **`*i-assoc`**: `(x * y) *i a = x *i (y *i a)`.
- **`*i-rdistr`**: `(x + y) *i a = x *i a + y *i a`.

#### Apartness for Additive Groups

- **`AddGroup.With#`**: Class extending `AddGroup` and `Set#` with a tight apartness `#0 : E -> \Prop` characterizing nonzero elements; the `#` of `Set#` is derived as `#0 (x - y)`.
- **`apartNonZero`**: `#0 x` implies `x /= zro`.
- **`#0-negative-inv`**: `#0 (negative x)` implies `#0 x`.
- **`#0-+-left`**, **`#0-+-right`**: Splitting apartness across sums.
- **`#0-BigSum`**, **`#0-BigSum-conv`**: Apartness propagates to/from a `BigSum`'s entries.
- **`AddGroup.Dec`**: Decidable apartness variant (`extends With#, DecSet`); `#0` defaults to `(_ /= zro)`, with `nonZeroApart` as the bridge to constructive apartness.
- **`decide#0`**: Decidability of `a = 0` versus `#0 a`.

#### Commutative Group Lemmas

- **`CGroup.ipow_*-comm`**: `ipow (a * b) x = ipow a x * ipow b x`.
- **`CGroup.BigProd_inverse`**: `inverse` distributes over `BigProd`.
- **`CGroup.BigProd_ipow`**: `BigProd` commutes with `ipow`.

#### Abelian (Additive Commutative) Group Lemmas

- **`AbGroup`**: Class extending `AddGroup` and `AbMonoid`; `negative-right` follows from `+-comm`.
- **`AbGroup.fromCGroup`**, **`AbGroup.toCGroup`**: Coercions between `CGroup` and `AbGroup`.
- **`AbGroup.equals`**: Lifts an equality of underlying `AddGroup`s to `AbGroup` equality.
- **`*n-ldistr_-`**: `n *n (a - b) = n *n a - n *n b`.
- **`*i-ldistr`**: `x *i (a + b) = x *i a + x *i b`.
- **`BigSum_negative`**: `negative` distributes over `BigSum`.
- **`sum-cancel-left`**, **`sum-cancel-right`**: Cancellation inside additive differences.
- **`diff-cancel-left`**: `x - z - (x - y) = y - z`.
- **`negative_+-comm`**: `negative (x + y) = negative x - y`.
- **`diff_sum`**: `x - (y + z) = x - y - z`.
- **`diff_diff`**: `x - (y - z) = x - y + z`.
