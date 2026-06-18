### Algebra.Group.Representation

Linear representations of a group on a module over a ring.

A `LinRepres` combines a left module structure (`LModule` over a ring `R`) with a group action (`GroupAction`) such that the action is compatible with both the module addition and scalar multiplication. This makes each group element act as a linear endomorphism of the module, formalizing the classical notion of a linear representation `G → GL(E)`. The module exposes a coercion `toLinearMap` sending each group element to its associated linear self-map, and provides the trivial representation as a canonical example.

#### Main Class

- **`LinRepres`**: Class of linear representations, extending both `LModule` (over a ring `R`) and `GroupAction` (of a group `G`). Adds the compatibility axioms requiring the action `**` to distribute over module addition and commute with scalar multiplication.
  - **`**-ldistr`**: The action is additive in the module argument: `g ** (e + e') = g ** e + g ** e'`.
  - **`**-*c`**: The action commutes with scalar multiplication: `g ** (c *c e) = c *c (g ** e)`.

#### Derived Properties

- **`g**-zro`**: A group element acts trivially on the zero vector: `g ** 0 = 0`.
- **`g**-negative`**: The action commutes with negation: `g ** negative e = negative (g ** e)`.

#### Linear Map Conversion

- **`toLinearMap`**: Sends a group element `g : G` to the `LinearMap` `e ↦ g ** e` from the representation to itself, packaging the representation's compatibility axioms as additivity and `R`-linearity.
- **`toLinearMap-ide`**: The identity element acts as the identity linear map: `toLinearMap ide = LinearMap.id`.

#### Constructions

- **`TrivialAction`**: Builds the trivial linear representation of a group `G` on a module `E` over a ring `R`, where every group element acts as the identity. Combines `E`'s module structure with the trivial group action and verifies the linearity axioms automatically.
