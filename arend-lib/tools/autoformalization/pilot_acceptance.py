#!/usr/bin/env python3
"""Acceptance check for the Fulton--Harris pilot (docs/fulton-harris-pilot-plan.tex, Section "Verification protocol").

Run from the arend-lib directory with the rebuilt CLI on PATH as `arend`:

    python3 -I tools/autoformalization/pilot_acceptance.py [--report out.json]

Checks, all of which must pass:
  1. every formal target in docs/fulton-harris/manifest.jsonl is a parsed declaration (symbol index, so a definition
     swallowed by a stray `{-` in a comment is caught);
  2. a fresh, double-checked typecheck (`arend --no-daemon -r -c`) of all target modules, in dependency
     order, reports no errors;
  3. the reported goals are exactly the intentional exercise holes registered in the manifest;
  4. the library test suite passes (`arend --no-daemon -t`), including the in-suite invalid-proof control;
  5. the import closure of every target module avoids Logic.Classical and Logic.Choice;
  6. the declaration-level consumer closure of the exercise holes (via `arend -fu --json`) agrees with the
     manifest statuses: consumers are `conditional-on-exercises`, everything else is `checked`;
  7. negative control: the axiom audit flags tools/autoformalization/controls/ClassicalControl.ard;
  8. negative control: the error detection of step 2 flags a deliberately invalid proof;
  9. doc comments in the target modules are well formed: a multi-line doc comment uses `{- | ... -}`, never a `-- |`
     line followed by `--` continuation lines (which are not part of the documentation), no `{-` or `-}` occurs
     inside a block doc comment's text, and every `{name}` link outside math resolves: to a definition of the module,
     of an imported module (respecting import lists), or to a parameter of the documented declaration.
Exit status 0 iff all checks pass.
"""
import json, os, re, subprocess, sys, tempfile
from collections import deque

ROOT = os.getcwd()
SRC = os.path.join(ROOT, 'src')
TEST = os.path.join(ROOT, 'test')
MANIFEST = os.path.join(ROOT, 'docs', 'fulton-harris', 'manifest.jsonl')
CONTROLS = os.path.join(ROOT, 'tools', 'autoformalization', 'controls')
FORBIDDEN = {'Logic.Classical', 'Logic.Choice'}
GOAL_RE = re.compile(r'\[GOAL\] ([\w.]+):\d+:\d+: Goal (\S+)')
ERROR_RE = re.compile(r'\[ERROR\] ([\w.]+):\d+:\d+')


def run(args, cwd=ROOT):
    p = subprocess.run(args, cwd=cwd, capture_output=True, text=True)
    return p.returncode, p.stdout + p.stderr


def module_file(mod, roots):
    for r in roots:
        f = os.path.join(r, *mod.split('.')) + '.ard'
        if os.path.exists(f):
            return f
    return None


def imports(mod, roots):
    f = module_file(mod, roots)
    if f is None:
        return []
    out = []
    for line in open(f, encoding='utf-8'):
        m = re.match(r'\s*\\import\s+([A-Za-z0-9_.]+)', line)
        if m:
            out.append(m.group(1))
    return out


def import_closure_hits(mod, roots):
    """Shortest import chain from mod to a forbidden module, or None. Over-approximates real dependency."""
    parent = {mod: None}
    q = deque([mod])
    while q:
        m = q.popleft()
        if m in FORBIDDEN:
            chain = [m]
            while parent[chain[-1]] is not None:
                chain.append(parent[chain[-1]])
            return list(reversed(chain))
        for n in imports(m, roots):
            if n not in parent:
                parent[n] = m
                q.append(n)
    return None


def topo_order(mods):
    mods = set(mods)
    order, state = [], {}

    def visit(m):
        if state.get(m) == 2:
            return
        state[m] = 1
        for n in imports(m, [SRC]):
            if n in mods and state.get(n) != 1:
                visit(n)
        state[m] = 2
        order.append(m)

    for m in sorted(mods):
        visit(m)
    return order


def declared(target):
    """Does the target `Module:decl` (or `test:Module:decl`) occur as a declaration in its file?"""
    roots = [SRC]
    if target.startswith('test:'):
        target, roots = target[5:], [TEST]
    mod, decl = target.split(':', 1)
    f = module_file(mod, roots)
    if f is None:
        return False
    text = open(f, encoding='utf-8').read()
    short = decl.split('.')[-1]
    return re.search(r'\\(?:func|lemma|sfunc|instance|record|class|cons)\s+' + re.escape(short) + r'(?:\.\{[^}]*\})?[\s{(:]', text) is not None \
        or re.search(r'^\s*\|\s*' + re.escape(short) + r'\b', text, re.M) is not None


