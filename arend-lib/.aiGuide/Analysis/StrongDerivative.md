### Analysis.StrongDerivative

Strong (Carathéodory-style) derivatives of maps between topological modules over a near skew field, defined via continuous difference-quotient functions.

The module formalizes differentiation by requiring a continuous "difference quotient" `f'` such that `h *c f' (x, h, a) = f (x + h *c a) - f x` on an open subset `U` of a topological module `X`. A function has a derivative when such a continuous quotient exists, and the quotient is then unique on the appropriate domain. The derivative at a point is recovered by evaluating the quotient at `h = 0`, yielding a linear map `X -> Y`. A "total" variant (`HasTDeriv`, `tderiv`) specializes to functions defined on all of `X`. Standard calculus rules — linearity, scalar multiplication, constants, linear maps, and the bilinear (Leibniz) rule — are established at the level of difference quotients.

#### Difference Quotient Predicate

- **`IsDerivQuot`**: Predicate stating that `f' : DerivDom U -> Y` is a difference quotient for `f`: for all `x, h, a` with `x, x + h *c a ∈ U`, `h *c f' ((x, (h, a)), _) = f (x + h *c a) - f (x)`.
- **`IsDerivQuot.DerivDom`**: The domain of difference quotients — pairs `(x, (h, a))` in `X ⨯ (R ⨯ X)` such that both `x` and `x + h *c a` lie in `U`, equipped with the subspace topology.
- **`IsDerivQuot.unique`**: Uniqueness — any two continuous difference quotients for the same `f` agree on `DerivDom U`.
- **`IsDerivQuot.isDeriv`**: Identifies the directional derivative `deriv Uo f d (x, Ux) a` with the value `f' ((x, (0, a)), _)` of any continuous difference quotient at `h = 0`.

#### Existence and Extraction

- **`HasDeriv`**: Propositional truncation asserting the existence of a continuous difference quotient `f' : ContMap (DerivDom U) Y` for `f`.
- **`HasDeriv-cont`**: Differentiability implies continuity of `f` on `U`.
- **`deriv-quot`**: The (unique) continuous difference quotient extracted from `HasDeriv`, evaluated on `DerivDom U`.
- **`deriv-quot.deriv-tuple`**: Internal extraction of the `(f', IsDerivQuot)` pair as a `\level`-truncated sigma, justified by `IsDerivQuot.unique`.
- **`deriv-isQuot`**: Confirms that `deriv-quot Uo f d` actually satisfies `IsDerivQuot f`.
- **`deriv-quot-cont`**: Continuity transport — given continuous `gx, gh, ga : ContMap R _` and a domain witness, the composite `h ↦ deriv-quot Uo f d (gx h, (gh h, ga h))` is continuous.

#### The Derivative as a Linear Map

- **`deriv`**: The derivative at `x ∈ U` as a `LinearMap X Y`, defined by `a ↦ deriv-quot Uo f d ((x, (0, a)), atZero)`, with `func-+` and `func-*c` proofs of additivity and scalar-homogeneity.
- **`deriv.atZero`**: Witness that `(x, (0, a))` lies in `DerivDom U` whenever `x ∈ U` (since `x + 0 *c a = x`).

#### Total Derivatives (on all of X)

- **`HasTDeriv`**: Specialization of `HasDeriv` to `U = open-top` (the whole space), for `f : X -> Y`.
- **`HasTDeriv.make`**: Builder — produces `HasTDeriv f` from a continuous `f' : ContMap (X ⨯ (R ⨯ X)) Y` satisfying `IsTDerivQuot`.
- **`tderiv-quot`**: Total difference quotient `(x, h, a) ↦ Y`, obtained from `deriv-quot` on the trivial subspace.
- **`IsTDerivQuot`**: Total version of `IsDerivQuot`: `h *c f' (x, (h, a)) = f (x + h *c a) - f x` for all `x, h, a`.
- **`IsTDerivQuot.isTDeriv`**: Recovers `tderiv f d x a = f' (x, (0, a))` from any continuous total difference quotient.
- **`tderiv-isQuot`**: `tderiv-quot` satisfies the difference-quotient identity.
- **`tderiv-quot-cont`**: Continuity of `h ↦ tderiv-quot f d (gx h) (gh h) (ga h)` along continuous parameter curves.
- **`tderiv`**: Total derivative at `x` as a `LinearMap X Y`, defined via `deriv` on the full space.

#### Calculus Rules

- **`deriv_linear`**: The derivative of a continuous linear map `f` is `f` itself: `tderiv f _ x a = f a`.
  - **`deriv_linear.deriv-quot_linear`**: The trivial quotient `(x, (h, a)) ↦ f a` works.
  - **`deriv_linear.has_deriv`**: Continuous linear maps have a total derivative.
- **`deriv_const`**: Constants have zero derivative: `tderiv (λ _ => y) _ x a = 0`.
  - **`deriv_const.deriv-quot_const`**, **`deriv_const.has_deriv`**: Quotient and existence witnesses.
- **`deriv_+`**: Additivity: `deriv (f + g) (x, Ux) a = deriv f (x, Ux) a + deriv g (x, Ux) a`.
  - **`deriv_+.deriv-quot_+`**, **`deriv_+.isCont`**, **`deriv_+.has_deriv`**: Quotient sum, continuity, and existence.
- **`deriv_*c`**: Scalar homogeneity (over a `NearField`): `deriv (c *c f) (x, Ux) a = c *c deriv f (x, Ux) a`.
  - **`deriv_*c.deriv-quot_*c`**, **`deriv_*c.isCont`**, **`deriv_*c.has_deriv`**: Quotient, continuity, and existence.
- **`deriv-quot_bilinear`**: Leibniz rule at the quotient level — for a bilinear `b : BilinearMap X1 X2 Y`, `(x ↦ b (f x) (g x))` has difference quotient `b (Df) (g shifted) + b (f) (Dg)`.
