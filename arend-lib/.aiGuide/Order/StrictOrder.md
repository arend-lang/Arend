### Order.StrictOrder

Strict (irreflexive) order structures and the notion of strictly monotone maps.

#### Strict Posets

- **`StrictPoset`**: Extends `BaseSet` with a strict order relation. Provides:
  - **`<`**: Strict order relation `E -> E -> \Prop`.
  - **`<-irreflexive`**: `Not (x < x)`.
  - **`<-transitive`** (alias **`<∘`**): Transitivity `x < y -> y < z -> x < z`.
  - **`>`**: Reverse strict order, defined as `\lam x y => y < x`.

#### Equational/Inequational Reasoning

- **`Reasoning.>>>`**: Chains two strict inequalities `x < y < z` into `x < z`.
- **`Reasoning.>>=`**: Combines a strict inequality with an equality on the right: `x < y -> y = z -> x < z`.
- **`Reasoning.>=>`**: Combines an equality with a strict inequality on the right: `x = y -> y < z -> x < z`.
- **`Reasoning.<<<`**: Starts a strict-inequality reasoning chain, returning the proof unchanged.

#### Monotonicity

- **`IsStrictlyMonotone`**: Predicate on `f : X -> Y` between strict posets stating that `x < x'` implies `f x < f x'`.
