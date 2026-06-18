### Logic.Rewriting.TRS.Union

Constructs the disjoint union (sum) of a family of term rewriting systems sharing a common sort set.

This module formalizes how to combine an indexed family of functional signatures `S : J -> FSignature Sort` into a single signature `SumFSignature S` whose function symbols are tagged pairs `(j, f)` identifying their originating component. Terms, rewrite rules, and rule registries from any individual component can be injected into the union by relabeling each function application with its component index, while variables and metavariables pass through unchanged. This provides the standard set-theoretic basis for studying modular properties of TRSs (such as confluence preservation under disjoint union).

#### Sum Signature

- **`SumFSignature`**: Given `S : J -> FSignature Sort`, builds the union signature whose symbols at sort `s` are dependent pairs `(j : J, symbol {S j} s)` and whose domain is inherited from the selected component.

#### Term Injection

- **`inject-term`**: Maps a term `Term (S j) c s mc` from component `j` into `Term (SumFSignature S) c s mc` by tagging every `func` node with its index `j`; recursive on `var`, `metavar`, and `func` constructors.
- **`inject-term.over-transport-sort`**: Naturality of `inject-term` with respect to `transport` along a sort equality `eq : s = s'` — injection commutes with sort transport.
- **`inject-term.over-transport-ctx`**: Naturality of `inject-term` with respect to `transport` along a context equality `eq : c = c'` — injection commutes with context transport.

#### Rule and Registry Injection

- **`inject-rule-container`**: Lifts a `RewriteRule` over component signature `S j` to a `RewriteRule` over `SumFSignature S` by injecting both the left- and right-hand sides; preserves the metacontext and the root-is-function-symbol condition on the LHS.
- **`SumRegistry`**: Given a family of rule registries `L : \Pi (j : J) -> RuleRegistry (S j)`, produces the combined `RuleRegistry (SumFSignature S)` whose rule indices at sort `s` are pairs `(j, rule-J {L j} s)` and whose rule containers come from the corresponding injected component rules.
