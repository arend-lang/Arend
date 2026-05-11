### Logic.Rewriting.TRS.Utils

Small utility helpers for working with terms in the higher-order rewriting system (HRS) framework.

This module provides a couple of low-level conveniences used elsewhere in the TRS development: a helper for case-splitting on the head symbol of a term, and a lemma for extracting equality of dependent components when the base index is fixed. They are kept here so that the main TRS files can avoid repeating boilerplate pattern matches and sigma-equality manipulations.

#### Term Inspection

- **`impossible-lambda`**: A `\meta` shorthand `\lam e => contradiction`, used to discharge impossible cases via lambda when pattern matching.
- **`unwrap-func`**: Given a term `T : Term env context s mc`, returns `just (f, arguments)` if `T` is headed by a function symbol `f` with the expected dependent argument family, and `nothing` for variables or metavariables. Used to safely peel off the function-symbol layer of a term.

#### Sigma Equality

- **`sigma-set-equalizer`**: For a type family `B : A -> \Type` over a set `A`, given an equality `(a, b) = (a, b')` in `\Sigma (x : A) (B x)` with the same first component `a`, extracts the underlying equality `b = b'`. Relies on `prop-isProp` to identify the (necessarily reflexive) first-component path with `idp` before applying dependent `pmap`.
