### Algebra.MulOrdered

Monoids equipped with a partial order compatible with multiplication.

This module introduces the basic structures combining order theory with monoid operations, where multiplication is monotone in each argument. The non-commutative version requires separate left and right monotonicity axioms, while the commutative version derives right monotonicity from left monotonicity using commutativity. These classes serve as the foundation for ordered algebraic hierarchies (ordered groups, semirings, rings) where multiplicative operations must respect the order.

#### Classes

- **`OrderedMonoid`**: Extends `Poset` and `Monoid`. A monoid with a partial order such that multiplication is monotone in both arguments. Required fields: `<=_*-left` (right-multiplication preserves order: `x <= y -> x * z <= y * z`) and `<=_*-right` (left-multiplication preserves order: `x <= y -> z * x <= z * y`).
- **`OrderedCMonoid`**: Extends `OrderedMonoid` and `CMonoid`. The commutative variant; `<=_*-right` is automatically derived from `<=_*-left` by transporting along `*-comm` on both sides.

#### Lemmas

- **`<=_*`**: Joint monotonicity of multiplication: given `a <= c` and `b <= d`, concludes `a * b <= c * d`. Combines left and right monotonicity to compare products of pairs of ordered elements.
