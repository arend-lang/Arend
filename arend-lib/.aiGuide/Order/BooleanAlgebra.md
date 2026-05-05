### Order.BooleanAlgebra

Boolean algebras as bounded distributive lattices with a complement operation, plus the construction exhibiting negated elements of a Heyting algebra as a Boolean algebra.

#### Boolean Algebra Class

- **`BooleanAlgebra`**: Extends `BoundedDistributiveLattice` and `HeytingAlebra`. Adds a complement operation `compl : E -> E` satisfying `a ∧ compl a <= bottom` (`compl-meet`) and `top <= a ∨ compl a` (`compl-join`). Implication is derived as `implies a b := compl a ∨ b`, with `exponent-left` and `exponent-right` proven from the complement laws via distributivity.

#### Negated Elements

- **`NegatedElem`**: `\type` of elements `a : R` of a Heyting algebra together with a proof `R.IsNegated a` that `a` is in the image of negation (equivalently, double-negation stable).

#### Boolean Algebra of Negated Elements

- **`HeytingBooleanAlgebra`**: For any Heyting algebra `R`, exhibits `NegatedElem R` as a `BooleanAlgebra`.
  - **Order**: Inherited from `R` on the underlying elements.
  - **Meet**: Componentwise via `meet-negated` (negated elements are closed under meet).
  - **Join**: Defined as `neg (neg (a ∨ b))` (double-negation closure) since negated elements are not generally closed under join; uses `id<=neg_neg` for the inclusions.
  - **Top/Bottom**: `R.top` (with `top-univ` witnessing negation) and `R.bottom` (via `bottom-negated`).
  - **Distributivity (`ldistr>=`)**: Proven by rewriting joins through `neg_join` and applying `exponent-left`, `eval`, and `modus-ponens` in the underlying Heyting algebra.
  - **Complement**: `compl a := neg a.1`, with `compl-meet` from `modus-ponens` and `compl-join` from a chain through `neg_join` and `modus-ponens`.
