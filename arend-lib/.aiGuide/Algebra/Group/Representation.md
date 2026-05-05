### Algebra.Group.Representation

Linear representations of groups: group actions on a module that are compatible with the module structure.

#### Classes

- **`LinRepres`**: Linear representation, extending `LModule` and `GroupAction`. A group `G` acts on an `R`-module `E` by `R`-linear automorphisms, requiring the action to distribute over module addition (`**-ldistr`: `g ** (e + e') = g ** e + g ** e'`) and commute with scalar multiplication (`**-*c`: `g ** (c *c e) = c *c (g ** e)`).

#### Constructions

- **`TrivialAction`**: The trivial linear representation of a group `G` on an `R`-module `E`, where every group element acts as the identity. Built from `trivialAction G E`; the linearity laws hold definitionally (`idp`).
