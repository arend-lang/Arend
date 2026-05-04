### Arith.Real.IVT

This module provides the Intermediate Value Theorem for real-valued functions.

#### IVT

- **`IVT`**: Given a function `f : Real -> Real` that is locally non-constant (satisfies a locatedness/covering condition), `f a < 0`, `f b > 0`, and `a < b`, there exists `c` with `f c = 0`. Proved via bisection using `InfReal.focus-iter`.
  - **`IVT-lem`**: Helper lemma performing the bisection step.
