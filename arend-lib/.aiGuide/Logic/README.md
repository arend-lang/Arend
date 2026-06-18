### Logic

This directory formalizes foundational logic for the library: classical axioms, h-level truncation predicates, equivalence-of-propositions tooling, a Cantor-style diagonal argument, and infrastructure for first-order theories and rewriting systems.

#### H-Levels and Uniqueness

- **`Unique.md`** — Propositions, sets, and contractible types as the basic homotopy h-levels, with closure properties and conversions between them.

#### Classical Axioms

- **`Classical.md`** — Postulates the law of excluded middle and the axiom of choice, with a `Choice` class and Diaconescu's derivation of LEM from choice.

#### Propositional Combinatorics

- **`TFAE.md`** — The "the following are equivalent" predicate over an array of propositions, with cycle and reflective graph-connectivity strategies for discharging proofs.
- **`PropFin.md`** — Cantor-style diagonal argument showing that an injection `\Prop -> \Prop` collapses every true proposition to the unit type.

#### Subdirectories

- **`FirstOrder/`** — First-order logic infrastructure: terms, algebraic theories, and their categories.
- **`Rewriting/`** — Abstract reduction systems and term rewriting systems, covering confluence, termination, substitutions, and higher-order rewriting.
