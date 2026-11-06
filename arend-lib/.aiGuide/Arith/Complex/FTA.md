### Arith.Complex.FTA

Richman's reformulation of the fundamental theorem of algebra: the root multiset of a monic complex polynomial exists as a point of a completion, without any choice axiom.

For a monic polynomial of degree $n$, the module builds `spectrum`, the multiset of its roots, as a point of the completion `ComplexMultiset n` of the space of $n$-multisets of complex numbers. It is defined as the Cauchy filter `specFilter` of near factorisations (`specSetFilter`), using `approx-factor` and `nearFactor-match`. The construction is choice-free, which is why the multiset is obtained but a single root is not (that is the classical statement in `Arith.Complex.FTA.Classical`). The module proves that the members of the spectrum are exactly the roots of `p` (`spectrum-char`), that `spectrum` inverts `prodLin` on split polynomials with multiplicities (`spectrum_prodLin`), that the spectrum determines `p` (`spectrum-inj`) and that it depends continuously on the coefficients (`spectrum-cont`). Surjectivity of `spectrum` onto the completion is not proved.

#### The space of multisets and the spectrum

- **`ComplexMultiset`**: the complete metric space of completed `n`-multisets of complex numbers.
- **`specSetFilter`**: the filter on arrays of `n` complex numbers whose large sets are those containing `nearFactorSet p delta` for some `delta > 0`.
- **`specFilter`**: for monic `p` of degree `< suc n`, `specSetFilter p` is a proper Cauchy filter on the multiset pseudometric.
- **`spectrum`**: the root multiset of a monic polynomial `p` of degree `< suc n`, as a point of `ComplexMultiset n`.

#### Membership and roots

- **`nearRootSet`**: arrays of `suc m` complex numbers having an entry within `eps` of `z`.
- **`multiset-mem`**: `z` is a member of a multiset `mu`: for every `eps > 0` the set `nearRootSet z eps` is large for `mu`.
- **`root-mem`**: every root of `p` is a member of `spectrum p`.
- **`spectrum-char`**: `z` is a member of `spectrum p` if and only if `polyEval p z = 0`; this sees only the support, not multiplicities.
- **`root-mem-prodLin`**: every entry `r i` of an array is a member of the spectrum of `prodLin r`.

#### Factorisation, injectivity and continuity

- **`spectrum_prodLin`**: `spectrum (prodLin r) = pointSCF r`: the spectrum of a split polynomial is the multiset of its roots, multiplicities included.
- **`spectrum-factors`**: for every `eps > 0` the spectrum of `p` is concentrated on `nearFactorSet p eps`; this expresses `prodLin (spectrum p) = p` without extending `prodLin` to the completion.
- **`spectrum-inj`**: two monic polynomials of the same degree bound with equal spectra are equal.
- **`spectrum-cont`**: for monic `p` and `eps > 0` there is `delta > 0` such that every monic `q` with coefficients within `delta` of those of `p` has `spectrum q` within `eps` of `spectrum p`.
