### Algebra.Ring.Boolean

Boolean rings — pseudo-commutative rings in which every element is idempotent under multiplication — and their equivalence with Boolean algebras.

#### Boolean Ring Structure

- **`BooleanRing`**: Class extending `PseudoCRing` and `BottomDistributiveLattice`. A pseudo-commutative ring satisfying `x * x = x` for all `x`. The lattice structure is induced: `meet = *`, `join x y = x + y + x*y`, `bottom = 0`, and `x <= y` iff `x * y = x`. Commutativity of `*` and the distributivity of `*` over `meet` are derived from idempotence.
- **`BooleanRing.isBooleanRing`**: The defining axiom: `x * x = x`.
- **`BooleanRing.<=`**: The induced partial order on a semigroup, defined as `x <= y => x * y = x`.
- **`BooleanRing.sum2`**: Lemma showing that in any pseudo-ring with idempotent multiplication, `x + x = 0` (every element is its own additive inverse, characteristic 2).

#### Unital Boolean Rings and Boolean Algebras

- **`UnitalBooleanRing`**: Class extending `BooleanRing`, `CRing`, and `BooleanAlgebra`. A Boolean ring with a multiplicative identity, simultaneously presented as a Boolean algebra with `top = 1` and complement `compl a = a + 1`.
- **`UnitalBooleanRing.fromBooleanAlgebra`**: Coercion converting any `BooleanAlgebra` `R` into a `UnitalBooleanRing` with `zro = bottom`, addition as symmetric difference `a ∧ ¬b ∨ b ∧ ¬a`, multiplication as meet `∧`, `ide = top`, and `negative = id`. Establishes the standard equivalence between Boolean algebras and unital Boolean rings.

#### Heyting Algebra Construction

- **`HeytingBooleanRing`**: Instance producing a `UnitalBooleanRing` on the negated elements `NegatedElem R` of a Heyting algebra `R`, by combining `HeytingBooleanAlgebra` with `fromBooleanAlgebra`.
