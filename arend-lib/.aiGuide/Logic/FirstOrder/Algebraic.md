### Logic.FirstOrder.Algebraic

Algebraic (equational/relational) first-order theories, structures, and models.

- **`Signature`**: Extends `TermSig` with `PredSymb` and `predDomain`.
- **`Formula`**: `equality` and `predicate` constructors over a signature.
- **`substF`**: Substitution on formulas.
- **`Sequent`**: A sequent `(V, FinSet, premises, conclusion)`.
- **`Theory`**: Extends `Signature` with `axioms : Sequent -> \Prop`.
- **`Structure`**: Carrier sets `E`, `operation`, and `relation` interpreting a signature.
- **`Model`**: Extends `Structure` with `isModel` (axiom satisfaction).
- **`TermModel`**: The term model (initial model) of a theory.
