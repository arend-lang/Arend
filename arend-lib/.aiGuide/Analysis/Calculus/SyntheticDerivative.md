### Analysis.Calculus.SyntheticDerivative

Synthetic differential calculus over a "differential ring" where derivatives are characterized by an algebraic remainder condition rather than limits.

This module formalizes differentiation in the style of synthetic differential geometry, replacing analytic limits with the algebraic property that `f(x + t·a) - f(x) = t·y` for some unique `y` (the directional derivative). The key structure is `DRing`, a commutative ring satisfying an "inverse-density" axiom that lets one cancel `t` whenever the equation holds for all invertible `t`. From this, derivatives become honest linear maps, and the standard calculus rules — linearity, composition (chain rule), and the Leibniz rule for bilinear maps — are derived purely algebraically. The use of a predicate `U` on the domain allows differentiation on arbitrary subsets, supporting partial functions.

#### Differential Ring

- **`DRing`**: A commutative ring extending `CRing` with the **`inv-dense`** axiom: any two functions defined on a subset that agree on all invertible elements agree everywhere. This is the core principle that makes derivatives unique despite being defined via the existence of remainders.
- **`DRing.cancel-lem`**: Cancellation lemma — if `p.1 *c f p = p.1 *c g p` for all elements in the subset, then `f = g`. Uses `inv-dense` to extend cancellation from invertibles to the whole domain.

#### Differentiability Predicate

- **`isDiff`**: The differentiability predicate for `f : Σ (x : A) (U x) → B`. Asserts that for every base point `x`, direction `a`, and scalar `t` (with `x + t·a` still in `U`), there exists `y : B` with `t·y = f(x + t·a) - f(x)`. The witness `y` is the directional derivative at `x` in direction `a`.
- **`isDiff.levelProp`**: Proof that `isDiff f` is a proposition — derivatives are unique when they exist, via `cancel-lem`.
- **`isDiff.isDiff-eq`**: Equality lemma showing the derivative value depends only on the base point and the scalar, not on the proofs of subset membership.
- **`isDiffT`**: Differentiability for total functions `A → B`, defined as `isDiff` over the trivial subset.

#### Derivative as Linear Map

- **`deriv`**: Extracts the derivative of a differentiable `f` at a point `x` as a `LinearMap A B`. The underlying function takes a direction `a` to the witness `y` produced by `isDiff` at `t = 0`. Linearity (`func-+`, `func-*c`) follows from uniqueness of remainders.
- **`deriv.sub-lem`**: Shows `U (x.1 + 0 *c a)` holds, used to evaluate the derivative at the base point.

#### Calculus Rules

- **`linear_diff`**: Linear maps are differentiable, with derivative equal to themselves: `f a` is the witness, since `f(x + t·a) - f x = t · f a`.
- **`const_diff`**: Constant functions are differentiable with derivative `0`.
- **`+_diff`**: Sum rule — if `f` and `g` are differentiable, so is `f + g`, with derivatives adding pointwise.
- **`o_diff`**: Chain rule — composition `g ∘ f` is differentiable when both `f` and `g` are; the derivative of `f` shifts the base point of `g`'s derivative.
- **`bilinear_diff`**: Leibniz rule for bilinear maps — `m : A × B → C` is differentiable with directional derivative `m x.1 a.2 + m a.1 x.2 + t · m a.1 a.2`, recovering the product rule when `m` is multiplication.
