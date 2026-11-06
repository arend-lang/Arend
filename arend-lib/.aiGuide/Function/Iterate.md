### Function.Iterate

Iterated application of an endofunction, with induction principles and an injectivity lemma.

This module formalizes the n-fold composition of a function `f : A -> A` in two equivalent styles: left-associated (`iterl`) where the function is applied to the argument first and the result threaded through, and right-associated (`iterr`) where the recursion wraps `f` around the recursive call. The right-associated form admits clean induction principles for proving properties of iterated values, with both index-free and index-aware variants. The injectivity lemma exploits these to characterize cycles: if `f` is injective and two iterates of `a` agree, then `a` itself lies on a shorter orbit.

#### Iteration

- **`iterl`**: Tail-recursive iteration `f^n(a)` defined by `iterl f (suc n) a = iterl f n (f a)`. Applies `f` first and recurses on the result.
- **`iterr`**: Structurally recursive iteration `f^n(a)` defined by `iterr f (suc n) a = f (iterr f n a)`. Wraps `f` around the recursive call.

#### Induction Principles

- **`iterr-ind`**: Induction on `iterr`: given `P a0` and a step `P a -> P (f a)`, produces `P (iterr f n a0)` for any `n`.
- **`iterr-index-ind`**: Index-aware induction: given `P 0 a0` and a step `P n a -> P (suc n) (f a)`, produces `P n (iterr f n a0)`, tracking the iteration index alongside the value.

#### Injectivity

- **`iterr_inj`**: For an injective `f` on a set `A`, if `iterr f n a = iterr f m a` with `n < m`, then `a` is a fixed point of `iterr f (suc (pred (m -' n)))`, exhibiting the cycle length on `a`'s orbit.
