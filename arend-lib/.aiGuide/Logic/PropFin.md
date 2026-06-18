### Logic.PropFin

A diagonal-style argument showing that `\Prop` cannot have an injection from `\Prop -> \Prop` into itself, packaged as the `InjData` class.

The module formalizes a propositional version of Cantor's theorem: any injection `f : \Prop -> \Prop` would force `f (f A) = A`, collapsing the universe of propositions in a way that makes every proposition equivalent to the unit type `\Sigma`. The `InjData` class bundles a candidate injection `f` together with its injectivity witness, and exposes lemmas demonstrating that under such an assumption, propositions become trivial. This is useful as a building block for impredicativity arguments and for proving that `\Prop` is not a small type.

#### Class

- **`InjData`**: A class parameterized by a function `f : \Prop -> \Prop` together with a proof `inj` that `f` is injective on propositions. Captures the hypothesis used in the diagonal argument.

#### Diagonal Lemmas

- **`PropFin`**: `f (f A) = A` for any proposition `A`. The core fixed-point/diagonalization consequence of `f` being injective.
- **`aux`**: Given a witness `h : f P`, derives `f (\Sigma) = P`. An auxiliary identification used to extract a proposition from membership in the image of `f`.

#### Propositional Triviality

- **`true=>equals`**: Any inhabited proposition `P` is equal to `\Sigma`: from `h : P` produces `P = \Sigma`. Shows that under the `InjData` assumption, all true propositions collapse to the unit.
- **`equals=>true`**: Converse direction: from `P = \Sigma` produces an inhabitant of `P`. Together with `true=>equals` this characterizes truth as equality with `\Sigma`.
