### Logic.Rewriting.TRS.Examples.LambdaCalculus

A worked example formalizing untyped λ-calculus as a higher-order term rewriting system with β-reduction as its sole rewrite rule.

This module instantiates the generic HRS framework from `Logic.Rewriting.TRS.HRS` to encode the untyped λ-calculus. It uses a single sort `lc-term` and two function symbols, `abstraction` (binding one variable) and `application` (binary, no binding), with β-reduction expressed as a rewrite rule using metavariables to capture the body and argument. The example demonstrates the full workflow: signature declaration, meta-context for rule schemas, registering rules into the system, and exhibiting a concrete reduction step `(λx.x) (λx.x) → λx.x`.

#### Signature

- **`SingularSort`**: One-element sort type with constructor `lc-term`, since untyped λ-calculus is single-sorted.
- **`LC-Symbols`**: The two function symbols of the calculus: `abstraction` and `application`.
- **`LC-FSignature`**: `FSignature` instance over `SingularSort`. `abstraction` takes one argument that binds one `lc-term` variable; `application` takes two non-binding `lc-term` arguments.

#### β-Reduction Rule

- **`MetaVariables`**: Two metavariables `metavar-a` (binding one variable, representing the abstraction body) and `metavar-b` (no binding, representing the argument).
- **`BetaRedexContext`**: `MetaContext` mapping `metavar-a` to arity `[lc-term]` and `metavar-b` to arity `[]`.
- **`BetaRedex`**: The redex pattern `(λx. a[x]) b` as a `Term` over `LC-FSignature` in `BetaRedexContext`.
- **`BetaResult`**: The contractum `a[b]`, obtained by substituting `metavar-b` into `metavar-a`.
- **`beta-reduction`**: `RewriteRule lc-term` packaging `BetaRedex ⇒ BetaResult` with `BetaRedexContext`.
- **`LC-rules`**: `RuleRegistry` for `LC-FSignature` indexing the single rule `beta-reduction` by the unit type at sort `lc-term`.

#### The Rewriting System

- **`UntypedLambdaCalculus`**: `SimpleHigherOrderTermRewritingSystem` instance combining `LC-FSignature`, `LC-rules`, and an empty meta-context for closed-term rewriting.

#### Example Terms and Reduction

- **`K`**: The combinator `λx.λy.x` as a pure (closed) term.
- **`I`**: The identity combinator `λx.x` as a pure term.
- **`I_I`**: The application `I I` as a `PureTerm`.
- **`suitable-substitution`**: `MetaSubstitution` mapping `metavar-a` to the bound variable `var 0` and `metavar-b` to `I`, instantiating the β-redex schema to match `I I`.
- **`I_I~>I`**: A `RewriteRelation` proof that `I I` rewrites to `I` by applying `beta-reduction` with `suitable-substitution`, witnessing the system in action.
