### Order.PartialOrder

Foundational definitions of preorders and partial orders, presented as thin categories.

This module defines `Preorder` and `Poset` as the basic order-theoretic structures, identifying them with (univalent) categories whose hom-sets are propositions: a preorder is a `Precat` and a poset is a `Cat`, with `<=` serving as `Hom`. The `op` constructions give opposite orders, and the quotient `PreorderC` of a preorder by mutual `<=` yields a canonical poset, exhibiting the standard preorder-to-poset reflection. The module also provides product orders, sub-posets, and predicate-level abstractions of meets and joins (`Is-meet`, `IsJoin`, `IsMeet`) used uniformly by downstream lattice/order theory.

#### Preorder Structure

- **`Preorder`**: Class extending `BaseSet` and `Precat`. A reflexive transitive proposition-valued relation `<=` on a set `E`, viewed as a thin category where objects are elements and morphisms are `<=`-witnesses.
- **`<=`**: The order relation `E -> E -> \Prop`.
- **`<=-refl`**: Reflexivity `x <= x`.
- **`<=-transitive`** (alias **`<=∘`**): Transitivity `x <= y -> y <= z -> x <= z`.
- **`>=`**: Reverse order, defined as `\lam x y => y <= x`.
- **`Preorder.op`**: The opposite preorder, swapping `<=`.
- **`=_<=`**: A propositional equality `x = y` yields `x <= y`.

#### Quotient to a Poset

- **`EquivRel`**: The equivalence relation `x ~ y := (x <= y) × (y <= x)` of mutual ordering.
- **`PreorderC`**: The set-theoretic quotient `Quotient EquivRel.~`.
- **`PosetC`**: The induced poset structure on `PreorderC`, exhibiting the universal poset reflection of a preorder.
- **`<=C`**: The order on `PreorderC`, defined by lifting `<=` through the quotient using `propExt`.
- **`<=C-reflexive`**, **`<=C-transitive`**, **`<=C-antisymmetric`**: Order-axiom proofs for `<=C`.

#### Meets and Joins (predicate form)

- **`Is-meet`**: Binary meet predicate: `m` is a greatest lower bound of `x` and `y`.
- **`IsJoin`**: `a` is the supremum of an `J`-indexed family `f : J -> E`.
- **`IsMeet`**: `a` is the infimum of an `J`-indexed family `f : J -> E`.

#### Poset Structure

- **`Poset`**: Class extending `Preorder` and `Cat`. A preorder with antisymmetry; the `Cat` univalence is automatic via `Cat.makeUnivalence` since isomorphisms reduce to mutual `<=`.
- **`<=-antisymmetric`**: `x <= y -> y <= x -> x = y`.
- **`Poset.op`**: The opposite poset, built on top of `Preorder.op`.

#### Constructions on Orders

- **`ProductPreorder`**: Componentwise preorder structure on `\Sigma P Q`.
- **`ProductPoset`**: Componentwise poset structure on `\Sigma P Q`, extending `ProductPreorder`.
- **`subPoset`**: The poset `\Sigma (x : P) (S x)` of elements satisfying a predicate `S : P -> \Prop`, with order inherited via the first projection.
