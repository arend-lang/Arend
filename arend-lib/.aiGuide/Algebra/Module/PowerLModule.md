### Algebra.Module.PowerLModule

The power (function space) construction of left modules, exhibiting `J -> M` as an `R`-module pointwise.

Given an indexing set `J` and an `R`-module `M`, the function type `J -> M` inherits a module structure where all operations are defined componentwise (zero, addition, negation, and scalar multiplication). This is the categorical product of `J` copies of `M` in the category of `R`-modules. The construction is contravariantly functorial in `J`: a function `f : I -> J` of sets induces a linear map `(J -> M) -> (I -> M)` by precomposition, giving a functor from `Set^op` to `LModuleCat R`.

#### Module Structure

- **`PowerLModule`**: For a ring `R`, set `J`, and `R`-module `M`, the `R`-module `J -> M` with all operations defined pointwise. Acts as the `J`-indexed product in `LModuleCat R`.

#### Functoriality

- **`FunctorPowerLMod`**: Contravariant functor `Set^op -> LModuleCat R` sending a set `I` to `PowerLModule I (RingLModule R)` (the free module of functions `I -> R`) and a function `f : I -> J` to the linear map given by precomposition `r |-> r ∘ f`.
