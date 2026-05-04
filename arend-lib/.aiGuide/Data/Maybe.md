### Data.Maybe

This module provides the `Maybe` (option) type and basic operations on it.

#### Maybe Type

- **`Maybe`**: Data type with constructors `nothing` and `just A`, representing an optional value.
  - **`map`**: Applies `f : A -> B` to the contained value if `just`, returns `nothing` otherwise.

#### Operations

- **`maybe`**: Eliminator/fold for `Maybe`: `maybe b f nothing = b`, `maybe b f (just a) = f a`.
- **`unjust`**: Extracts the value from a `Maybe`, returning a default if `nothing`.
- **`just-injective`**: `just a = just b` implies `a = b`.
