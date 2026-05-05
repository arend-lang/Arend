### Topology.UniformSpace.Complete

Complete uniform spaces and the uniform completion construction, extending cover-space completeness with uniform structure.

#### Core Class

- **`CompleteUniformSpace`**: A uniform space that is also a complete cover space; extends `UniformSpace` and `CompleteCoverSpace`.

#### Universal Property

- **`dense-uniform-lift`**: Given a dense uniform embedding `f : X -> Y` and a uniform map `g : X -> Z` into a complete uniform space, produces the unique extending uniform map `Y -> Z`.

#### Completion Construction

- **`UniformCompletion`**: The completion of a uniform space `X` as a `CompleteUniformSpace`, built on top of the cover-space `Completion X`; its uniform covers are those refined by sets of the form `mkSet U` for `U` in some uniform cover of `X`.
- **`UniformCompletion.properUniform`**: If `X` is proper uniform, so is its completion.
- **`UniformCompletion.makeUniform`**: A uniform cover `C` of `X` lifts to the uniform cover `{mkSet U | U ∈ C}` of the completion.

#### Embedding into the Completion

- **`uniform-completion`**: The canonical uniform map `X -> UniformCompletion X`, refining the cover-space `completion` map.
- **`uniform-completion.isDenseEmbedding`**: The canonical map is a dense uniform embedding, witnessing the universal property.

#### Star-Refinement Lemma

- **`mkSet_<=*`**: Star-refinement is preserved by `mkSet`: if `V <=* U` then `mkSet V <=* mkSet U`.

#### Filter Characterizations

- **`cauchyFilter-uniform-char`**: A set filter `F` is Cauchy iff every uniform cover `C` contains a member `U` with `F U`.
- **`regularFilter-uniform-char`**: For a Cauchy filter, the regularity condition via the rather-below relation `<=<` is equivalent to the corresponding condition via star-refinement `<=*`.
