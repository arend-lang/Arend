### Set.Partial

Partial elements of a set: values defined on a propositional domain, supporting extensionality, lifting of functions, and a partial-monoid structure.

#### Core Definition

- **`Partial`**: A class representing a partial element of a set `E`, consisting of a proposition `isDefined` and a value function `value : isDefined -> E`.
- **`Partial.make`**: Constructor `(P : \Prop) (f : P -> X) -> Partial X` building a partial element from a proposition and a value function.

#### Constructors

- **`defined`**: The total partial element `defined x : Partial X` with `isDefined = \Sigma` (always defined) returning `x`.
- **`undefined`**: The empty partial element `undefined : Partial X` with `isDefined = Empty` (never defined).

#### Extensionality

- **`partial-ext`**: Two partials are equal given a logical equivalence of their definedness and pointwise equality of values on any witnesses.
- **`partial-ext-left`**: Variant of `partial-ext` quantifying over witnesses of `u.isDefined`.
- **`partial-ext-right`**: Variant of `partial-ext` quantifying over witnesses of `v.isDefined`.
- **`defined-ext`**: If `u` is defined with value `x`, then `u = defined x`.
  - **`defined-ext.isDefined`**: From `u = defined x`, extracts a proof that `u.isDefined`.
  - **`defined-ext.value`**: From `u = defined x`, extracts the equality `u d = x` on the induced witness.
- **`undefined-ext`**: If `u.isDefined` is uninhabited, then `u = undefined`.

#### Equality Consequences

- **`partial-defined`**: From `u = v` derives `u.isDefined <-> v.isDefined`.
- **`partial-value`**: From `u = v` derives equality of values `u d = v e` on any witnesses.
- **`defined-inj`**: Injectivity of `defined`: `defined x = defined y -> x = y`.

#### Lifting

- **`plift`**: Lifts `f : X -> Y` to `Partial X -> Partial Y`, preserving definedness.
- **`plift2`**: Lifts a binary `f : X -> Y -> Z` to `Partial X -> Partial Y -> Partial Z`, defined when both arguments are.

#### Algebraic Structure

- **`PartialAddMonoid`**: Instance making `Partial X` an `AddMonoid` whenever `X` is, with `zro = defined X.zro` and `+ = plift2 (+)`.
