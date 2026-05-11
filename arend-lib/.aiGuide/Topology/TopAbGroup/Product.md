### Topology.TopAbGroup.Product

Product structure and continuity of arithmetic operations for topological abelian groups.

This module equips the cartesian product of two topological abelian groups with its componentwise abelian group structure and the product uniformity, yielding a topological abelian group. It also packages the standard arithmetic operations (negation, addition, subtraction, integer scaling, finite sums) as uniform/cover maps, which are the core tools needed when reasoning about continuity in additive topological settings. The `HasProduct` instance integrates this construction with the generic categorical product machinery so products of topological abelian groups can be formed in a uniform way.

#### Product Structure

- **`ProductTopAbGroup`**: The cartesian product `\Sigma X Y` of two topological abelian groups, with componentwise zero, addition, and negation, and the product uniform space structure. Verifies that addition and negation are continuous, and that the uniform-cover topology agrees with neighborhoods.
- **`TopAbGroupHasProduct`**: `HasProduct` instance registering `ProductTopAbGroup` as the categorical product on `TopAbGroup`.

#### Uniformity of Group Operations

- **`negative-uniform`**: Negation `negative : X -> X` is a uniform map of topological abelian groups (`TopAbGroupMap X X`).
- **`+-uniform`**: Addition `(s.1 + s.2) : X ⨯ X -> X` is a uniform map of topological abelian groups.
- **`subtract-uniform`**: Subtraction `(s.1 - s.2) : X ⨯ X -> X` is a uniform map of topological abelian groups.

#### Integer Scaling

- **`*n-uniform`**: Multiplication by a fixed natural number `n`, viewed as `n X.*n : X -> X`, is a uniform map.
  - **`*n-cont`**: The same map is continuous (`ContMap X X`), used as a building block for the uniform version.
- **`*i-uniform`**: Multiplication by a fixed integer `n`, viewed as `n X.*i : X -> X`, is a uniform map.

#### Finite Sums

- **`BigSum-cover`**: Given an array `fs : Array (CoverMap X Y)` of cover maps from a cover space `X` into a topological abelian group `Y`, the pointwise sum `\lam x => Y.BigSum (\lam j => fs j x)` is itself a cover map. Used to lift continuity of finite sums of cover-continuous functions.
