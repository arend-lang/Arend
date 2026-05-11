### Order.BooleanAlgebra

Boolean algebras as bounded distributive lattices with complementation, plus the construction of a Boolean algebra from the negated elements of any Heyting algebra.

A Boolean algebra is presented here as a `BoundedDistributiveLattice` together with a complement operation `compl` satisfying the two characteristic laws `a ∧ ¬a ≤ ⊥` and `⊤ ≤ a ∨ ¬a`. The class also extends `HeytingAlgebra` by deriving the implication `a → b` as `¬a ∨ b`, so every Boolean algebra is automatically Heyting. The double-negation construction `HeytingBooleanAlgebra` shows that the regular (i.e. doubly-negated) elements of any Heyting algebra form a Boolean algebra: meets are inherited, joins are obtained by double-negating the underlying join, and complement is `neg`.

#### Boolean Algebra Class

- **`BooleanAlgebra`**: Class extending `BoundedDistributiveLattice` and `HeytingAlebra`. Adds a complement `compl : E -> E` with axioms `compl-meet : a ∧ compl a <= bottom` and `compl-join : top <= a ∨ compl a`. Implication is defined as `compl a ∨ b`, with `exponent-left`/`exponent-right` derived from distributivity and the complement laws.

#### Complement Lemmas

- **`compl-adj`**: Adjunction characterization: `a ∧ b <= bottom -> a <= compl b`.
- **`compl-mono`**: Complement is order-reversing: `a <= b -> compl b <= compl a`.
- **`compl_meet`**: De Morgan law: `compl (a ∧ b) = compl a ∨ compl b`.
- **`compl_join`**: De Morgan law: `compl (a ∨ b) = compl a ∧ compl b`.
- **`compl-inv`**: Double complement is identity: `compl (compl a) = a`.

#### Symmetric Difference

- **`symm-diff`**: Symmetric difference: `a ∧ compl b ∨ b ∧ compl a`.
- **`compl_symm-diff`**: Complement of symmetric difference: `compl (symm-diff a b) = a ∧ b ∨ compl a ∧ compl b`, i.e. the biconditional.

#### Boolean Algebra of Negated Elements

- **`NegatedElem`**: The subtype of a Heyting algebra `R` consisting of elements satisfying `IsNegated` (i.e. regular / doubly-negated elements).
- **`HeytingBooleanAlgebra`**: Instance making `NegatedElem R` into a `BooleanAlgebra` for any Heyting algebra `R`. Order is inherited; meet is the underlying meet (closed under negated-ness); join is `neg (neg (a ∨ b))`; top and bottom come from `R`; complement is `neg`. This realizes the standard fact that the regular elements of a Heyting algebra form a Boolean algebra.
