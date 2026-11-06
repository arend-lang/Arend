### Logic.Rewriting.TRS.Linearity

Linear terms, patterns, and rewrite rules for higher-order term rewriting systems.

This module formalizes *linear* syntactic objects in a higher-order rewriting setting: terms in which each metavariable position appears at most once and is fully applied to the local variable context. Linearity is enforced structurally via the `GenericLinearTerm` datatype, which is parameterized by a proposition controlling whether variable occurrences are allowed — yielding `LinearTerm` (variables permitted) and `LinearPattern` (no variables, suitable as left-hand sides of rules). The metacontext for a linear term is computed inductively to match its shape, so each metavariable hole gets its own slot in a `ModularMetaContext`, allowing rewrite rules to be presented compactly without manual metacontext bookkeeping.

#### Linear Term Datatype

- **`GenericLinearTerm`**: Inductive datatype of linear higher-order terms over signature `env`, in `context : List Sort` at sort `termSort`, parameterized by a proposition `allow-variables` toggling whether `l-var` constructors are permitted.
  - **`l-func`**: Function symbol applied to linear subterm arguments, each in the extended context `context ++ (f !!domain index)`.
  - **`l-full-metavar`**: A metavariable hole applied to the identity substitution on the ambient context (i.e. fully linear use).
  - **`l-var`**: Variable reference at a context index (only available when `allow-variables` holds).

#### Specializations

- **`LinearTerm`**: `GenericLinearTerm` with `allow-variables = \Sigma` (unit) — variables are permitted.
- **`LinearPattern`**: `GenericLinearTerm` with `allow-variables = Empty` — no variables, used as rewrite-rule left-hand sides.
- **`lt-var`**: Smart constructor for variable references in `LinearTerm`.

#### Metacontext Computation

- **`LinearMetaContext`**: Computes the canonical `MetaContext Sort` induced by a linear term — `EmptyMetaContext` for variables, `SingularMetaContext s context` for a hole, and `ModularMetaContext` recursively combining children's metacontexts for function applications.

#### Conversion to General Terms

- **`Linear.convert-to-term`**: Embeds a `GenericLinearTerm` into the general `Term` type using its computed `LinearMetaContext`, lifting subterm metavariables through `ModularMetaContext.upgrade-metavariables` so each child's local metacontext is reindexed into the combined parent metacontext.

#### Substitution Lemmas

- **`modular-commutation`**: States that applying a metasubstitution `rho : MetaSubstitution env subcontext (ModularMetaContext sigs) msig` after upgrading a term from component `sigs i` agrees with applying the restricted substitution `\lam m => rho (i, m)` directly — i.e. modular metacontext upgrading commutes with substitution.
- **`invariant-through-empty-subst`**: When the source context is empty (`nil`), the choice of `SubList nil (context ++ context')` is irrelevant: any two sublist witnesses produce the same result of `MetaSubstitution.apply`.
  - **`invariant-through-empty-subst.lemma`**: Auxiliary form proving the same equality from explicit equality of the sublist witnesses.

#### Linear Rewrite Rules

- **`LinearRewriteRule`**: Extends `RewriteRule` by specifying the left-hand side as a `LinearPattern`; the rule's metacontext `rr-mc` and term `rr-l` are derived automatically via `LinearMetaContext` and `Linear.convert-to-term` respectively.
  - **`rr-pattern`**: The `LinearPattern env nil s` serving as the rule's left-hand side.
- **`LinearRegistry`**: Extends `RuleRegistry`, overriding `rule-container` to require every rule be a `LinearRewriteRule`.
  - **`rule-pattern`**: Convenience accessor returning the `LinearPattern` of a registered rule by id.
