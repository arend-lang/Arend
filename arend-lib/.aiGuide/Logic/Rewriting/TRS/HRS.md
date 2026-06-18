### Logic.Rewriting.TRS.HRS

Higher-order term rewriting systems (HRS): typed terms with metavariables and binders, rewrite rules over an arbitrary signature, and the induced reduction relation as an abstract reduction system.

This module formalizes a many-sorted higher-order rewriting framework parameterized by an `FSignature` (sorts, function symbols, and their domains carrying local context extensions) and a `MetaContext` (metavariables with sort-indexed domains). Terms are an inductive family over a typing context and target sort, supporting variables, metavariables applied to argument terms, and function symbols whose arguments live in extended contexts (modeling binders). Two layers of substitution — ordinary `Substitution` for variables and `MetaSubstitution` for metavariables (which performs the inner variable substitution at meta-instantiation sites) — drive the rewrite mechanism. Rewrite rules are packaged in a `RuleRegistry`; the induced `RewriteRelation` allows top-level rule application as well as congruence steps under function symbols and metavariables, and is finally lifted to an `AbstractReductionSystem` instance.

#### Signatures and Meta-Contexts

- **`FSignature`**: Class describing a many-sorted signature: a `Sort` set, sort-indexed `symbol` families, and a `domain` per symbol giving each argument's local context extension (a list of sorts) together with its sort. Provides helpers `!!domain`, `!!sort`, `arity`, and `index-in`.
- **`MetaContext`**: Record of metavariable declarations over a fixed `Sort`: each metavariable name has a sort and a list of argument sorts (`m-domain`). Exposes `arity` and `index-in`.

#### Terms

- **`Term`**: Inductive family `Term env context termSort mc` of well-typed terms with three constructors:
  - **`var`**: Bound variable referring to a position in `context` whose sort matches `termSort`.
  - **`metavar`**: A metavariable `m` applied to a `DArray` of argument terms in the same context, with sorts taken from `mc.m-domain m`.
  - **`func`**: A function symbol `f` applied to arguments where each argument lives in `context ++ f !!domain index` (modeling binders introduced by the symbol).
- **`Term.fext`**: Function-argument extensionality: pointwise equal argument arrays give equal `func` terms.
- **`Term.mext`**: Metavariable-argument extensionality, analogous to `fext`.

#### Variable Substitution

- **`Substitution`**: Type of context-changing substitutions: maps each index of `old-context` to a term of the corresponding sort over `new-context`.
- **`Substitution.apply`**: Recursive application of a substitution to a term, threading the substitution under binders via `append-context-right`.
- **`Substitution.over-transport-sort`**: Naturality of `Substitution.apply` with respect to transport along an equality of sorts.

#### Function Roots

- **`FunctionRoot`**: Propositional predicate asserting that a term has a function symbol at its root (rules that violate this are excluded — only `func _ _` introduces `T-has-functional-root`).

#### Meta-Substitution

- **`MetaSubstitution`**: Maps each metavariable of `old-metacontext` (sort `s`) to a term over the extended context `new-context ++ m-domain mvar` and `new-metacontext`. This captures higher-order substitution: a metavariable's instance is parameterized by its argument values.
- **`MetaSubstitution.apply`**: Applies a meta-substitution to a term, tracking which subset of the current context is the "core" (`SubList`); at each `metavar` occurrence, recursively meta-substitutes the arguments and then plugs them into the metavariable's body via `Substitution.apply` and `extend-substitution-left`.
- **`MetaSubstitution.over-transport-sort`**: Naturality of `MetaSubstitution.apply` under sort transport.

#### Rewrite Rules

- **`RewriteRule`**: Record of a single rule at sort `s`: its own metacontext `rr-mc`, left- and right-hand sides `rr-l`, `rr-r` as closed terms (empty context), and a proof `rr-l-func-root` that the LHS has a function root.
- **`RuleRegistry`**: Record indexing a family of rewrite rules by sort: `rule-J s` enumerates rules of sort `s`, with accessors `rule-mc`, `rule-l`, `rule-r`, and the rule's LHS function-root lemma `rule-func-root`.

#### Argument Reindexing under Symbol Equalities

- **`arguments-over-f`**: Transports an argument array along an equality `f-A = f-B` of function symbols, producing arguments indexed by `f-A`'s domain from those indexed by `f-B`'s.
- **`arguments-over-m`**: Same for metavariable equality `m-A = m-B`.

#### Rewrite Relation

- **`RewriteRelation`**: One-step rewrite between terms `A` and `B` in the same context and sort, with three cases:
  - **`rewrite-with-rule`**: Top-level application of a rule `idx` with a meta-substitution that takes the (weakened, closed) LHS to `A` and the RHS to `B`.
  - **`rewrite-with-parameter-f`**: Congruence under a function symbol — given `f-A = f-B`, rewriting at one argument index `i` while all other arguments coincide (modulo the symbol-equality transport).
  - **`rewrite-with-parameter-m`**: Congruence under a metavariable, analogous to the function case.

#### Abstract Reduction System Instances

- **`HigherOrderTermRewritingSystem`**: Class extending `AbstractReductionSystem` with `env`, `meta-context`, and `set-of-rules`. Its carrier `A` is the sigma of `(context, sort, term)` triples; the reduction `~>` exists between two such triples when, after transporting `B` along context/sort equalities, there is a `RewriteRelation` step from `A` to it.
- **`SimpleHigherOrderTermRewritingSystem`**: Specialization extending both `HigherOrderTermRewritingSystem` and `SimpleARS`, restricting the framework to the simple-ARS setting (e.g. propositional reduction).
