### Set.Partial

Partial elements of a set, represented as values defined on a propositional condition.

A `Partial X` packages a proposition `isDefined` together with a function from a proof of that proposition to a value in `X`. This encodes the "maybe defined" pattern in a propositionally-truncated style: equality of partial elements is determined by logical equivalence of their definedness propositions plus agreement of their values where defined. The module provides constructors for total and never-defined partial elements, extensionality lemmas for proving equalities, functorial lifts of ordinary functions, and an `AddMonoid` instance that lifts addition pointwise (with the identity always defined).

#### Core Record

- **`Partial`**: A record over `E : \Set` with a propositional definedness `isDefined : \Prop` and a coercion `value : isDefined -> E` extracting the value from a definedness witness.
- **`Partial.HasValue`**: Predicate `\Sigma (P : isDefined) (value P = a)` asserting the partial element is defined and equals `a`.
- **`Partial.make`**: Builder constructing a `Partial X` from a proposition `P` and a function `P -> X`.

#### Constructors

- **`defined`**: The always-defined partial element with `isDefined = \Sigma` (unit) and constant value `x`.
- **`undefined`**: The never-defined partial element with `isDefined = Empty`.

#### Extensionality

- **`partial-ext`**: Equality of partial elements from a logical equivalence of definedness propositions and pointwise agreement of values on both witnesses.
- **`partial-ext-left`**: Variant of `partial-ext` requiring agreement only over witnesses of `u.isDefined`.
- **`partial-ext-right`**: Variant of `partial-ext` requiring agreement only over witnesses of `v.isDefined`.
- **`defined-ext`**: If `u` is defined and its value equals `x`, then `u = defined x`.
  - **`defined-ext.isDefined`**: Recovers `u.isDefined` from `u = defined x`.
  - **`defined-ext.value`**: Recovers the value equation `u d = x` from `u = defined x`.
- **`undefined-ext`**: If `u.isDefined` is uninhabited, then `u = undefined`.

#### Equality Consequences

- **`partial-defined`**: Equality of partials gives a logical equivalence of their definedness propositions.
- **`partial-value`**: Equality of partials gives equality of their values on any witnesses of definedness.
- **`defined-inj`**: Injectivity of `defined`: from `defined x = defined y` derive `x = y`.

#### Functorial Lifts

- **`plift`**: Lift a function `f : X -> Y` to `Partial X -> Partial Y`, preserving the underlying definedness.
- **`plift2`**: Lift a binary function `f : X -> Y -> Z` to `Partial X -> Partial Y -> Partial Z`, with combined definedness `\Sigma p.isDefined q.isDefined`.

#### Algebraic Structure

- **`PartialAddMonoid`**: `AddMonoid` instance on `Partial X` for an `AddMonoid X`, with `zro = defined X.zro` and `+` given by `plift2 (+)`.
