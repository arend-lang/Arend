### Algebra.Ring.Boolean

Boolean rings: commutative rings in which every element is idempotent, equivalently Boolean algebras viewed multiplicatively.

A Boolean ring satisfies `x * x = x` for all `x`, which forces commutativity, characteristic 2 (`x + x = 0`), and `negative x = x`. The module exposes the canonical equivalence between Boolean rings and Boolean algebras: multiplication acts as meet, the formula `x + y + x*y` acts as join, the partial order is `x <= y ⇔ x*y = x`, and the zero is bottom. Adding a unit `1` extends this to the equivalence with Boolean algebras with complement `compl x = x + 1`. A symmetric difference operation `diff x y = x + x*y` is provided as a counterpart of `x ∧ ¬y`, with the lattice-theoretic lemmas needed to reason about it.

#### Main Classes

- **`BooleanRing`**: A pseudo-commutative ring with `isBooleanRing : x * x = x`, simultaneously a `BottomDistributiveLattice`. Lattice structure is induced: `meet = *`, `join x y = x + y + x*y`, `bottom = 0`, and `<=` is defined via `x * y = x`. Commutativity `*-comm` is derived from idempotence.
- **`UnitalBooleanRing`**: Boolean ring with unit, extending `CRing` and `BooleanAlgebra`. Provides `top = 1` and complement `compl a = a + 1`.

#### Order and Lattice Structure

- **`BooleanRing.<=`**: The canonical order on a semigroup with `x*x=x`, defined as `x * y = x`.
- **`isBooleanRing`**: The defining axiom `x * x = x`.

#### Basic Arithmetic Lemmas

- **`double=0`**: `x + x = 0` — every Boolean ring has characteristic 2.
- **`negative=id`**: `negative x = x` — additive inverse is the identity.
- **`sum2`** (in `\where`): General lemma that idempotence in any pseudo-ring forces `x + x = 0`.
- **`+_join`**: If `x * y = 0` then `x + y = x ∨ y`; addition coincides with join on disjoint elements.

#### Symmetric Difference (`diff`)

- **`diff`**: `diff x y = x + x * y`, the Boolean-ring counterpart of `x ∧ ¬y`.
- **`diff_<=`**: `diff x y <= x`.
- **`diff_*`**: `diff x y * y = 0` — `diff x y` is disjoint from `y`.
- **`diff-univ`**: Universal property: if `x <= y` and `x * z = 0` then `x <= diff y z`.
- **`diff-mono`**: Monotonicity: `diff` is monotone in the first argument and antitone in the second.
- **`diff_*_diff`**: `diff x y * diff y x = 0` — the two asymmetric differences are disjoint.
- **`+_diff`**: `x + y = diff x y ∨ diff y x` — addition expressed as the join of asymmetric differences.
- **`split_<=`**: For `y <= x`, decomposes `x = diff x y ∨ y`.
- **`split`**: General decomposition `x = diff x y ∨ x * y`.
- **`diff-trans`**: Triangle-like inequality `diff x z <= diff x y ∨ diff y z`.
- **`diff_BigJoin`**: `diff` distributes over indexed joins: `diff (BigJoin l) x = BigJoin (j => diff (l j) x)`.

#### Boolean Algebra Correspondence

- **`UnitalBooleanRing.fromBooleanAlgebra`**: Coercion turning any `BooleanAlgebra R` into a `UnitalBooleanRing` with `zro = bottom`, `+` as symmetric difference `a ∧ ¬b ∨ b ∧ ¬a`, `*` as meet, `negative = id`, and `ide = top`.
- **`HeytingBooleanRing`**: Instance producing a `UnitalBooleanRing` on the negated elements `NegatedElem R` of any Heyting algebra `R`, via the Boolean algebra of regular elements.
