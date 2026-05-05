### Logic Directory Overview

This directory provides classical logic principles, propositional utilities, first-order logic, and rewriting theory.

#### Core Logic

- **`Classical.md`**: Classical logic — `lem` (law of excluded middle), `Choice` class (axiom of choice for sets), `choice`, and `lemFromChoice`.
- **`Unique.md`**: Propositionality, contractibility, and set-level truncation — `isProp`, `isSet`, `Contr`, conversions between them (`isContr=>isProp`, `isProp=>isContr`, etc.), and closure under `\Pi`/`\Sigma` (`pi-isProp`, `pi-Contr`, `sigma-Contr`).
- **`PropFin.md`**: Injective data for propositions — `InjData` class with an injective endofunction `f : \Prop -> \Prop`.
- **`TFAE.md`**: "The following are equivalent" automation — `TFAE` mutual equivalence type, `proof`/`proof'` (from directed implications via graph connectivity), and `cycle`/`cycle'` (from cyclic implication chains).

#### First-Order Logic

- **`FirstOrder/Term.md`**: Multi-sorted first-order terms — `TermSig` (signatures with sorts and symbols), `Term` inductive type (`var`/`apply`), `subst` (substitution), `subst-assoc`, `subst_var`.
- **`FirstOrder/Algebraic.md`**: Algebraic first-order theories — `Signature` (extends `TermSig` with predicates), `Formula`, `Sequent`, `Theory`, `Structure`, `Model`, and `TermModel` (initial model).
- **`FirstOrder/Algebraic/`**: Subdirectory contains `Category.md` — `ModelHom` (model homomorphisms), `ModelCat` (bicomplete category of models with univalence via SIP).

#### Rewriting

- **`Rewriting/ARS/`**: Abstract reduction systems.
  - **`Relation.md`**: Relation utilities — `Rel` type alias, `StraightJoin`, `Closure` (with `compose` and `lift`).
  - **`AbstractReductionSystem.md`**: `AbstractReductionSystem` class (carrier, reduction `~>`, convertibility `|--|`), `SimpleARS`, and closure operators (`~>_0`, `~>_1`, `~>_=`, `~>_+`, `~>_*`).
  - **`Termination.md`**: Normal forms and termination — `isNormalForm`, `HasNormalForm`, `isNormalizing`, `Acc` (accessibility/well-founded induction), `Acc-Trans`, `Acc=>AccTrans`.
  - **`Confluence.md`**: Confluence properties — `StrongJoin`, `isStronglyConfluent`, `isChurchRosser`, `isConfluent`, `isLocallyConfluent`, normal form uniqueness properties, Church-Rosser/confluence equivalence, and `Newman`'s lemma.

- **`Rewriting/TRS/`**: Term rewriting systems (higher-order).
  - **`HRS.md`**: Higher-order rewriting — `FSignature`, `MetaContext`, `Term` (with `var`/`metavar`/`func`), `Substitution`, `MetaSubstitution`, `RewriteRule`, `RuleRegistry`, and `RewriteRelation` (one-step rewrite as `SimpleARS`).
  - **`Substitutions.md`**: Substitution operations — `plain-identity`, `plain-identity-effect`, `weakening`, `Substitution.apply`, `MetaSubstitution.apply`.
  - **`MetaContexts.md`**: Meta-context combinators — `ModularMetaContext` (disjoint union with `upgrade-metavariables`), `PointedModularMetaContext`, `EmptyMetaContext`.
  - **`Linearity.md`**: Linear terms — `GenericLinearTerm` (each meta-variable occurs at most once), `Linear.convert-to-term`, `LinearMetaContext`.
  - **`Union.md`**: Union of TRSs — `SumFSignature`, `inject-term`, `inject-rule-container`, `SumRegistry`.
  - **`Union/`**: Subdirectory contains `Colors.md` (colored/indexed TRS setup with `TheoremContext`) and `Confluence.md` (modular confluence theorem for disjoint TRS unions).
