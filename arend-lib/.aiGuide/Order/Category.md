### Order.Category

Categories of ordered sets and their order-preserving morphisms.

This module assembles the basic categorical infrastructure for order theory by packaging each flavor of ordered set (posets, strict posets, decidable linear orders) as a category with monotone maps as morphisms. Each homomorphism record extends `SetHom` and adds the appropriate order-preservation field, and each category instance reuses ordinary function composition while threading the order-preservation proofs through `id` and `o`. The structures are kept parallel so that `PosetCat`, `StrictPosetCat`, and `DecLinearOrderCat` can be plugged interchangeably wherever a categorical context over orders is required.

#### Morphisms

- **`PosetHom`**: Record extending `SetHom` with `Dom Cod : Poset`, carrying a monotonicity field `func-<= : x <= y -> func x <= func y`. Used as the morphism type for posets.
- **`StrictPosetHom`**: Record extending `SetHom` with `Dom Cod : StrictPoset`, carrying a strict-monotonicity field `func-< : x < y -> func x < func y`. Used as the morphism type for strict posets.

#### Categories

- **`PosetCat`**: Instance of `Cat` whose objects are `Poset`s and whose morphisms are `PosetHom`s; identity and composition are inherited from functions, with monotonicity composed pointwise.
- **`StrictPosetCat`**: Instance of `Cat` whose objects are `StrictPoset`s and whose morphisms are `StrictPosetHom`s, analogous to `PosetCat` but tracking strict order preservation.
- **`DecLinearOrderCat`**: Instance of `Cat` whose objects are decidable linear orders (`LinearOrder.Dec`) and whose morphisms are `PosetHom`s, viewing decidable linear orders categorically through their underlying poset structure.
