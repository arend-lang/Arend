### Arith.Complex.FTA.Moduli

Quantitative estimates relating the roots and the coefficients of a monic complex polynomial: bounds, matching of near factorisations, and roots versus near roots.

The estimates support Richman's spectrum in `Arith.Complex.FTA`. `nearFactor-bound` and `cabs_polyEval-small-disc` give Cauchy-style root bounds. The shrinking lemma `nearFactor-match` says that two sufficiently good near factorisations (`nearFactorSet`) of the same monic polynomial are close in the matching metric on multisets; it is what makes the near factorisations a Cauchy filter. `prodLin-close` is the forward modulus of continuity of `prodLin`. `root-nearRoot` and `nearRoot-root` relate roots of `p` to entries of near factorisations. No root is constructed here.

#### Root bounds

- **`cabs_polyEval-small-disc`**: for monic `p` of degree `< suc (suc m)` whose lower coefficients have modulus at most `C`, any `z` with `cabs (polyEval p z) <= K` satisfies `cabs z <= 2 + (K + C) * natCoef (suc m)` (the Cauchy root bound).
- **`nearFactor-bound`**: for monic `p` of degree `< suc n`, there is `B >= 1` bounding the modulus of every entry of every array in `nearFactorSet p 1`.

#### Matching of near factorisations

- **`nearFactor-match`**: for monic `p` and `eps > 0`, there is `delta > 0` such that any two arrays in `nearFactorSet p delta` are within `eps` in `matchDist` (the matching distance on multisets).
- **`prodLin-close`**: if two root arrays `r`, `s` are entrywise within `d`, and the entries of `s` are bounded by `B`, then the coefficients of `prodLin r - prodLin s` are bounded by `natCoef n * pow (1 + B + d) n * d`.

#### Roots and near factorisations

- **`root-nearRoot`**: if `z` is a root of monic `p` (degree `< suc (suc m)`) and `eta > 0`, there is `delta > 0` such that every array in `nearFactorSet p delta` has an entry within `eta` of `z` (`minDist < eta`).
- **`nearRoot-root`**: if for all `delta`, `eta > 0` there is a near factorisation in `nearFactorSet p delta` (also in `nearFactorSet p 1`) with an entry within `eta` of `z`, then `polyEval p z = 0`.
