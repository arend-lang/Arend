### Topology.UniformSpace.Product

Product construction for (regular pre)uniform spaces, with the projection and tupling maps.

#### Product Instances

- **`RegularPreuniformSpaceHasProduct`**: `HasProduct` instance for `RegularPreuniformSpace`, using `ProductRegularPreuniformSpace`.
- **`ProductRegularPreuniformSpace`**: Product of two regular preuniform spaces `X`, `Y` on `\Sigma X Y`. Its uniform covers are those refined by products `Prod U V` of a uniform cover `C` of `X` and a uniform cover `D` of `Y`. Underlying cover space is `ProductCoverSpace X Y`.
- **`UniformSpaceHasProduct`**: `HasProduct` instance for `UniformSpace`, using `ProductUniformSpace`.
- **`ProductUniformSpace`**: Product uniform space on `\Sigma X Y`, extending `ProductRegularPreuniformSpace` with the star-refinement axiom.

#### Properness

- **`ProductUniformSpace.properUniform`**: If `X` and `Y` are properly uniform, so is their product `X ⨯ Y`.

#### Projections and Maps

- **`ProductUniformSpace.proj1`**: First projection `UniformMap (X ⨯ Y) X`.
- **`ProductUniformSpace.proj2`**: Second projection `UniformMap (X ⨯ Y) Y`.
- **`ProductUniformSpace.tuple`**: Pairing of uniform maps: given `f : UniformMap Z X` and `g : UniformMap Z Y`, builds `UniformMap Z (X ⨯ Y)` mapping `z ↦ (f z, g z)`.
- **`ProductUniformSpace.prod`**: Functorial action on uniform maps: `f : X → Y` and `f' : X' → Y'` yield `UniformMap (X ⨯ X') (Y ⨯ Y')`, defined as `tuple (f ∘ proj1) (f' ∘ proj2)`.

#### Cover Lemmas

- **`ProductUniformSpace.prodCover`**: From uniform covers `C` of `X` and `D` of `Y`, the family of rectangles `λ s. U s.1 ∧ V s.2` (for `U ∈ C`, `V ∈ D`) is a uniform cover of `X ⨯ Y`.
