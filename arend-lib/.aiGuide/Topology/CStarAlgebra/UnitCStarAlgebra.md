### Topology.CStarAlgebra.UnitCStarAlgebra

Construction of the unitization of a (possibly non-unital) Stone C*-algebra and the induced Riesz space structure on Stone C*-algebras.

This module shows that any `StoneC*PseudoAlgebra` `A` can be embedded into a unital Stone C*-algebra `\Sigma Real A`, where the real component supplies a unit and the norm is defined operator-theoretically as a supremum over unit-ball multiplications. It then derives a canonical Riesz space (lattice ordered abelian group) structure on every Stone C*-algebra by constructing an absolute value `|a|` as the unique positive square root of `a * a`, built via the functional calculus `sqrt` from `RealBanachAlgebra`. The pseudo-algebra case is handled by transferring the Riesz structure through the unitization, using the order pulled back along the unit embedding.

#### Unitization

- **`UnitC*Algebra`**: Instance constructing a `StoneC*Algebra` on `\Sigma Real A` for any `StoneC*PseudoAlgebra` `A`, using the unital algebra `UnitAlgebra RealField A.toRealAlgebra`. The norm is `|x.1| ∨ sup { |x.1 *r s + x.2 * s| : s ∈ A, |s| ≤ 1 }`, the standard operator norm on the unitization.
- **`UnitC*Algebra.inhabitted`**: Existence lemma `∃ (y : A) (|y| ≤ 1)`, needed to ensure the join in the norm is over an inhabited family.

#### Riesz Space on Stone C*-Algebras

- **`StoneC*AlgebraRieszSpace`**: Equips any `StoneC*Algebra A` with a `RieszSpace` structure, using `RieszSpace.fromAbs` together with the absolute value produced by `abs-aux`.
- **`StoneC*AlgebraRieszSpace.StoneC*AlgebraTopPoset`**: The underlying `HausdorffTopPosetAbGroup` on `A`, combining its topological abelian group and poset structure with closedness of the positive cone.
- **`StoneC*AlgebraRieszSpace.abs-aux`**: Existence of an absolute value: for every `a : A`, there is `b` with `b * b = a * a`, `a ≤ b`, `-a ≤ b`, and `b` is the least such upper bound.
- **`StoneC*AlgebraRieszSpace.abs-square`**: `|a| * |a| = a * a`, characterizing the absolute value.

#### Functional Calculus Absolute Value

- **`c*abs`**: For `a : A` with `a ≤ 1` and `-a ≤ 1`, defines `|a| := sqrt(a * a)` using the square-root functional calculus from `RealBanachAlgebra`.
- **`c*abs.yfunc>=0`**: Positivity of the `n`th approximant `yfunc n x` used in the sqrt iteration.
- **`c*abs.square-norm`**: `|1 - a * a| ≤ 1`, the bound enabling the sqrt power series to converge on `a * a`.
- **`c*abs.c*abs>=id`**, **`c*abs.c*abs>=neg`**: `x ≤ |x|` and `-x ≤ |x|`; the absolute value dominates `x` and `-x`.
- **`c*abs.c*abs>=id.induction`**: Inductive step `x ≤ 1 - yfunc n (x * x)` driving `c*abs>=id`.
- **`c*abs.zfunc_pow>0`**, **`c*abs.zfunc_pow`**, **`c*abs.zfunc_pow'`**: Identities `zfunc-lim (w * w) = w` under positivity/norm hypotheses, expressing that sqrt inverts squaring on positive elements.
- **`c*abs.zfunc_pow>0.square-norm`**: Auxiliary norm bound `|1 - w * w| ≤ 1` from `|1 - w| ≤ 1`.
- **`c*abs.yfunc-step`**, **`c*abs.yfunc-mono`**: Monotonicity of the sqrt approximants in the index and in the argument.
- **`c*abs.zfunc-lim-meet`**: `zfunc-lim s` is a meet of the approximants `zfunc n s.1`.
- **`c*abs.c*abs-univ-bounded`**: Universal property in the unit ball: any `w` with `w ≤ 1`, `|1 - w| ≤ 1`, `x ≤ w`, `-x ≤ w` dominates `c*abs x`.
- **`c*abs.sqrt-t`**: Wrapper `sqrt s.1 s.2` for elements of the unit ball.
- **`c*abs.square-inj`**: Injectivity of squaring on positive elements bounded by 1: `x * x = y * y ⇒ x = y`.
- **`c*abs.elem>=0`**: Any element bounding both `a` and `-a` is non-negative.
- **`c*abs.c*abs>=0`**, **`c*abs.c*abs<=1`**: `0 ≤ |a| ≤ 1`.
- **`c*abs.c*abs_*q`**: Compatibility with rational scaling: `q · |x| = |q · x|` for `0 ≤ q ≤ 1`.
- **`c*abs.c*abs-univ`**: Full universal property: `|a| ≤ w` whenever `a ≤ w` and `-a ≤ w`, certifying `c*abs` as the least absolute value.

#### Riesz Space on Pseudo-Algebras

- **`StoneC*PseudoAlgebraRieszSpace`**: Riesz space structure on a `StoneC*PseudoAlgebra A`, obtained by embedding `a ↦ (0, a)` into `UnitC*Algebra A` and pulling back the absolute value, observing that the resulting `b` lives in the `A`-component since `b.1 * b.1 = 0`.
- **`StoneC*PseudoAlgebraRieszSpace.StoneC*PseudoAlgebaPoset`**: `PosetQModule` on `A` whose order is `a ≤ b ⟺ unit-algebra a ≤ unit-algebra b`, transported from the unitization.
