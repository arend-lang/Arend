import os, re

missing = [
    "Domain/PID", "Domain/Valuation",
    "Field/Algebraic", "Field/AlgebraicClosure", "Field/Splitting",
    "Group/Aut", "Group/Fin", "Group/Free", "Group/GSet",
    "Group/GSet/CategoricalDefinition", "Group/GSet/Category", "Group/GSet/ExampleActions",
    "Group/GroupCategory", "Group/GroupHom", "Group/Lagrange", "Group/Product",
    "Group/QuotientProperties", "Group/Representation",
    "Group/Representation/Category", "Group/Representation/Characters",
    "Group/Representation/Irreducible", "Group/Representation/MaschkeLemma",
    "Group/Representation/PermutationRepresentation", "Group/Representation/Product",
    "Group/Representation/Sub", "Group/Solver", "Group/Sub", "Group/Symmetric",
    "Linear/Matrix", "Linear/Matrix/CayleyHamilton", "Linear/Matrix/CharPoly",
    "Linear/Matrix/Smith", "Linear/Solver", "Linear/VectorSpace",
    "Module/FinModule", "Module/LinearMap", "Module/ModuleCategory",
    "Module/PowerLModule", "Module/Sub", "Module/Trace",
    "Ordered/OrderedLocalization", "Ordered/RieszSpace",
    "Pointed/PointedCategory", "Pointed/PointedHom", "Pointed/Sub",
    "Ring/Boolean", "Ring/Boolean/Sub", "Ring/Factor", "Ring/FormalSeries",
    "Ring/Graded", "Ring/Graded/Ideal", "Ring/Graded/Localization",
    "Ring/GrothendieckRing", "Ring/Ideal", "Ring/Integral", "Ring/Integral/MinPoly",
    "Ring/Local", "Ring/Localization", "Ring/Localization/Field",
    "Ring/Localization/Properties", "Ring/MPoly", "Ring/MonoidRing",
    "Ring/Nakayama", "Ring/Noetherian", "Ring/Poly", "Ring/Poly/Euclidean",
    "Ring/QPoly", "Ring/QuotientProperties", "Ring/Reduced",
    "Ring/RingCategory", "Ring/RingHom", "Ring/Solver", "Ring/Sub",
    "Ring/UnitAlgebra", "Ring/ZeroDimensional",
    "Semiring/Sub",
    "Solver/BooleanRing", "Solver/CGroup", "Solver/CMonoid", "Solver/CRing",
    "Solver/CSemiring", "Solver/Group", "Solver/Monoid", "Solver/Ring", "Solver/Semiring",
]

def extract_defs(filepath):
    if not os.path.exists(filepath):
        return []
    with open(filepath) as f:
        lines = f.readlines()
    defs = []
    for line in lines:
        m = re.match(r'^(\s*)\\(class|func|lemma|data|record|instance|type|module)\s+(\S+)', line)
        if not m:
            continue
        indent = len(m.group(1))
        kind = m.group(2)
        name = m.group(3)
        extends = ''
        ext_m = re.search(r'\\extends\s+([^{]+)', line)
        if ext_m:
            extends = ext_m.group(1).strip().rstrip('{').strip()
        defs.append((indent, kind, name, extends))
    return defs

for mod in missing:
    src = f".compactifiedLib/Algebra/{mod}.ard"
    out = f".aiGuide/Algebra/{mod}.md"
    os.makedirs(os.path.dirname(out), exist_ok=True)
    defs = extract_defs(src)
    module_name = f"Algebra.{mod.replace('/', '.')}"
    lines_out = [f"### {module_name}", ""]
    if not defs:
        lines_out.append(f"Module for `{mod.split('/')[-1]}` utilities.")
    else:
        top_defs = [(i, d) for i, d in enumerate(defs) if d[0] == 0]
        for idx, (pos, (indent, kind, name, extends)) in enumerate(top_defs):
            next_pos = top_defs[idx+1][0] if idx+1 < len(top_defs) else len(defs)
            children = [d for d in defs[pos+1:next_pos] if d[0] > 0]
            bullet = f"- **`{name}`**"
            ext_str = f" Extends `{extends}`." if extends else ""
            if kind in ('class', 'record'):
                child_names = [c[2] for c in children[:15]]
                child_str = ""
                if child_names:
                    child_str = " Has: `" + "`, `".join(child_names) + "`."
                    if len(children) > 15:
                        child_str = child_str[:-1] + ", and more."
                lines_out.append(f"{bullet}:{ext_str}{child_str}")
            elif kind == 'data':
                child_names = [c[2] for c in children[:10]]
                child_str = ""
                if child_names:
                    child_str = " Constructors/members: `" + "`, `".join(child_names) + "`."
                lines_out.append(f"{bullet}: Data type.{ext_str}{child_str}")
            elif kind == 'instance':
                lines_out.append(f"{bullet}: Instance.{ext_str}")
            elif kind in ('func', 'lemma'):
                child_names = [c[2] for c in children[:10]]
                child_str = ""
                if child_names:
                    child_str = " Helpers: `" + "`, `".join(child_names) + "`."
                tag = 'Lemma' if kind == 'lemma' else 'Function'
                lines_out.append(f"{bullet}: {tag}.{child_str}")
            elif kind == 'type':
                lines_out.append(f"{bullet}: Type alias.")
            elif kind == 'module':
                child_names = [c[2] for c in children[:10]]
                child_str = ""
                if child_names:
                    child_str = " Contains: `" + "`, `".join(child_names) + "`."
                lines_out.append(f"{bullet}: Module.{child_str}")
    lines_out.append("")
    with open(out, 'w') as f:
        f.write('\n'.join(lines_out) + '\n')

print(f"Generated {len(missing)} files")
