### Data.Bool

The two-element boolean type with standard logical operations and their basic properties.

This module defines `Bool` as an inductive type with constructors `false` and `true`, along with the propositional reflection `So` that converts a boolean into a proposition (true becomes the unit type, false becomes empty). The standard connectives `not`, `and`, `or`, and `xor` are defined by case analysis, and small lemmas bridge the boolean-level equations (e.g. `x and y = true`) to their propositional counterparts (e.g. a pair of equalities), which is the typical pattern for using decidable boolean predicates in propositional reasoning.

#### Core Type

- **`Bool`**: Inductive type with constructors `false` and `true`.

#### Propositional Reflection

- **`So`**: `Bool -> \Prop` sending `true` to `\Sigma` (unit) and `false` to `Empty`; lifts a boolean into a proposition.
- **`So.fromSo`**: Converts `So b` into the equality `b = true`.
- **`So.toSo`**: Converts `b = true` into `So b`.

#### Negation

- **`not`**: Boolean negation, swapping `true` and `false`.
- **`not-isInv`**: Involutivity: `not (not b) = b`.

#### Conditional

- **`if`**: Polymorphic if-then-else: given `b : Bool` and `then else : A`, returns `then` if `b = true` and `else` otherwise.

#### Conjunction

- **`and`**: Infixl 3 boolean conjunction, defined by elimination on the left argument.
- **`and.toSigma`**: From `x and y = true` extracts the pair `(x = true, y = true)`.
- **`and.fromSigma`**: From `(x = true, y = true)` derives `x and y = true`.

#### Disjunction

- **`or`**: Infixl 2 boolean disjunction, defined by elimination on the left argument.
- **`or.toOr`**: From `x or y = true` derives the propositional disjunction `(x = true) || (y = true)`.
- **`or.fromOr`**: From `(x = true) || (y = true)` derives `x or y = true`.

#### Exclusive Or

- **`xor`**: Infixl 2 exclusive or, defined by full case analysis on both arguments.

#### Disjointness

- **`true/=false`**: The constructors are distinct: `true = false` implies `Empty`.
