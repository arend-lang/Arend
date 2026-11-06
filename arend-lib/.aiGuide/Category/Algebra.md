### Category.Algebra

Internal algebraic structures (commutative monoids, abelian groups, commutative rings) defined inside an arbitrary cartesian category.

This module lifts standard algebraic axioms from `Set` to any `CartesianPrecat` by replacing element-level operations with morphisms involving the terminal object and binary products. Units become morphisms `terminal.apex -> E`, binary operations become morphisms `Bprod E E -> E`, and the usual laws (associativity, commutativity, distributivity) are expressed as commuting diagrams using `prodMap`, `pair`, `proj1`/`proj2`, and the `associator`. This pattern allows the same algebraic theories to be instantiated in `Set`, sheaves, presheaves, or any other cartesian category, yielding e.g. topological groups or sheaves of rings as instances.

#### Base

- **`BaseObject`**: A carrier object `E` in a fixed `CartesianPrecat C`. Common ancestor for all internal algebraic structures.

#### Internal Monoids and Groups

- **`MonoidObject`**: A monoid object in the cartesian category `C`, written multiplicatively. Extends `BaseObject` with a unit `iide : Hom terminal.apex E`, a multiplication `imul : Hom (Bprod E E) E`, and laws `iide-left`, `iide-right` (unit laws), and `imul-assoc` (associativity via the `associator`).
- **`GroupObject`**: A group object. Extends `MonoidObject` with `iinverse : Hom E E` and the laws `iinverse-left` and `iinverse-right` (`x * x^-1 = 1` as diagrams).
- **`CMonoidObject`**: An internal commutative monoid on `E`. Extends `MonoidObject` with commutativity `imul-comm` (`imul` is invariant under the swap `pair proj2 proj1`); `iide-right` is then derived by default from `iide-left`.
- **`comm_pair-swap`**: If `m : Hom (Bprod E E) E` is invariant under the swap `pair proj2 proj1`, then `m ∘ pair f g = m ∘ pair g f` for all generalized elements `f g : Hom z E`.
- **`imul-assoc-char`**: Elementwise characterization of associativity: if `m ∘ pair (m ∘ pair f g) h = m ∘ pair f (m ∘ pair g h)` for all `f g h : Hom z E`, then `m ∘ prodMap m id = m ∘ prodMap id m ∘ associator`.

#### Internal Abelian Groups

- **`AbGroupObject`**: An internal abelian group on `E`. Extends `BaseObject` with zero `izro`, addition `iadd`, and negation `inegative : Hom E E`, together with the abelian group laws `izro-left`, `iadd-assoc`, `iadd-comm`, and `inegative-left` (stating that `a + (-a) = 0` as a diagram).

#### Internal Rings

- **`CRingObject`**: An internal commutative ring on `E`. Extends both `AbGroupObject` and `CMonoidObject`, sharing the carrier and adding the left distributivity law `ildistr` expressing `a * (b + c) = a*b + a*c` via `prodMap` and `pair` of the appropriate projections.

#### Generalized Elements

- **`homMonoid`**: For a `MonoidObject` `G` and an object `X`, the set `Hom X G.E` of generalized elements of `G` is a `Monoid` (pointwise operations).
- **`homGroup`**: For a `GroupObject` `G` and an object `X`, `Hom X G.E` is a `Group`.
- **`imul_o`**: Precomposition distributes over multiplication: `G.imul ∘ pair f g ∘ h = G.imul ∘ pair (f ∘ h) (g ∘ h)`.
- **`precompMonoidHom`**: For `h : Hom X Y`, precomposition with `h` is a `MonoidHom` from `homMonoid Y G` to `homMonoid X G`.
- **`precompHom`**: For `h : Hom X Y`, precomposition with `h` is a `GroupHom` from `homGroup Y G` to `homGroup X G`.
- **`postcompHom`**: A morphism of monoid objects `a : MonoidObjectHom G H` between group objects induces a `GroupHom` from `homGroup X G` to `homGroup X H` by postcomposition.
- **`monHom-iinverse`**: A `MonoidObjectHom` between group objects commutes with inverses: `a.f ∘ G.iinverse = H.iinverse ∘ a.f`.

#### Homomorphisms and Categories of Objects

- **`MonoidObjectHom`**: A homomorphism of monoid objects `Dom -> Cod`: a morphism `f : Hom Dom.E Cod.E` preserving the unit (`func-iide`) and multiplication (`func-imul`).
- **`MonoidObjectHom-char`**: A morphism `f` is a `MonoidObjectHom` iff for every `X`, postcomposition with `f` is a `MonoidHom` between the monoids of generalized elements `homMonoid X G` and `homMonoid X H`.
- **`MonoidObjectPrecat`**: The precategory of monoid objects in `C` and their homomorphisms.
- **`GroupObjectPrecat`**: The precategory of group objects in `C` and their homomorphisms.

#### Transport along Functors

- **`mapMonoidObject`**: Image of a `MonoidObject` under a functor `F` between cartesian precategories that preserves the terminal object (`PreservesTerminal`) and binary products (`PreservesBprod`); the carrier is `F G.E`.
- **`mapGroupObject`**: The same construction for a `GroupObject`.
- **`mapHomGroup`**: Under such an `F`, the map `Hom X G.E -> Hom (F X) (F G.E)` induced by `F` is a `GroupHom` from `homGroup X G` to `homGroup (F X) (mapGroupObject F pt pb G)`.
- **`mapMonoidObjectHom`**: `F` sends a `MonoidObjectHom` `G -> H` to a `MonoidObjectHom` between the mapped monoid objects.
- **`mapMonoidObject-natural`**: Naturality: a natural transformation `a : NatTrans F F'` gives a `MonoidObjectHom` from `mapMonoidObject F ... G` to `mapMonoidObject F' ... G` with underlying morphism `a G.E`.
- **`mapMonoidObject-comp`**: Functoriality: mapping along `Comp G F` agrees with mapping along `F` and then `G`, via the identity morphism as a `MonoidObjectHom`.
