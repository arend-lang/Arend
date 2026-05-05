### Function.Iterate

Iteration of endofunctions.

- **`iterl`**: Left iteration: `iterl f n a = f(f(...f(a)...))` applying `f` first then recursing.
- **`iterr`**: Right iteration: `iterr f n a = f(f(...f(a)...))` recursing first then applying `f`.
- **`iterr-ind`**: Induction principle for `iterr`: given `P a0` and `P a -> P (f a)`, produces `P (iterr f n a0)`.
- **`iterr-index-ind`**: Index-aware induction: produces `P n (iterr f n a0)` from `P 0 a0` and step.
- **`iterr_inj`**: If `f` is injective and `iterr f n a = iterr f m a` with `n < m`, then `a = iterr f (suc (pred (m -' n))) a`.
