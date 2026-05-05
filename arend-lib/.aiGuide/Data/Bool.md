### Data.Bool

This module defines the `Bool` type and basic boolean operations with associated lemmas.

#### Bool Type

- **`Bool`**: Inductive type with constructors `false` and `true`.

#### So (Boolean Proposition)

- **`So`**: Converts a `Bool` to a proposition: `So true = \Sigma` (unit), `So false = Empty`.
  - **`fromSo`**: `So b` implies `b = true`.
  - **`toSo`**: `b = true` implies `So b`.

#### Boolean Operations

- **`not`**: Boolean negation; `not true = false`, `not false = true`.
- **`not-isInv`**: `not (not b) = b` (involution).
- **`if`**: Conditional expression: `if true a b = a`, `if false a b = b`.
- **`and`** (infix `\infixl 3`): Boolean conjunction; `true and y = y`, `false and y = false`.
  - **`toSigma`**: `x and y = true` implies `(x = true, y = true)`.
  - **`fromSigma`**: `(x = true, y = true)` implies `x and y = true`.
- **`or`** (infix `\infixl 2`): Boolean disjunction; `true or y = true`, `false or y = y`.
  - **`toOr`**: `x or y = true` implies `(x = true) || (y = true)`.
  - **`fromOr`**: `(x = true) || (y = true)` implies `x or y = true`.
- **`xor`** (infix `\infixl 2`): Boolean exclusive or; true when exactly one argument is true.

#### Miscellaneous

- **`true/=false`**: `true = false` implies `Empty` (true and false are distinct).
