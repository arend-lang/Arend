### Algebra.Module.PowerLModule

Power modules: the `LModule` structure on function spaces `J -> M` indexed by an arbitrary set, together with the contravariant functor sending an index set to its power module over the base ring.

#### Power Module Instance

- **`PowerLModule`**: Given a ring `R`, an index set `J`, and an `R`-module `M`, the function space `J -> M` is itself an `R`-module with pointwise zero, addition, negation, and scalar multiplication. All module axioms (associativity, commutativity, distributivity, identity) are verified pointwise via `ext`.

#### Functorial Structure

- **`FunctorPowerLMod`**: A contravariant functor `SetCat.op -> LModuleCat R` sending an index set `I` to the power module `PowerLModule I (RingLModule R)` (functions `I -> R` with pointwise `R`-module structure). On morphisms, a function `f : J -> I` is sent to the linear precomposition map `r |-> r o f`, which is automatically additive and scalar-linear.
