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

- **`and`**: Infix boolean conjunction (`infixl 3`), defined by elimination on the left argument: `true and y = y` and `false and y = false`.
- **`and.toSigma`**: From `x and y = true` extracts the pair `(x = true, y = true)`.
- **`and.fromSigma`**: From `(x = true, y = true)` derives `x and y = true`.
- **`and-assoc`**, **`and-comm`**, **`and-idemp`**: `and` is associative, commutative and idempotent.

#### Disjunction

- **`or`**: Infix boolean disjunction (`infixl 2`), defined by elimination on the left argument: `true or y = true` and `false or y = y`.
- **`or.toOr`**: From `x or y = true` derives the propositional disjunction `(x = true) || (y = true)`.
- **`or.fromOr`**: From `(x = true) || (y = true)` derives `x or y = true`.
- **`or-false`**: `a or false = a`.

#### Exclusive Or

- **`xor`**: Infixl 2 exclusive or, defined by full case analysis on both arguments.
- **`xor-assoc`**, **`xor-comm`**: `xor` is associative and commutative.
- **`xor_false-left`**, **`xor_false-right`**: `false` is a unit for `xor` on either side.
- **`xor-self`**: `a xor a = false`.
- **`and-xor-ldistr`**: `and` distributes over `xor` on the left: `a and (b xor c) = (a and b) xor (a and c)`.
- **`xor-or-and`**: `(a xor b) xor (a and b) = a or b`.

#### Disjointness

- **`true/=false`**: The constructors are distinct: `true = false` implies `Empty`.
- **`bool-ext`**: Extensionality for booleans: `x = y` follows from `x = true -> y = true` and `y = true -> x = true`.
- **`bool-false`**: `x = false` follows from `x = true -> Empty`.
- **`not-char`**: `not b = true` iff `b = false`.
