### Analysis.StrongDerivative

Strong (Fréchet-style) differentiability for maps between topological modules over a near-skew-field, defined via continuous difference-quotient functions.

#### Difference-Quotient Predicate

- **`IsDerivQuot`**: Predicate stating that `f' : DerivDom U -> Y` is a difference quotient for `f`, i.e. `h *c f'((x, (h, a)), _) = f(x + h *c a) - f(x)` for all `x, h, a` with `x, x + h*c a ∈ U`.
- **`IsDerivQuot.DerivDom`**: The topological subspace of `X × (R × X)` consisting of triples `(x, (h, a))` such that both `x` and `x + h *c a` lie in `U`.
- **`IsDerivQuot.unique`**: Two continuous difference quotients for the same `f` on an open set agree pointwise.
- **`IsDerivQuot.isDeriv`**: Connects the difference quotient at `h = 0` to the derivative: `deriv f (x, Ux) a = f'((x, (0, a)), _)`.

#### Existence and Continuity of the Derivative

- **`HasDeriv`**: Propositional truncation asserting that `f : Set.Total U -> Y` admits a continuous difference-quotient function on `DerivDom U`.
- **`HasDeriv-cont`**: A function with a strong derivative on an open set is itself continuous.
- **`deriv-quot`**: Extracts the canonical continuous difference-quotient function from `HasDeriv` proof; well-defined by uniqueness.
- **`deriv-quot.deriv-tuple`**: Lifts `HasDeriv` (a `\Prop`) to the underlying contractible Σ-type of (continuous quotient, IsDerivQuot proof) pairs.
- **`deriv-isQuot`**: The extracted `deriv-quot` indeed satisfies `IsDerivQuot`.
- **`deriv-quot-cont`**: Composition continuity: pulling back `deriv-quot` along continuous maps `gx, gh, ga : R -> X/R/X` yields a continuous map `R -> Y` (under appropriate domain conditions).

#### The Derivative as a Linear Map

- **`deriv`**: The strong derivative `deriv Uo f d (x, Ux) : LinearMap X Y`, defined by `a ↦ deriv-quot Uo f d ((x, (0, a)), _)`, with proofs of additivity (`func-+`) and `R`-scalar homogeneity (`func-*c`) derived from uniqueness of `deriv-quot`.
- **`deriv.atZero`**: Witness that `(x, (0, a))` lies in `DerivDom U` whenever `x ∈ U` (since `x + 0 *c a = x`).

#### Total Derivatives (Domain `U = X`)

- **`HasTDeriv`**: Specialization of `HasDeriv` to the whole space (`U = open-top`), the "total" derivative case.
- **`HasTDeriv.make`**: Constructs `HasTDeriv f` from a continuous map `f' : X × (R × X) -> Y` satisfying `IsTDerivQuot`.
- **`tderiv-quot`**: Difference quotient `(x, h, a) ↦ Y` for total derivatives, evaluating `deriv-quot` at the trivial domain witness.
- **`IsTDerivQuot`**: Total-space version of `IsDerivQuot`: `h *c f'(x, (h, a)) = f(x + h *c a) - f(x)` for all arguments.
- **`IsTDerivQuot.isTDeriv`**: Identifies the total derivative `tderiv f d x a` with `f'(x, (0, a))` for any continuous quotient `f'`.
- **`tderiv-isQuot`**: `tderiv-quot` is a total difference quotient: `h *c tderiv-quot f d x h a = f(x + h *c a) - f x`.
- **`tderiv-quot-cont`**: Continuity of `tderiv-quot` precomposed with continuous maps `gx, gh, ga : R -> X/R/X`.
- **`tderiv`**: The total strong derivative `tderiv f d x : LinearMap X Y`.

#### Calculus Rules

- **`deriv_linear`**: A continuous linear map is its own total derivative: `tderiv f x a = f a`.
  - **`deriv_linear.deriv-quot_linear`**: The trivial difference quotient `(x, h, a) ↦ f a` works for linear `f`.
  - **`deriv_linear.has_deriv`**: Continuous linear maps have a total derivative.
- **`deriv_const`**: The derivative of a constant function is `0`.
  - **`deriv_const.deriv-quot_const`** / **`has_deriv`**: Difference quotient and existence proof for constants.
- **`deriv_+`**: Sum rule: `deriv (f + g) = deriv f + deriv g` on an open set.
  - **`deriv_+.deriv-quot_+`**, **`isCont`**, **`has_deriv`**: Componentwise difference quotient, its continuity, and existence of the derivative for `f + g`.
- **`deriv_*c`**: Scalar multiplication rule: `deriv (c *c f) = c *c deriv f` (requires `R : NearField`, i.e. commutative).
  - **`deriv_*c.deriv-quot_*c`**, **`isCont`**, **`has_deriv`**: Quotient, continuity, existence for `c *c f`.
- **`deriv-quot_bilinear`**: Leibniz/product rule for a bilinear map `b : X1 → X2 → Y`: the difference quotient of `x ↦ b (f x) (g x)` is `b (Df s) (g(x + h*c a)) + b (f x) (Dg s)`.