def indexed(target):
    """Is the source target `Module:decl` in the symbol index?"""
    mod, decl = target.split(':', 1)
    short = decl.split('.')[-1]
    out = subprocess.run(['arend', '-ss', 'glob:' + short, '--json'], cwd=ROOT, capture_output=True, text=True).stdout
    try:
        data = json.loads(out[out.index('{'):])
    except ValueError:
        return False
    return any(r['module'] == mod and (r['name'] == decl or r['name'].endswith('.' + short) or r['name'] == short)
               for r in data.get('results', []))


_ss_cache = {}


def symbol_modules(name):
    if name not in _ss_cache:
        out = subprocess.run(['arend', '-ss', 'glob:' + name, '--json'], cwd=ROOT, capture_output=True, text=True).stdout
        try:
            _ss_cache[name] = json.loads(out[out.index('{'):]).get('results', [])
        except ValueError:
            _ss_cache[name] = []
    return _ss_cache[name]


def declaration_header(rest):
    """The signature of the declaration starting at `rest`: up to a top-level `=>`, `\\cowith`, `\\where` or `\\elim`."""
    depth = 0
    for i, ch in enumerate(rest):
        if ch in '({':
            depth += 1
        elif ch in ')}':
            depth -= 1
        elif depth == 0 and (rest.startswith('=>', i) or any(rest.startswith(k, i) for k in ('\\cowith', '\\where', '\\elim'))):
            return rest[:i]
    return rest


def unresolved_doc_links(path, mod):
    text = open(path, encoding='utf-8').read()
    imps = {}
    for m in re.finditer(r'^\\import\s+([\w.]+)(?:\s*\(([^)]*)\))?', text, re.M):
        imps[m.group(1)] = None if m.group(2) is None else {x.strip().split(' ')[0] for x in m.group(2).split(',')}
    bad = []
    for m in re.finditer(r'\{- \|.*?-\}|^[ \t]*-- \|[^\n]*', text, re.S | re.M):
        doc = re.sub(r'\$\$.*?\$\$|\$[^$]*\$', '', m.group(0), flags=re.S)
        rest = text[m.end():]
        header = declaration_header(rest)
        params = set()
        for b in re.findall(r'[({]\s*([^(){}:]+?)\s*:', header):
            params.update(b.split())
        for ref in re.findall(r'\{([A-Za-z][\w.\-*]*)\}', doc):
            parts = ref.split('.')
            if parts[0] in params:
                continue
            ok = any(r['module'] == mod or (r['module'] in imps and (imps[r['module']] is None or parts[0] in imps[r['module']]))
                     for r in symbol_modules(parts[-1]))
            if not ok:
                bad.append(f'{path}:{text[:m.start()].count(chr(10)) + 1}: unresolved doc link {{{ref}}}')
    return bad


def doc_comment_problems(path):
    problems, lines = [], open(path, encoding='utf-8').read().split('\n')
    for i, l in enumerate(lines):
        m = re.match(r'^([ \t]*)-- \|', l)
        if m and i + 1 < len(lines) and re.match(r'^' + re.escape(m.group(1)) + r'--(?! \|)', lines[i + 1]):
            problems.append(f'{path}:{i + 1}: multi-line doc comment written with -- |')
    text = '\n'.join(lines)
    for m in re.finditer(r'\{- \|(.*?)-\}', text, re.S):
        if '{-' in m.group(1) or '-}' in m.group(1):
            problems.append(f'{path}:{text[:m.start()].count(chr(10)) + 1}: nested comment delimiter inside a block doc comment')
    return problems


def consumers_closure(holes):
    """Declarations that transitively use one of the hole declarations (Module:decl), via `arend -fu --json`."""
    seen, q = set(), deque(holes)
    while q:
        t = q.popleft()
        out = subprocess.run(['arend', '-fu', t, '--json'], cwd=ROOT, capture_output=True, text=True).stdout
        try:
            data = json.loads(out[out.index('{'):])
        except ValueError:
            raise SystemExit('cannot parse -fu output for ' + t + ':\n' + out)
        for r in data.get('results', []):
            c = r['module'] + ':' + r['name']
            if c not in seen and c not in holes:
                seen.add(c)
                q.append(c)
    return seen


