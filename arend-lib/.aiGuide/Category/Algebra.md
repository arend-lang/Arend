### Category.Algebra

Internal algebraic structures (commutative monoids, abelian groups, commutative rings) defined as objects in a cartesian category.

#### Base

- **`BaseObject`**: An object `E` in a `CartesianPrecat` `C`, used as the carrier for internalized algebraic structures.

#### Internal Commutative Monoids

- **`CMonoidObject`**: Extends `BaseObject` with an internal commutative monoid structure on `E`.
  - **`iide`**: Unit morphism `Hom terminal.apex E`.
  - **`imul`**: Multiplication morphism `Hom (Bprod E E) E`.
  - **`iide-left`**: Left unit law: `imul ∘ pair (iide ∘ terminalMap) (id E) = id E`.
  - **`imul-assoc`**: Associativity expressed via the cartesian associator.
  - **`imul-comm`**: Commutativity: `imul ∘ pair proj2 proj1 = imul`.

#### Internal Abelian Groups

- **`AbGroupObject`**: Extends `BaseObject` with an internal abelian group structure on `E`.
  - **`izro`**: Zero morphism `Hom terminal.apex E`.
  - **`iadd`**: Addition morphism `Hom (Bprod E E) E`.
  - **`inegative`**: Negation endomorphism `Hom E E`.
  - **`izro-left`**: Left identity law for addition.
  - **`iadd-assoc`**: Associativity of addition.
  - **`iadd-comm`**: Commutativity of addition.
  - **`inegative-left`**: Left inverse law: `iadd ∘ pair inegative (id E) = izro ∘ terminalMap`.

#### Internal Commutative Rings

- **`CRingObject`**: Extends `AbGroupObject` and `CMonoidObject`, combining an internal abelian group (addition) and commutative monoid (multiplication) on the same carrier.
  - **`ildistr`**: Left distributivity of multiplication over addition, expressed through prodMap and pairing of projections.
