### Logic.Rewriting.TRS.Union.Colors

Color-indexed structure for unions of term rewriting systems sharing a common sort signature.

This module sets up the framework for combining multiple TRSs by tagging each system with a "color" drawn from a decidable set. A `TheoremContext` bundles per-color signatures and linear rule registries, and the union signature is built via `SumFSignature` / `SumRegistry` so that every function symbol in the combined system carries its originating color. Predicates like `HasColoredRoot` and `HasExcludedColoredRoot` track which subsystem a term's head belongs to, which is essential for stating and proving modular theorems (e.g. confluence by colored decompositions). Conversion lemmas show how linear terms from a single colored signature embed coherently into the unified term language.

#### TheoremContext

- **`TheoremContext`**: Class parameterizing a TRS union by a decidable set of colors `Color`, a shared sort set `Sort'`, per-color signatures `envs : Color -> FSignature Sort'`, and per-color linear rule registries `rules`.
- **`env`**: The combined `FSignature` instance, defined as `SumFSignature envs`, whose function symbols are dependent pairs of a color and a symbol from that color's signature.
- **`monochrome-reduction`**: One-step rewrite relation `A B : Term (envs c) ...` confined to a single color `c`, given by `RewriteRelation {envs c} (rules c)`.
- **`JRegistry`**: The combined `RuleRegistry` over `env`, assembled as `SumRegistry envs rules`.
- **`get-rule-pattern`**: Retrieves the linear left-hand-side pattern of rule `idx` in color `c`.
- **`rule-linear-mc`**: The `MetaContext` induced by the linear pattern of a rule, used to type its right-hand side.
- **`rule-right-linear`**: The right-hand side of rule `idx` in color `c`, as a term over `rule-linear-mc idx`.

#### Color of the Root Symbol

- **`HasColoredRoot`**: Inductive predicate asserting that a term's head function symbol has color `color`; introduced by `func-root` requiring `f.1 = color` for `func f _`.
- **`HasColoredRoot.equalize-colors`**: If two terms with the same head symbol both have colored roots `color` and `color'`, then `color = color'`.
- **`HasColoredRoot.reorganize`**: Extracts the underlying equality `f.1 = color` from a `HasColoredRoot` proof on `func f arguments`.
- **`HasExcludedColoredRoot`**: Dual predicate stating that a term's root symbol does not have color `color`; introduced by `ex-func-root` with `Not (f.1 = color)`.

#### Injection of Monochrome Linear Terms

- **`convert-to-injected-term`**: Converts a `GenericLinearTerm` over a single color's signature `envs color` into a `Term` over the union signature `env`, paired with the appropriate `LinearMetaContext`. Handles function applications (tagging the head with `color`), full metavariables, and variables.
- **`unwrap-injection`**: Coherence lemma showing that injecting a converted linear term equals directly converting it to an injected term: `inject-term envs (Linear.convert-to-term t) = convert-to-injected-term color t`.
- **`unwrap-injection.swap`**: Auxiliary lemma showing that `inject-term` commutes with `ModularMetaContext.upgrade-metavariables`, allowing metacontext upgrades to be performed before or after injection into the union signature.