def errors_and_goals(output):
    return [m.group(0) for m in ERROR_RE.finditer(output)], {(m.group(1), m.group(2)) for m in GOAL_RE.finditer(output)}


def main():
    report = {'checks': {}}
    ok = True

    def record(name, passed, detail):
        nonlocal ok
        ok = ok and passed
        report['checks'][name] = {'passed': passed, 'detail': detail}
        print(('PASS ' if passed else 'FAIL ') + name + (': ' + str(detail) if not passed or isinstance(detail, str) else ''), flush=True)

    manifest = [json.loads(l) for l in open(MANIFEST, encoding='utf-8') if l.strip()]
    targets = [t for r in manifest for t in r['formal_targets']]
    src_targets = [t for t in targets if not t.startswith('test:')]
    modules = sorted({t.split(':', 1)[0] for t in src_targets})

    missing = [t for t in targets if not (declared(t) if t.startswith('test:') else indexed(t))]
    record('1 targets exist', not missing, missing or f'{len(targets)} targets')

    order = topo_order(modules)
    code, out = run(['arend', '--no-daemon', '-r', '-c'] + order)
    errs, goals = errors_and_goals(out)
    record('2 fresh double-checked typecheck', not errs, errs or f'{len(order)} modules, exit {code}')

    expected = {(r['formal_targets'][0].split(':', 1)[0], r['goal_name']) for r in manifest if r.get('status') == 'intentional-exercise-hole'}
    record('3 goals match registered holes', goals == expected,
           f'reported {sorted(goals)}, expected {sorted(expected)}' if goals != expected else f'{sorted(g for _, g in goals)}')

    code, out = run(['arend', '--no-daemon', '-t'])
    m = re.search(r'Tests completed: (\d+), Failed: (\d+)', out)
    record('4 test suite', bool(m) and m.group(2) == '0', m.group(0) if m else out[-500:])

    hits = {mod: import_closure_hits(mod, [SRC]) for mod in modules}
    bad = {k: v for k, v in hits.items() if v}
    record('5 axiom audit (import closure)', not bad, bad or f'{len(modules)} modules clean')

    holes = [r['formal_targets'][0] for r in manifest if r.get('status') == 'intentional-exercise-hole']
    closure = consumers_closure(holes)
    mismatches = []
    for r in manifest:
        if r.get('status') == 'intentional-exercise-hole':
            continue
        cond = any(t in closure for t in r['formal_targets'] if not t.startswith('test:'))
        expected_status = 'conditional-on-exercises' if cond else 'checked'
        if r['status'] != expected_status:
            mismatches.append((r['id'], r['status'], expected_status))
    report['hole_consumers'] = sorted(closure)
    record('6 dependency statuses', not mismatches, mismatches or f'{len(closure)} declarations depend on exercise holes')

    hit = import_closure_hits('ClassicalControl', [CONTROLS, SRC])
    record('7 control: axiom audit flags classical module', bool(hit), ' <- '.join(reversed(hit)) if hit else 'NOT FLAGGED')

    with tempfile.TemporaryDirectory() as d:
        os.makedirs(os.path.join(d, 'src'))
        with open(os.path.join(d, 'arend.yaml'), 'w') as f:
            f.write('langVersion: 1.12\nsourcesDir: src\ndependencies: [arend-lib]\n')
        with open(os.path.join(d, 'src', 'InvalidControl.ard'), 'w') as f:
            f.write('\\lemma invalid-control : 0 = {Nat} 1 => idp\n')
        code, out = run(['arend', '--no-daemon', '-L', os.path.dirname(ROOT), 'InvalidControl'], cwd=d)
        errs, _ = errors_and_goals(out)
        record('8 control: invalid proof is rejected', any('InvalidControl' in e for e in errs), errs or out[-300:])

    probs = [p for mod in modules for p in doc_comment_problems(module_file(mod, [SRC])) + unresolved_doc_links(module_file(mod, [SRC]), mod)]
    record('9 doc comments well formed', not probs, probs or f'{len(modules)} modules')

    if '--report' in sys.argv:
        with open(sys.argv[sys.argv.index('--report') + 1], 'w') as f:
            json.dump(report, f, indent=2)
    print('ACCEPTED' if ok else 'NOT ACCEPTED')
    sys.exit(0 if ok else 1)


if __name__ == '__main__':
    main()
