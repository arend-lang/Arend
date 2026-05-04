### Analysis.Calculus.SyntheticDerivative

Synthetic differential calculus over a discrete ring, defining differentiability and derivatives of maps between modules via the Kock-Lawvere style nilpotent infinitesimals.

#### Base Ring

- **`DRing`**: Extends `CRing` with an `inv-dense` axiom: two functions on a subset agree everywhere if they agree on invertible elements. Captures rings where invertible elements are "dense" enough to determine equality.
- **`DRing.cancel-lem`**: Cancellation lemma for module-valued functions — if `p.1 *c f p = p.1 *c g p` for all `p`, then `f p = g p`. Used to extract uniqueness of derivatives.

#### Differentiability

- **`isDiff`**: The predicate that `f : \Sigma (x : A) (U x) -> B` is differentiable. For each base point `x`, direction `a`, and infinitesimal scalar `t`, there exists `y : B` with `t *c y = f(x + t*c a) - f x`. The witness `y` is the directional derivative.
- **`isDiff.levelProp`**: `isDiff f` is a proposition — derivatives, when they exist, are unique.
- **`isDiff.isDiff-eq`**: Derivative values agree under propositional equality of base points and scalars.
- **`isDiffT`**: Total-space variant of `isDiff` for functions `f : A -> B` defined on the whole module (trivial subset).

#### The Derivative

- **`deriv`**: Extracts the derivative at a point `x` as a `LinearMap A B`. Linearity (`func-+`, `func-*c`) is established using `cancel-lem` and the `isDiff` data.
- **`deriv.sub-lem`**: Helper showing `U (x.1 + 0 *c a)` from `U x.1`, used to instantiate the differentiability witness at zero.

#### Differentiation Rules

- **`linear_diff`**: Every `LinearMap A B` is differentiable, with derivative equal to the map itself.
- **`const_diff`**: Constant functions are differentiable with zero derivative.
- **`+_diff`**: Pointwise sum of differentiable functions is differentiable; derivative is the sum of derivatives.
- **`o_diff`**: Chain rule — composition of differentiable functions is differentiable. Takes `df` for the inner map (on its first projection) and `dg` for the outer map, producing the derivative of `g ∘ f`.
- **`bilinear_diff`**: A `BilinearMap A B C` is differentiable as a function on `\Sigma A B`, with derivative `m x.1 a.2 + m a.1 x.2 + t *c m a.1 a.2` (Leibniz rule plus a second-order infinitesimal correction).
