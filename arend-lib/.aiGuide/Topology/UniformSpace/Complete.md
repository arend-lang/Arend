### Topology.UniformSpace.Complete

Complete uniform spaces and the uniform completion of a uniform space.

A `CompleteUniformSpace` combines `UniformSpace` and `CompleteCoverSpace`, asserting that every regular Cauchy filter converges. The uniform completion is built on top of the underlying cover-space completion: points are regular Cauchy filters, and a cover is uniform when it refines the image of some uniform cover under the canonical `mkSet` operation. The construction comes with the canonical dense uniform embedding `uniform-completion : X -> UniformCompletion X` and a universal property (`dense-uniform-lift`) that lifts uniform maps into a complete uniform space along any dense uniform embedding. Two filter-theoretic characterizations relate Cauchy/regularity conditions to uniform covers, replacing the rather-below relation `<=<` with the uniform-star relation `<=*`.

#### Complete Uniform Spaces

- **`CompleteUniformSpace`**: Class extending `UniformSpace` and `CompleteCoverSpace`; a uniform space whose underlying cover space is complete (every regular Cauchy filter converges).

#### Universal Property

- **`dense-uniform-lift`**: Given a dense uniform embedding `f : UniformMap X Y` and a uniform map `g : UniformMap X Z` into a complete uniform space `Z`, produces the unique extension `UniformMap Y Z`. Built on top of the cover-space `dense-lift`, using that an embedding of uniform spaces is in particular an embedding of cover spaces.

#### The Uniform Completion

- **`UniformCompletion`**: Instance making `Completion X` a `CompleteUniformSpace`. A cover `D` of the completion is uniform iff there exists a uniform cover `C` of `X` such that every `U ∈ C` is refined (via `mkSet U ⊆ V`) by some `V ∈ D`.
- **`UniformCompletion.properUniform`**: If `X` is a proper uniform space, so is its uniform completion.
- **`UniformCompletion.makeUniform`**: From a uniform cover `C` of `X`, produces the uniform cover `{mkSet U | U ∈ C}` of the completion.

#### Embedding into the Completion

- **`mkSet_<=*`**: The `mkSet` operation preserves the uniform-star refinement relation: `V <=* U` implies `mkSet V <=* mkSet U`.
- **`uniform-completion`**: The canonical uniform map `X -> UniformCompletion X`, extending the cover-space `completion` map.
- **`uniform-completion.isDenseEmbedding`**: Witnesses that `uniform-completion` is a dense uniform embedding, enabling use with `dense-uniform-lift`.

#### Filter Characterizations

- **`cauchyFilter-uniform-char`**: A set filter `F` on a uniform space is Cauchy iff for every uniform cover `C` there exists `U ∈ C` with `F U`. Reformulates the cover-space Cauchy condition in terms of uniform covers only.
- **`regularFilter-uniform-char`**: For a Cauchy filter `F`, the regularity condition expressed via the rather-below relation `<=<` is equivalent to the analogous condition expressed via the uniform-star relation `<=*`.
