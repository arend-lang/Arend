### Order.PartialOrder

Foundational definitions of preorders and partial orders, presented as thin categories.

This module defines `Preorder` and `Poset` as the basic order-theoretic structures, identifying them with (univalent) categories whose hom-sets are propositions: a preorder is a `Precat` and a poset is a `Cat`, with `<=` serving as `Hom`. The `Preorder.op` and `Poset.op` constructions give opposite orders, and the quotient `Preorder.PreorderC` of a preorder by mutual `<=` yields a canonical poset, exhibiting the standard preorder-to-poset reflection. The module also provides product orders, sub-posets, and predicate-level abstractions of meets and joins (`Preorder.Is-meet`, `Preorder.IsJoin`, `Preorder.IsMeet`) used uniformly by downstream lattice/order theory.

#### Preorder Structure

- **`Preorder`**: Class extending `BaseSet` and `Precat`. A reflexive transitive proposition-valued relation `<=` on a set `E`, viewed as a thin category where objects are elements and morphisms are `<=`-witnesses.
- **`Preorder.<=`**: The order relation `E -> E -> \Prop`.
- **`Preorder.<=-refl`**: Reflexivity `x <= x`.
- **`Preorder.<=-transitive`** (alias **`<=∘`**): Transitivity `x <= y -> y <= z -> x <= z`.
- **`Preorder.>=`**: Reverse order, defined as `\lam x y => y <= x`.
- **`Preorder.op`**: The opposite preorder, swapping `<=`.

#### Quotient to a Poset

- **`Preorder.EquivRel`**: The equivalence relation `x ~ y := (x <= y) × (y <= x)` of mutual ordering.
- **`Preorder.PreorderC`**: The set-theoretic quotient `Quotient EquivRel.~`.
- **`Preorder.PosetC`**: The induced poset structure on `PreorderC`, exhibiting the universal poset reflection of a preorder.
- **`Preorder.PosetC.<=C`**: The order on `PreorderC`, defined by lifting `<=` through the quotient using `propExt`.
- **`Preorder.PosetC.<=C-reflexive`**, **`Preorder.PosetC.<=C-transitive`**, **`Preorder.PosetC.<=C-antisymmetric`**: Order-axiom proofs for `<=C`.

#### Meets and Joins (predicate form)

- **`Preorder.Is-meet`**: Binary meet predicate: `m` is a greatest lower bound of `x` and `y`.
- **`Preorder.IsJoin`**: `a` is the supremum of an `J`-indexed family `f : J -> E`.
- **`Preorder.IsMeet`**: `a` is the infimum of an `J`-indexed family `f : J -> E`.

#### Poset Structure

- **`Poset`**: Class extending `Preorder` and `Cat`. A preorder with antisymmetry `<=-antisymmetric`; the `Cat` univalence is filled in automatically via `Cat.makeUnivalence`, since isomorphisms reduce to mutual `<=`.
- **`Poset.<=-antisymmetric`**: `x <= y -> y <= x -> x = y`.
- **`Poset.op`**: The opposite poset, built on top of `Preorder.op`.

#### Constructions on Orders

- **`ProductPreorder`**: Componentwise preorder structure on `\Sigma P Q`.
- **`ProductPoset`**: Componentwise poset structure on `\Sigma P Q`, extending `ProductPreorder`.
- **`subPoset`**: The poset `\Sigma (x : P) (S x)` of elements satisfying a predicate `S : P -> \Prop`, with order inherited via the first projection.

- **`TrivialPoset`**: The one-element poset on `\Sigma`, where every pair is related.
- **`=_<=`**: A path `x = y` in a preorder yields `x <= y`.
- **`=<=`**: Mixes a path with an order step: `x = y -> y <= z -> x <= z`. Useful for equational-then-inequational reasoning chains.

#### Order-Preserving Maps

- **`PreorderHom`**: Record extending `SetHom` and `Functor`: a map `func` between preorders that preserves order (`func-<= : x <= y -> func x <= func y`), i.e. a functor between the thin categories. Has `PreorderHom.id` and composition `PreorderHom.compose` (`∘`).
- **`PosetHom`**: Record extending `PreorderHom` whose domain and codomain are posets: a monotone map between posets. Has `PosetHom.id` and composition `PosetHom.compose` (`∘`).
