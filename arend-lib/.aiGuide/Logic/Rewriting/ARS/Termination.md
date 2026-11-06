### Logic.Rewriting.ARS.Termination

Notions of normal forms, accessibility, well-founded induction, and termination for abstract reduction systems.

This module formalizes when a reduction system terminates, building on the accessibility predicate from `Logic.Accessible`: an element is accessible when everything related to it by `R b a` is accessible. Termination is defined as accessibility of every element with respect to the reversed one-step reduction (`\lam x y => y ~> x`), while normalization is the existence of a normal form for every element. The module relates these notions (termination implies normalization under decidability of reducibility), generalizes accessibility to the transitive closure, and exposes well-founded induction principles for proving properties along reduction chains.

#### Normal Forms

- **`isNormalForm`**: Predicate stating that `a` admits no one-step reduction: `∀ {b}, ¬ (a ~> b)`.
- **`HasNormalForm`**: Inductive proposition asserting `a` reduces (in `~>_*`) to some normal form, via constructor `normal-form`.
- **`isNormalizing`**: An ARS is normalizing when every element has a normal form.

#### Accessibility and Termination

- **`Acc-Trans`**: Accessibility under the transitive closure of `R`, defined as accessibility of `a` with respect to `TransClosure R`.
- **`Acc=>AccTrans`**: Lemma upgrading ordinary accessibility to accessibility under the transitive closure.
- **`isTerminating`**: An ARS is terminating when every element is accessible with respect to the reversed one-step reduction, i.e. `a` is accessible for `\lam x y => y ~> x`.

#### Termination vs. Normalization

- **`Termination=>Normalization`**: If reducibility `∃ a', a ~> a'` is decidable at every point, then accessibility (with respect to the reversed `~>`) implies the existence of a normal form. Bridges the constructive gap between termination and normalization.

#### Well-Founded Induction

- **`well-founded-induction`**: Induction principle on accessible elements: to prove `C a`, it suffices to prove `C b` for every `b` with `R b a`. Recurses structurally on the accessibility proof.
- **`well-founded-induction-transitive`**: Variant taking an induction step quantified over the transitive closure (`TransClosure R b a`), for elements in `Acc-Trans`.

#### Reduction Paths

- **`one-step-reduction`**: The set of one-step successors of `a`, i.e. `Σ (b : A), a ~> b`.
- **`ReductionPath`**: Inductive type of reduction sequences starting at `a`, built by `branch` (extend by a one-step reduction) or terminated by `leaf` at a normal form.
