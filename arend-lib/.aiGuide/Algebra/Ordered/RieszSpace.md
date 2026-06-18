### Algebra.Ordered.RieszSpace

Ordered abelian groups and modules with divisibility properties, culminating in Riesz spaces (lattice-ordered vector spaces over the rationals).

This module builds a tower of ordered algebraic structures: starting from torsion-free posets where positivity can be detected after multiplying by a nonzero natural, extending to ordered rational modules where positivity and ordering interact compatibly with rational scalar multiplication, and finally to Riesz spaces — lattice-ordered Q-modules where absolute value is a derived operation. The `fromAbs` construction provides an alternative way to define a Riesz space: given a `PosetQModule` together with a least upper bound of `a` and `-a` for every element, the join structure can be recovered via the formula `a ∨ b = ½ (a + b + |a − b|)`. This gives two equivalent presentations — one starting from a lattice and deriving `abs`, the other starting from `abs` and deriving the lattice.

#### Posets with Divisibility

- **`PosetDivAbGroup`**: Extends `PosetAbGroup` and `TorsionFreeGroup`. A poset abelian group where `0 <= n *n a` implies `0 <= a` for nonzero `n`. Torsion-freeness is derived from this divisibility property via antisymmetry.
- **`PosetDivAbGroup.<=_*n-div`**: For `n /= 0`, positivity of `n *n a` reflects to positivity of `a`.
- **`PosetDivAbGroup.<=_*n-cancel-left`**: Cancellation `n *n a <= n *n b -> a <= b` for nonzero `n`.

#### Posets with Rational Action

- **`PosetQModule`**: Extends `QModule` and `PosetDivAbGroup`. A rational module whose order interacts compatibly with scalar multiplication by rationals.
- **`PosetQModule.*q_>=0`**: Rational scaling preserves nonnegativity: `q >= 0` and `a >= 0` imply `q *q a >= 0`.
- **`PosetQModule.*q_>=0-cancel`**: For `q > 0`, `q *q a >= 0` reflects to `a >= 0`.
- **`PosetQModule.<=_*q-left`**: Monotonicity in the scalar: `q <= r` and `a >= 0` imply `q *q a <= r *q a`.
- **`PosetQModule.<=_*q-right`**: Monotonicity in the vector: `q >= 0` and `a <= b` imply `q *q a <= q *q b`.
- **`PosetQModule.<=_*q-cancel-left`**: Cancellation `q *q a <= q *q b -> a <= b` for `q > 0`.
- **`PosetQModule.<=_*q-rotate-left`**: Rearranges `a <= q⁻¹ *q b` to `q *q a <= b`.
- **`PosetQModule.<=_*q-rotate-right`**: Rearranges `q⁻¹ *q a <= b` to `a <= q *q b`.
- **`PosetQModule.<=_*q-rotate_finv-left`**: Rearranges `a <= q *q b` to `q⁻¹ *q a <= b`.

#### Riesz Spaces

- **`RieszSpace`**: Extends `PosetQModule`, `AbsAbGroup`, and `LatticeAbGroup.FromJoin`. A lattice-ordered Q-module whose absolute value is determined as `abs a = a ∨ (-a)`. The key axioms `abs>=0` and `<=_*n-div` are derived from the lattice structure (using that `2 *n abs a = (a + (-a)) ∨ ...` is nonnegative).
- **`RieszSpace.join_*q`**: Rational scaling distributes over join: `q *q (x ∨ y) = (q *q x) ∨ (q *q y)` for `q >= 0`.
- **`RieszSpace.abs_*q`**: Absolute value commutes with nonnegative rational scaling: `abs (q *q x) = q *q abs x`.
- **`RieszSpace.meet_*q`**: Rational scaling distributes over meet: `q *q (x ∧ y) = (q *q x) ∧ (q *q y)` for `q >= 0`.
- **`RieszSpace.join_abs`**: Standard formula `x ∨ y = ½ (x + y + |x − y|)`.
- **`RieszSpace.meet_abs`**: Dual formula `x ∧ y = ½ (x + y − |x − y|)`.

#### Constructions

- **`RieszSpace.fromAbs`**: Builds a `RieszSpace` from a `PosetQModule` `A` together with a function producing, for each `a`, a least upper bound of `{a, -a}`. The join is reconstructed via the formula `a ∨ b = ½ (a + b + |a − b|)`, yielding a Riesz space whose absolute value matches the supplied data.
