### Algebra.Ordered.TruncRieszSpace

Riesz spaces equipped with a truncation operator, the Boolean elements it singles out, and the resulting Boolean ring.

A `TruncRieszSpace` is a `RieszSpace` with an operator `trunc` (truncation at a unit) that is below the identity, sends `0` to a nonnegative element, and satisfies `a ∧ trunc b <= trunc a`. The module derives its basic behaviour: monotonicity, idempotence, compatibility with `∨` and `∧`, a 1-Lipschitz estimate, and additivity on disjoint elements (including finite disjoint sums). Elements fixed by `trunc` are `IsUnderUnit`. An element `e` is `IsBoolean` when `trunc (e + e) = e`; the Boolean elements are closed under `∧`, `∨`, positive part of differences and absolute value of differences, and under the operations `(a, b) ↦ |a - b|` (as addition) and `∧` (as multiplication) they form a Boolean ring `BoolRing`. The module also computes the truncation of rational multiples of Boolean elements and bounds the defect `trunc (x + x) - x`. Finally `TruncRieszSpaceHom` is the structure of homomorphisms commuting with `trunc`.

#### The Truncation Operator

- **`TruncRieszSpace`**: Class of Riesz spaces with an operator `trunc : E -> E` such that `0 <= trunc 0`, `trunc a <= a` and `a ∧ trunc b <= trunc a`.
- **`TruncRieszSpace.trunc_zro`**: `trunc 0 = 0`.
- **`TruncRieszSpace.trunc-pos`**: Truncation preserves nonnegativity: `0 <= a` implies `0 <= trunc a`.
- **`TruncRieszSpace.trunc-neg`**: Truncation fixes nonpositive elements: `a <= 0` implies `trunc a = a`.
- **`TruncRieszSpace.trunc-mono`**: `trunc` is monotone.
- **`TruncRieszSpace.trunc-idemp`**: `trunc` is idempotent.
- **`TruncRieszSpace.trunc_join`**: `trunc` preserves binary joins: `trunc (a ∨ b) = trunc a ∨ trunc b`.
- **`TruncRieszSpace.trunc_meet`**: `trunc` preserves binary meets: `trunc (a ∧ b) = trunc a ∧ trunc b`.
- **`TruncRieszSpace.trunc-spliit`**: Truncation is determined by the positive and negative parts: `trunc a = trunc (part+ a) - part- a`.

#### Lipschitz Estimates and Disjoint Additivity

- **`TruncRieszSpace.trunc-diff-cmp`**: If `b <= a` then `trunc a - trunc b <= a - b`.
- **`TruncRieszSpace.trunc-diff`**: `trunc a - trunc b <= part+ (a - b)` for all `a`, `b`.
- **`TruncRieszSpace.trunc-lipschitz`**: `trunc` is 1-Lipschitz: `abs (trunc a - trunc b) <= abs (a - b)`.
- **`TruncRieszSpace.trunc_+-pos-disjoint`**: For nonnegative `a`, `b` with `a ∧ b = 0`, `trunc (a + b) = trunc a + trunc b`.
- **`TruncRieszSpace.trunc_+-disjoint`**: Truncation is additive on disjoint elements (`AreDisjoint a b`).
- **`TruncRieszSpace.trunc_BigSum-disjoint`**: Truncation of the big sum of a pairwise disjoint array is the big sum of the truncations.

#### Elements Under the Unit

- **`TruncRieszSpace.IsUnderUnit`**: Proposition that `a` is fixed by truncation, `trunc a = a`.
- **`TruncRieszSpace.InUnderUnit_<=`**: Being under the unit is downward closed: `a <= b` and `IsUnderUnit b` give `IsUnderUnit a`.

#### Rational Multiples of Boolean Elements

- **`TruncRieszSpace.trunc_*q-boolean_<=1`**: For a Boolean `e` and a rational `q <= 1`, `q *q e` is under the unit.
- **`TruncRieszSpace.trunc_*q-boolean_>=1`**: For a Boolean `e` and a rational `q >= 1`, `trunc (q *q e) = e`.
- **`TruncRieszSpace.trunc_*q-boolean`**: For a Boolean `e` and any rational `q`, `trunc (q *q e) = (q ∧ 1) *q e`.

#### Boolean Elements

- **`TruncRieszSpace.IsBoolean`**: Proposition that `a` is Boolean: `trunc (a + a) = a`.
- **`TruncRieszSpace.IsBoolean>=0`**: Boolean elements are nonnegative.
- **`TruncRieszSpace.IsBoolean_zro`**: `0` is Boolean.
- **`TruncRieszSpace.IsBoolean-trunc`**: Boolean elements are under the unit.
- **`TruncRieszSpace.defect`**: The defect `trunc (x + x) - x` of an element, which vanishes exactly on Boolean elements.
- **`TruncRieszSpace.IsBoolean-defect`**: For a Boolean `e`, `abs (defect x) <= 3 * abs (x - e)` (written as the threefold sum), so the defect is 3-Lipschitz and zero on Boolean elements.
- **`TruncRieszSpace.IsBoolean-orth`**: For a Boolean `e` and `y` under the unit, `e` and `part+ (y - e)` are disjoint: `e ∧ part+ (y - e) = 0`.
- **`TruncRieszSpace.IsBoolean_meet`**: Boolean elements are closed under `∧`.
- **`TruncRieszSpace.IsBoolean_join`**: Boolean elements are closed under `∨`.
- **`TruncRieszSpace.IsBoolean_diff`**: For Boolean `a`, `b`, the element `part+ (a - b)` is Boolean.
- **`TruncRieszSpace.IsBoolean_abs`**: For Boolean `a`, `b`, the element `abs (a - b)` is Boolean.
- **`TruncRieszSpace.IsBoolean_diff-disj`**: For Boolean `a`, `b`, `part+ (a - b)` is disjoint from `a ∧ b`.

#### The Boolean Ring

- **`TruncRieszSpace.BoolRing`**: The `BooleanRing` structure on the type of Boolean elements: sum is `abs (a - b)` (symmetric difference), product is `∧`, zero is `0`. Its lemmas `abs-formula`, `meet-split`, `meet-formula`, `meet-swap` and `join-char` establish the ring laws and show that the ring join is the lattice join `∨`.

#### Homomorphisms

- **`TruncRieszSpaceHom`**: Record of homomorphisms between truncated Riesz spaces: Riesz space homomorphisms that commute with `trunc`. Comes with the identity `TruncRieszSpaceHom.id` and composition `TruncRieszSpaceHom.compose` (`∘`).
