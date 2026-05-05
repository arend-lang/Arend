### Topology.CStarAlgebra.UnitCStarAlgebra

Construction of the unitization of a non-unital C*-algebra (StoneC*PseudoAlgebra) into a unital C*-algebra, together with the Riesz space structure induced by absolute values on C*-algebras.

#### Unitization

- **`UnitC*Algebra`**: Given a `StoneC*PseudoAlgebra A`, builds the unital C*-algebra structure on `\Sigma Real A` (the standard adjunction of a unit). Extends `StoneC*Algebra`, with ring structure from `UnitAlgebra RealField A.toRealAlgebra` and norm inherited from the product. Provides commutativity, divisibility, all norm axioms (`norm_zro`, `norm_negative`, `norm_+`, `norm-double`, `norm-bounded`, `norm_*_<=`, `norm_ide_<=`, `norm-ext`), metric completeness, and the C*-identities `c*-sum` and `c*-square`.
- **`UnitC*Algebra.inhabitted`**: Witnesses that there exists `y : A` with `A.norm y <= 1`, used to bootstrap the unitization construction.

#### Riesz Space Structure on C*-Algebras

- **`StoneC*AlgebraRieszSpace`**: Equips a unital `StoneC*Algebra A` with a `RieszSpace` structure, derived via `RieszSpace.fromAbs` from the absolute value built out of the square-root functional calculus.
- **`StoneC*AlgebraRieszSpace.StoneC*AlgebraTopPoset`**: Auxiliary `HausdorffTopPosetAbGroup` instance on `A`, combining its topological abelian group structure with the order on self-adjoint elements (closedness of the positive cone).
- **`StoneC*AlgebraRieszSpace.abs-aux`**: Existence of an absolute value: for every `a : A` there exists `b : A` with `b * b = a * a`, `a <= b`, `-a <= b`, and `b` is the least such upper bound. This is the universal property defining `|a|`.
- **`StoneC*AlgebraRieszSpace.abs-square`**: Identity `|a| * |a| = a * a`, characterizing the absolute value via the square-root of `a²`.

#### Absolute Value via Square Root (`c*abs`)

- **`c*abs`**: For `a : A` with `a <= 1` and `-a <= 1`, defines `|a| := sqrt(a * a)` using the convergent functional-calculus series for square roots in a Banach algebra (`sqrt-t`).
- **`c*abs.yfunc>=0`**: Nonnegativity of the auxiliary sequence `yfunc n x` used in the sqrt power-series expansion.
- **`c*abs.square-norm`**: `norm (1 - a * a) <= 1`, ensuring `a * a` lies in the unit ball where the sqrt series converges.
- **`c*abs.c*abs>=id`** / **`c*abs.c*abs>=neg`**: `x <= c*abs x` and `-x <= c*abs x`, with inductive proof on partial sums (`induction`).
- **`c*abs.zfunc_pow>0`**, **`c*abs.zfunc_pow`**, **`c*abs.zfunc_pow'`**: Compute the sqrt-functional-calculus limit on squares: `zfunc-lim (w * w, _) = w` under norm constraints.
- **`c*abs.yfunc-step`**: Monotonicity of `yfunc` in `n` (`yfunc n x <= yfunc (suc n) x`).
- **`c*abs.zfunc-lim-meet`**: Identifies `zfunc-lim s` as the meet of the sequence `zfunc __ s.1`.
- **`c*abs.yfunc-mono`**: Antitone behaviour of `yfunc n` in its argument.
- **`c*abs.c*abs-univ-bounded`**: Universal property in the bounded regime: any `w` dominating both `x` and `-x` (with `w, 1 - w` in the unit ball) dominates `c*abs x`.
- **`c*abs.sqrt-t`**: Wrapper applying `sqrt` to a unit-ball element.
- **`c*abs.square-inj`**: Injectivity of squaring on `[0,1]`: `x * x = y * y` and `0 <= x, y <= 1` imply `x = y`.
- **`c*abs.elem>=0`**: If `a <= x` and `-a <= x` then `0 <= x`.
- **`c*abs.c*abs>=0`**, **`c*abs.c*abs<=1`**: `c*abs x` is in the positive part of the unit ball.
- **`c*abs.c*abs_*q`**: Compatibility with rational scalar multiplication: `q *q c*abs x = c*abs (q *q x)` for `0 <= q <= 1`.
- **`c*abs.c*abs-univ`**: Full universal property: `c*abs a` is the least element dominating both `a` and `-a`.

#### Riesz Space on the Non-Unital Case

- **`StoneC*PseudoAlgebraRieszSpace`**: Transports the Riesz structure to a non-unital `StoneC*PseudoAlgebra A` by passing through its unitization `UnitC*Algebra A`, then projecting back. The absolute value is constructed by lifting `(0, a)` to the unitization, computing `|·|` there via `abs-aux`, and showing the result has zero scalar component.
- **`StoneC*PseudoAlgebraRieszSpace.StoneC*PseudoAlgebaPoset`**: `PosetQModule` instance on `A` whose order is pulled back along `unit-algebra : A -> UnitC*Algebra A` from the order on the unitization. Provides `<=-refl`, `<=-transitive`, `<=-antisymmetric`, additivity (`<=_+`), and compatibility with rational scalar division (`<=_*n-div`).
