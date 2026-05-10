### Algebra.Pointed.PointedHom

Homomorphisms between pointed sets, preserving the distinguished basepoint.

This module defines the morphism layer for the category of pointed sets, extending `SetHom` with a preservation law for the distinguished element. Two parallel variants are provided: `PointedHom` for multiplicative pointed structures (preserving `ide`) and `AddPointedHom` for additive ones (preserving `zro`). Each comes equipped with identity and composition operations, supplying the basic categorical infrastructure used downstream by group, monoid, and ring homomorphism hierarchies.

#### Multiplicative Pointed Homomorphisms

- **`PointedHom`**: Record extending `SetHom` with domain and codomain restricted to `Pointed`, plus the law `func-ide : func ide = ide` that the underlying function preserves the distinguished identity element.
- **`PointedHom.id`**: The identity homomorphism `PointedHom X X` on a pointed set `X`, given by the identity function.
- **`PointedHom.compose`** (alias **`∘`**, `\infixl 8`): Composition of pointed homomorphisms `g ∘ f : PointedHom X Z` from `f : PointedHom X Y` and `g : PointedHom Y Z`, with the preservation law inherited via composition.

#### Additive Pointed Homomorphisms

- **`AddPointedHom`**: Record extending `SetHom` with domain and codomain restricted to `AddPointed`, plus the law `func-zro : func zro = zro` that the underlying function preserves the distinguished zero element.
- **`AddPointedHom.id`**: The identity homomorphism `AddPointedHom X X` on an additive pointed set, given by the identity function.
- **`AddPointedHom.compose`** (alias **`∘`**, `\infixl 8`): Composition of additive pointed homomorphisms producing an `AddPointedHom A C` from `f : AddPointedHom A B` and `g : AddPointedHom B C`.
