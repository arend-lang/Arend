### Algebra.Pointed.PointedHom

Homomorphisms between pointed sets, preserving the distinguished basepoint (multiplicative `ide` or additive `zro`).

#### Multiplicative Pointed Homomorphisms

- **`PointedHom`**: Class extending `SetHom` between two `Pointed` sets, with the additional law **`func-ide`**: `func ide = ide` requiring preservation of the distinguished point.
- **`PointedHom.id`**: The identity homomorphism `PointedHom X X` on any `Pointed` `X`, with trivial preservation proof.
- **`PointedHom.compose`** (alias **`∘`**): Composition of pointed homomorphisms `g ∘ f : PointedHom X Z` from `f : PointedHom X Y` and `g : PointedHom Y Z`; preservation is established via `pmap g f.func-ide *> g.func-ide`.

#### Additive Pointed Homomorphisms

- **`AddPointedHom`**: Class extending `SetHom` between two `AddPointed` sets, with the additional law **`func-zro`**: `func zro = zro` requiring preservation of the additive zero.
- **`AddPointedHom.id`**: The identity homomorphism `AddPointedHom X X` on any `AddPointed` `X`, with trivial zero-preservation.
- **`AddPointedHom.compose`** (alias **`∘`**): Composition of additive pointed homomorphisms; zero-preservation follows from `pmap g f.func-zro *> g.func-zro`.
