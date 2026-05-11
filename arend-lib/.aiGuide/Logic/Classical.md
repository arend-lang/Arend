### Logic.Classical

Classical logic axioms: the law of excluded middle and the axiom of choice.

This module postulates the two classical axioms that go beyond Arend's constructive base. `alem` asserts decidability of every proposition (excluded middle), while `achoice` provides global choice over arbitrary sets. The `Choice` class packages a local form of choice for a given base set, supporting derived surjection-lifting lemmas. By Diaconescu's theorem, choice implies excluded middle — this is realized concretely by `lemFromChoice`.

#### Axioms

- **`alem`**: Law of excluded middle: every proposition `P : \Prop` is decidable, `Dec P`.
- **`achoice`**: Axiom of choice: for `B : A -> \Set` over a set `A`, `(\Pi x -> TruncP (B x)) -> TruncP (\Pi x -> B x)` — pointwise inhabitation lifts to a global section.

#### Choice Class

- **`Choice`**: Extends `BaseSet`. A set `E` equipped with a choice operator `choice` selecting a global section from a family of pointwise-inhabited sets indexed by `E`.
  - **`choice`**: For `B : E -> \Set`, turns `\Pi x -> TruncP (B x)` into `TruncP (\Pi x -> B x)`.
  - **`liftDepSurj`**: Given a fiberwise surjection `f : A x -> B x` between a family of sets and a family of 1-types, any global section `g : \Pi x -> B x` admits a lift `g'` with `f (g' x) = g x` (truncated existence).
  - **`liftSurj`**: Non-dependent version: a surjection `f : A -> B` from a set to a 1-type can be post-composed inverted to lift any `g : E -> B` to `g' : E -> A`.

#### Derived Results

- **`achoice.lemFromChoice`**: Diaconescu's theorem — derives the law of excluded middle `Dec P` for any proposition `P` from the global axiom of choice `achoice`.
