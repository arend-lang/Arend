### Algebra.Monoid

Defines semigroups, monoids, their commutative and cancellative variants, divisibility relations, and invertible elements.

#### Core Algebraic Structures

- **`Semigroup`**: Extends `BaseSet` with an associative binary operation `*`.
- **`Monoid`**: Extends `Pointed` and `Semigroup`; adds left/right identity laws (`ide-left`, `ide-right`) for `ide`.
- **`CSemigroup`**: Commutative semigroup; extends `Semigroup` with `*-comm`.
- **`CMonoid`**: Commutative monoid; extends `Monoid` and `CSemigroup`. Identity laws are derived from commutativity.
- **`CancelMonoid`**: Monoid with left and right cancellation: `cancel_*-left`, `cancel_*-right`.
- **`CancelCMonoid`**: Commutative cancellative monoid; right cancellation is derived from left cancellation and commutativity.
- **`AddMonoid`**: Additive monoid; extends `AddPointed` with `+`, `zro-left`, `zro-right`, `+-assoc`. Coerces to/from `Monoid`.
- **`AbMonoid`**: Abelian (commutative additive) monoid; extends `AddMonoid` with `+-comm`. Coerces to/from `CMonoid`.

#### Monoid Equality

- **`Monoid.equals`**: Extensionality for monoids: equal iff carrier sets are equal and multiplication is preserved.

#### Divisibility Base

- **`DivBase`**: Common base for divisibility records: bundles a monoid `M`, a divisor `val`, a target `elem`, and a witness `inv`.

#### Left Divisibility (`LDiv`)

- **`LDiv`**: Extends `DivBase` with `inv-right : val * inv = elem`, expressing `val` divides `elem` on the left.
- **`LDiv.make`**: Construct an `LDiv` from `c` and a proof `a * c = b`.
- **`LDiv.product`**: Componentwise product of divisibilities in a `CMonoid`: `LDiv (x*z) (y*w)`.
- **`LDiv.product-left`**, **`LDiv.product-right`**: Multiply both sides of a divisibility on the left/right.
- **`LDiv.factor-left`**, **`LDiv.factor-right`**: Extend a divisor to divide a product.
- **`LDiv.cancel-left`**, **`LDiv.cancel-right`**: Cancel a common factor in a cancellative (commutative) monoid.
- **`LDiv.trans`**: Transitivity of left divisibility.
- **`LDiv.ide-div`**, **`LDiv.id-div`**: Identity divides everything; everything divides itself.
- **`LDiv.swap`**: In a `CMonoid`, swaps `val` and `inv`.
- **`LDiv.idempt`**: Idempotent divisibility lemma.
- **`LDiv.cancelProp`**: Under left-cancellability, `LDiv x y` is a proposition.
- **`LDiv.fromTruncP`**: Extract an `LDiv` from its propositional truncation when cancellation holds.
- **`LDiv.levelProp`**: `LDiv x y` is a proposition in any `CancelMonoid`.

#### Right Divisibility (`RDiv`)

- **`RDiv`**: Extends `DivBase` with `inv-left : inv * val = elem`.
- **`RDiv.product-right`**, **`RDiv.cancel-right`**, **`RDiv.trans`**: Right-divisibility analogues of `LDiv` lemmas.
- **`RDiv.levelProp`**: `RDiv x y` is a proposition in any `CancelMonoid`.

#### One-Sided Inverses

- **`LInv`**: Left-inverse; extends `RDiv` with `elem = ide`.
- **`LInv.cancel`**: A left-invertible element cancels on the left.
- **`RInv`**: Right-inverse; extends `LDiv` with `elem = ide`.
- **`RInv.cancel`**: A right-invertible element cancels on the right.

#### Two-Sided Inverses (`Inv`)

- **`Inv`**: Two-sided invertibility; extends both `LInv` and `RInv`.
- **`Inv.inv-isUnique`**: Inverses of equal elements are equal.
- **`Inv.levelProp`**: Being an inverse of a fixed element is a proposition.
- **`Inv.ide-isInv`**: The identity is invertible.
- **`Inv.product`**, **`Inv.prod`**: Product of two invertibles is invertible (with explicit inverse `j.inv * i.inv`).
- **`Inv.factor-right`**, **`Inv.factor-left`**: If `x * y` is invertible and one factor has a one-sided inverse, the other factor is invertible.
- **`Inv.lmake`**, **`Inv.rmake`**: Build `Inv x` in a `CMonoid` from a single one-sided inverse equation.
- **`Inv.ldiv`**, **`Inv.rdiv`**: Build `Inv x` in a `CMonoid` from a divisibility witness of `ide`.
- **`Inv.cfactor-right`**, **`Inv.cfactor-left`**: In a `CMonoid`, invertibility of a product implies invertibility of each factor.

#### Associates

- **`associates`**: `associates x y` is the type of `(u : Inv, x = u * y)`, expressing that `x` and `y` differ by a unit.
- **`associates.levelProp`**: In a `CancelMonoid`, `associates x y` is a proposition.
- **`associates-sym`**: Symmetry of the associates relation.
