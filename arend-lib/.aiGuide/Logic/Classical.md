### Logic.Classical

Classical logic axioms: the law of excluded middle and the axiom of choice.

This module postulates the two classical axioms that go beyond Arend's constructive base. `alem` asserts decidability of every proposition (excluded middle), while `achoice` provides global choice over arbitrary sets. By Diaconescu's theorem, choice implies excluded middle — this is realized concretely by `lemFromChoice`.

#### Axioms

- **`alem`**: Law of excluded middle: every proposition `P : \Prop` is decidable, `Dec P`.
- **`achoice`**: Axiom of choice: for `B : A -> \Set` over a set `A`, `(\Pi x -> TruncP (B x)) -> TruncP (\Pi x -> B x)` — pointwise inhabitation lifts to a global section.

#### Derived Results

- **`achoice.lemFromChoice`**: Diaconescu's theorem — derives the law of excluded middle `Dec P` for any proposition `P` from the global axiom of choice `achoice`.
