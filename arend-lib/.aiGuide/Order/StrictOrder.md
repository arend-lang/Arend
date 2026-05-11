### Order.StrictOrder

Strict (irreflexive) order relations and their basic algebra.

This module introduces the `StrictPoset` class, axiomatizing a strict order `<` via irreflexivity and transitivity, with `>` defined as the flip of `<`. The opposite order `op` and the derived inequality lemmas (`<_/=`, `>_/=`) show that any strict order yields a discrete distinction between related elements. A small `Reasoning` submodule provides chaining combinators so that mixed strict/equational arguments compose like equational reasoning chains, and `IsStrictlyMonotone` captures the standard preservation property for maps between strict posets.

#### Strict Poset Class

- **`StrictPoset`**: Extends `BaseSet`. A type equipped with a strict order `<` that is irreflexive (`<-irreflexive : Not (x < x)`) and transitive (`<-transitive`/`<∘`). Provides `>` as the flipped relation `\lam x y => y < x`.
- **`<`**: The strict less-than relation, `E -> E -> \Prop`, declared `\infix 4`.
- **`>`**: The strict greater-than relation, defined as `\lam x y => y < x`.
- **`<-irreflexive`**: Axiom that `x < x` is impossible.
- **`<-transitive`** (alias **`<∘`**): Transitivity `x < y -> y < z -> x < z`, declared `\infixr 9`.

#### Derived Constructions

- **`op`**: The opposite strict poset on the same carrier, swapping `<`. Used to dualize statements about `<` to statements about `>`.
- **`<_/=`**: From `x < y` derives `x /= y`, since equality plus strictness would contradict irreflexivity.
- **`>_/=`**: Symmetric form: `x > y` implies `x /= y`.

#### Reasoning Combinators

- **`>>>`**: Chains two strict inequalities `x < y` and `y < z` into `x < z` (transitivity in chain form).
- **`>>=`**: Chains a strict inequality with an equation: `x < y` and `y = z` give `x < z`.
- **`>=>`**: Chains an equation with a strict inequality: `x = y` and `y < z` give `x < z`.
- **`<<<`**: Identity-like opener for a strict reasoning chain, fixing the starting element `x` and returning the proof `p : x < y`.

#### Monotonicity

- **`IsStrictlyMonotone`**: Predicate on `f : X -> Y` between strict posets asserting `x < x' -> f x < f x'`. The standard order-preservation condition for strict orders.
