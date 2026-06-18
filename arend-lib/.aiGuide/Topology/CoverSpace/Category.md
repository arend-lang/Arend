### Topology.CoverSpace.Category

Category instances for precover spaces and cover spaces with their structure-preserving maps.

This module packages `PrecoverSpace` and `CoverSpace` as categories in the standard `Cat` framework, with morphisms given by `PrecoverMap` and `CoverMap` respectively. The instances supply identity, composition, the usual category laws, and univalence, enabling these topological structures to participate in generic categorical constructions (limits, colimits, adjunctions, functors). Together they form the categorical setting in which (pre)cover spaces and their completions can be studied uniformly with other algebraic and topological categories in the library.

#### Category Instances

- **`PrecoverSpaceCat`**: The category `Cat PrecoverSpace` whose objects are precover spaces and whose morphisms are `PrecoverMap`s. Identity is `PrecoverMap.id`, composition is `PrecoverMap.∘`, and the instance witnesses the category laws (`id-left`, `id-right`, `o-assoc`) along with `univalence`.
- **`CoverSpaceCat`**: The category `Cat CoverSpace` whose objects are cover spaces and whose morphisms are `CoverMap`s. Identity is `CoverMap.id`, composition is `CoverMap.∘`, with the category laws and univalence supplied. Use this when reasoning categorically about cover spaces (e.g., functoriality of completion, products, or comparison with other topological categories).
