### Data.Maybe

The optional/partial value type `Maybe A`, representing `A` extended with a distinguished "no value" element.

This module defines the standard `Maybe` (option) datatype with constructors `nothing` and `just`, together with its basic eliminator `maybe` and a few utility functions. The design follows the usual functional-programming pattern: `Maybe` is a sum type used to model partiality, and `maybe` is its non-dependent recursor. A small `just-injective` lemma is provided to recover equality of contents from equality of `just` constructors via the standard "extract or default" trick using `unjust`.

#### Type Definition

- **`Maybe`**: The optional type `Maybe (A : \Type)` with constructors `nothing` (absent value) and `just : A -> Maybe A` (present value).

#### Functorial Action

- **`Maybe.map`**: Functorial action on `Maybe`: given `f : A -> B`, produces `Maybe A -> Maybe B` by mapping `just a` to `just (f a)` and preserving `nothing`.

#### Elimination

- **`maybe`**: Non-dependent eliminator for `Maybe`: `maybe (b : B) (f : A -> B) (m : Maybe A) : B`, returning `b` on `nothing` and `f a` on `just a`. The standard case-analysis combinator.
- **`unjust`**: Extracts the contained value, falling back to a default: `unjust (a : A) (m : Maybe A) : A` returns `a` on `nothing` and the wrapped element on `just`.

#### Injectivity

- **`just-injective`**: Constructor injectivity for `just`: from `just a = just b` derives `a = b`, proved by `pmap (unjust a)`.
