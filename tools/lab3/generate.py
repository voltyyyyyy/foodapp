"""Build the documentation-only Lab 3 package; never import application modules."""
from __future__ import annotations

import argparse
import ast
import hashlib
import json
import math
import re
import sys
import textwrap
from pathlib import Path
from xml.etree import ElementTree as ET
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

ROOT = Path(__file__).resolve().parents[2]
LOCAL_PACKAGES = ROOT / 'tools/.local/lab3-runtime'
if LOCAL_PACKAGES.is_dir():
    sys.path.insert(0, str(LOCAL_PACKAGES))

DOCS = ROOT / 'docs/labs/lab3'
SKELETON = DOCS / 'ai-todo-skeleton'
PDF = ROOT / 'output/pdf/foodbridge-lab3-report.pdf'
ARCHIVE = ROOT / 'output/lab3/foodbridge-ai-todo-skeleton.zip'
PREVIEW = ROOT / 'tmp/pdfs/lab3'
ANDROID = 'android/app/src/main/java/sg/foodbridge/navigator/'

# The manifest is the authoritative list: no secrets, datasets or binary assets.
FILES = {
    'README.md': 'Describe setup and the Android, API and staff portal boundaries.',
    '.gitignore': 'Exclude credentials, local data, toolchains and build outputs.',
    '.dockerignore': 'Allow only required backend and portal runtime inputs.',
    'compose.yaml': 'Configure database, API and maintenance without embedded secrets.',
    'android/build.gradle.kts': 'Declare the existing Android and Kotlin build plugins.',
    'android/settings.gradle.kts': 'Configure plugin repositories and app module.',
    'android/gradle.properties': 'Configure the Android build properties.',
    'android/app/build.gradle.kts': 'Configure SDK levels, Compose and existing dependencies.',
    'android/app/proguard-rules.pro': 'Declare release shrinking rules.',
    'android/app/src/main/AndroidManifest.xml': 'Declare activity, permissions and reminder receiver.',
    'android/app/src/main/assets/README.md': 'Supply the reviewed map style and required assets later.',
    'android/app/src/main/res/values/styles.xml': 'Define Android theme resources.',
    ANDROID + 'MainActivity.kt': 'Implement app shell, navigation and shared screens.',
    ANDROID + 'AppViewModel.kt': 'Define UiState and coordinate discovery, accounts and reporting.',
    ANDROID + 'Repository.kt': 'Handle API requests, Service data, local bookmarks and approved cache.',
    ANDROID + 'SessionStore.kt': 'Protect local session credentials using Android Keystore.',
    ANDROID + 'Reminders.kt': 'Implement ReminderStore and ReminderReceiver with account-separated local history.',
    ANDROID + 'AccountScreens.kt': 'Implement sign-in, registration and profile Compose functions.',
    ANDROID + 'FoodMap.kt': 'Implement map, foreground location and route presentation.',
    ANDROID + 'SearchSuggestions.kt': 'Implement address and organisation-name suggestions.',
    ANDROID + 'ServiceDetails.kt': 'Show details, evidence, separate schedules and contact actions.',
    ANDROID + 'Providers.kt': 'Show provider directory facts without assuming collection points.',
    ANDROID + 'UiComponents.kt': 'Provide shared presentation components and icons.',
    'backend/requirements.txt': 'Declare the existing pinned Python dependencies.',
    'backend/Dockerfile': 'Build the API and same-origin staff portal image.',
    'backend/app/__init__.py': 'Mark the backend application package.',
    'backend/app/main.py': 'Implement protected API, publication, import and atomic audit workflows.',
    'backend/app/domain.py': 'Define validated domain schemas, schedules, availability and search calculations.',
    'backend/app/db.py': 'Define Record, account/session/link, audit and compatibility tables.',
    'backend/app/auth.py': 'Implement accounts, roles, bounded sessions and identity/email flows.',
    'backend/app/onemap.py': 'Isolate OneMap geocoding and legacy routing adapter responsibilities.',
    'backend/app/google_routes.py': 'Isolate the active Google Routes integration and failures.',
    'backend/app/maintenance.py': 'Expire evidence/sessions and implement backups.',
    'backend/app/scheduler.py': 'Schedule maintenance and record operational status.',
    'backend/app/manage_admin.py': 'Provide explicit operator account creation and migration commands.',
    'backend/app/seed.py': 'Keep fictional fixtures separate from reviewed public data.',
    'backend/app/retire_demo.py': 'Retire fictional records through the established audited workflow.',
    'backend/seeds/README.md': 'Add only authorised reviewed data with provenance and verification notes.',
    'backend/tests/verify_backend.py': 'Check search, validation, visibility and atomic moderation using disposable data.',
    'backend/tests/verify_staff_portal.py': 'Check protected portal contracts and draft recovery.',
    'staff-portal/index.html': 'Define the browser administrator interface.',
    'staff-portal/portal.css': 'Style responsive, accessible staff workflows.',
    'staff-portal/portal.js': 'Implement authenticated queues, editing, retries and assessment.',
    'staff-portal/account/index.html': 'Define account verification and password-reset pages.',
    'staff-portal/account/account.js': 'Implement account-link actions without leaking credentials.',
    'tools/start-local.ps1': 'Start the existing stack while retaining persistent data.',
    'tools/setup-android.ps1': 'Prepare the local Android toolchain when needed.',
    'tools/build-android.ps1': 'Build Android with the configured API endpoint.',
    'tools/import-reviewed-seed.ps1': 'Import reviewed services through audited APIs.',
    'tools/import-foodconnect.ps1': 'Import reviewed provider directory data through audited APIs.',
    'docs/architecture/README.md': 'Document source mapping, requirements and architecture decisions.',
    'docs/setup/README.md': 'Document toolchain, accounts and external adapter configuration.',
    'docs/operations/README.md': 'Document staff workflows, deployment, expiry, backup and restoration.',
    'docs/verification/README.md': 'Record checks actually run and material evidence limits.',
}


def placeholder(path: str, todo: str) -> str:
    suffix = Path(path).suffix
    if suffix in {'.md', '.html', '.xml'}:
        return f'<!-- TODO: {todo} -->\n'
    if suffix in {'.kt', '.kts', '.js'}:
        return f'// TODO: {todo}\n'
    if suffix == '.css':
        return f'/* TODO: {todo} */\n'
    return f'# TODO: {todo}\n'


def tree_lines() -> list[str]:
    """Compact complete tree for the report; braces expand filenames in one folder."""
    return [
        'foodbridge-ai-todo-skeleton/',
        '  README.md, .gitignore, .dockerignore, compose.yaml',
        '  android/',
        '    build.gradle.kts, settings.gradle.kts, gradle.properties',
        '    app/build.gradle.kts, app/proguard-rules.pro',
        '    app/src/main/AndroidManifest.xml',
        '    app/src/main/assets/README.md, res/values/styles.xml',
        '    app/src/main/java/sg/foodbridge/navigator/',
        '      MainActivity.kt, AppViewModel.kt, Repository.kt',
        '      SessionStore.kt, Reminders.kt, AccountScreens.kt',
        '      FoodMap.kt, SearchSuggestions.kt, ServiceDetails.kt',
        '      Providers.kt, UiComponents.kt',
        '  backend/',
        '    requirements.txt, Dockerfile',
        '    app/__init__.py, main.py, domain.py, db.py, auth.py',
        '    app/onemap.py, google_routes.py, maintenance.py, scheduler.py',
        '    app/manage_admin.py, seed.py, retire_demo.py',
        '    seeds/README.md',
        '    tests/verify_backend.py, verify_staff_portal.py',
        '  staff-portal/',
        '    index.html, portal.css, portal.js',
        '    account/index.html, account.js',
        '  tools/',
        '    start-local.ps1, setup-android.ps1, build-android.ps1',
        '    import-reviewed-seed.ps1, import-foodconnect.ps1',
        '  docs/{architecture,setup,operations,verification}/README.md',
    ]


def recover_lab2() -> None:
    path = ROOT / 'reference/stitch_foodbridge_navigator/SC2006 Labs.docx'
    with ZipFile(path) as archive:
        root = ET.fromstring(archive.read('word/document.xml'))
    ns = {'w': 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
    paras = [''.join(t.text or '' for t in p.findall('.//w:t', ns))
             for p in root.findall('.//w:p', ns)]
    start = next(i for i, p in enumerate(paras) if p.startswith('# FoodBridge Navigator'))
    end = next(i for i in range(start, len(paras)) if paras[i] == '## Team opinion on Jetpack Compose')
    DOCS.joinpath('lab2-evidence.md').write_text(
        '# Recovered Lab 2 stack exercise\n\n'
        'Extracted from `reference/stitch_foodbridge_navigator/SC2006 Labs.docx`, '
        'first occurrence of the stack exercise. Text is evidence recorded in that document; '
        'the original chat, model identity and diagram revision were not independently verified. '
        'Referenced files such as `lab 1.md` are historical names, not current repository links.\n\n'
        + '\n\n'.join(paras[start:end]) + '\n', encoding='utf-8')


def diagram():
    """Print adaptation of the actual Jarbobo-authored class specifications."""
    from reportlab.graphics.shapes import Drawing, Rect, String, Line, Polygon
    drawing = Drawing(480, 345)
    positions = {}

    def box(key, x, y, item, width=136):
        positions[key] = (x, y, width, 43)
        drawing.add(Rect(x, y, width, 43, fillColor='#F0F5F4', strokeColor='#A6BAB5', radius=3))
        drawing.add(String(x + 7, y + 29, item['name'], fontName='Helvetica-Bold', fontSize=8, fillColor='#173F38'))
        detail = item.get('attributes') or textwrap.wrap(item['tooltip'], width=36)
        for i, line in enumerate(detail[:2]):
            drawing.add(String(x + 7, y + 18 - i * 8, line, fontName='Helvetica', fontSize=6.2, fillColor='#405B55'))

    def arrow(relation):
        x, y, w, h = positions[relation['from']]
        xx, yy, ww, hh = positions[relation['to']]
        if abs(y - yy) < 3:
            sx = x if xx < x else x + w
            ex = xx + ww if xx < x else xx
            sy, ey = y + h / 2, yy + hh / 2
        else:
            sx, ex = x + w / 2, xx + ww / 2
            sy, ey = (y, yy + hh) if yy < y else (y + h, yy)
        dashed = relation['kind'] == 'dependency'
        inheritance = relation['kind'] == 'inheritance'
        angle = math.atan2(ey - sy, ex - sx)
        ux, uy = math.cos(angle), math.sin(angle)
        size = 6 if inheritance else 4
        bx, by = ex - ux * size, ey - uy * size
        px, py = -uy * size * .48, ux * size * .48
        points = [ex, ey, bx + px, by + py, bx - px, by - py]
        endx, endy = (bx, by) if inheritance else (ex, ey)
        drawing.add(Line(sx, sy, endx, endy, strokeColor='#718A83', strokeWidth=.8,
                         strokeDashArray=[3, 2] if dashed else None))
        drawing.add(Polygon(points, fillColor='white' if inheritance else '#718A83',
                            strokeColor='#718A83' if inheritance else None))

    layouts = [
        ('android-classes.jarbobo', 'JARBOBO / ANDROID COORDINATION', 332, [274, 203]),
        ('backend-classes.jarbobo', 'JARBOBO / BACKEND VALIDATION AND PERSISTENCE', 181, [124, 44]),
    ]
    for filename, title, title_y, rows in layouts:
        spec = read_jarbobo(DOCS / filename)
        drawing.add(String(0, title_y, title, fontSize=8, fontName='Helvetica-Bold', fillColor='#173F38'))
        for i, item in enumerate(spec['classes']):
            box(item['id'], (i % 3) * 170, rows[i // 3], item)
        for relation in spec['relations']:
            arrow(relation)
    drawing.add(String(0, 19, 'Solid: association. Hollow triangle: inheritance. Dashed: dependency or logical JSON/ID mapping.',
                       fontSize=7, fillColor='#405B55'))
    drawing.add(String(0, 5, 'Print layout from saved Jarbobo class specs. Source links and relation details remain in the editable files.',
                       fontSize=7, fillColor='#405B55'))
    return drawing


def read_jarbobo(path: Path) -> dict:
    content = path.read_text(encoding='utf-8-sig')
    content = '\n'.join(line for line in content.splitlines() if not line.startswith('//'))
    return json.loads(content)['versions'][-1]


def make_pdf() -> None:
    from html import escape
    from reportlab.lib import colors
    from reportlab.lib.enums import TA_LEFT
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import ParagraphStyle
    from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak, Preformatted
    PDF.parent.mkdir(parents=True, exist_ok=True)
    body = ParagraphStyle('Body', fontName='Helvetica', fontSize=9.3, leading=13.1,
                          textColor=colors.HexColor('#273C37'), spaceAfter=8, alignment=TA_LEFT)
    styles = {
        1: ParagraphStyle('Title', parent=body, fontName='Helvetica-Bold', fontSize=23, leading=27, spaceAfter=12),
        2: ParagraphStyle('Heading', parent=body, fontName='Helvetica-Bold', fontSize=16, leading=20, spaceAfter=12),
        3: ParagraphStyle('Subheading', parent=body, fontName='Helvetica-Bold', fontSize=11, leading=15, spaceBefore=6, spaceAfter=7),
    }
    mono = ParagraphStyle('Tree', fontName='Courier', fontSize=7.1, leading=9.6,
                          textColor=colors.HexColor('#173F38'), backColor=colors.HexColor('#F0F5F4'),
                          borderPadding=8, spaceBefore=6)

    def footer(canvas, doc):
        canvas.saveState()
        width, height = A4
        canvas.setFillColor(colors.HexColor('#173F38'))
        canvas.rect(43, height - 30, width - 86, 3, fill=1, stroke=0)
        canvas.setFont('Helvetica', 7)
        canvas.drawString(43, 29, 'FOODBRIDGE NAVIGATOR  /  LAB 3  /  TECHNOLOGY STACK REVIEW')
        canvas.drawRightString(width - 43, 29, str(doc.page))
        canvas.restoreState()

    flow = []
    for chunk in DOCS.joinpath('report.md').read_text(encoding='utf-8').split('\n\n'):
        text = chunk.strip()
        if not text:
            continue
        if text == '<!-- pagebreak -->':
            flow.append(PageBreak())
        elif text == '<!-- class-diagram -->':
            flow.extend([Spacer(1, 4), diagram(), Spacer(1, 8)])
        elif text == '<!-- skeleton-tree -->':
            flow.append(Preformatted('\n'.join(tree_lines()), mono))
        elif text.startswith('#'):
            match = re.match(r'^(#{1,3}) (.*)$', text)
            if not match:
                raise ValueError(f'Unsupported heading: {text}')
            flow.append(Paragraph(escape(match[2]), styles[len(match[1])]))
        else:
            escaped = escape(text.replace('\n', ' '))
            # Keep URLs clickable without introducing long unbreakable runs.
            escaped = re.sub(r'https://[^\s<]+', lambda m: '<link href="' + m[0].rstrip('.') + '">' + m[0] + '</link>', escaped)
            flow.append(Paragraph(escaped, body))
    SimpleDocTemplate(str(PDF), pagesize=A4, rightMargin=43, leftMargin=43,
                      topMargin=48, bottomMargin=46,
                      title='FoodBridge Navigator - Lab 3 AI-based project initialisation',
                      author='FoodBridge course project', pageCompression=1).build(flow, onFirstPage=footer, onLaterPages=footer)


def generate() -> None:
    DOCS.mkdir(parents=True, exist_ok=True)
    for name, todo in FILES.items():
        target = SKELETON / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(placeholder(name, todo), encoding='utf-8', newline='\n')
    ARCHIVE.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(ARCHIVE, 'w', compression=ZIP_DEFLATED) as archive:
        for name in sorted(FILES):
            info = ZipInfo('foodbridge-ai-todo-skeleton/' + name, date_time=(2026, 10, 18, 0, 0, 0))
            info.compress_type = ZIP_DEFLATED
            archive.writestr(info, (SKELETON / name).read_bytes())
    recover_lab2()
    make_pdf()
    import pymupdf
    PREVIEW.mkdir(parents=True, exist_ok=True)
    with pymupdf.open(PDF) as document:
        for i, page in enumerate(document):
            page.get_pixmap(matrix=pymupdf.Matrix(1.3, 1.3)).save(PREVIEW / f'page-{i + 1}.png')


def verify() -> None:
    actual = {p.relative_to(SKELETON).as_posix() for p in SKELETON.rglob('*') if p.is_file()}
    assert actual == set(FILES), f'Skeleton manifest mismatch: {actual ^ set(FILES)}'
    for name, todo in FILES.items():
        content = (SKELETON / name).read_text(encoding='utf-8')
        assert content == placeholder(name, todo), f'Non-TODO content: {name}'
        if name.endswith('.py'):
            assert not ast.parse(content).body, f'Executable Python in {name}'
    with ZipFile(ARCHIVE) as archive:
        expected = {'foodbridge-ai-todo-skeleton/' + name for name in FILES}
        assert set(archive.namelist()) == expected
        assert archive.testzip() is None
        for name in FILES:
            assert archive.read('foodbridge-ai-todo-skeleton/' + name) == (SKELETON / name).read_bytes()
    from pypdf import PdfReader
    reader = PdfReader(PDF)
    assert len(reader.pages) == 6, f'Expected six pages, got {len(reader.pages)}'
    for i, page in enumerate(reader.pages):
        assert len(page.extract_text()) > 500, f'Unexpected empty page {i + 1}'
    # Read declarations only: importing backend models would create data directories/engines.
    declarations = {}
    for path in (ROOT / 'backend/app').glob('*.py'):
        for node in ast.walk(ast.parse(path.read_text(encoding='utf-8-sig'))):
            if isinstance(node, ast.ClassDef):
                declarations[node.name] = path
    android_text = '\n'.join(p.read_text(encoding='utf-8-sig') for p in (ROOT / ANDROID).glob('*.kt'))
    aliases = {'AndroidService': 'Service', 'BackendService': 'Service'}
    mermaid = DOCS.joinpath('class-diagram.mmd').read_text(encoding='utf-8')
    names = set(re.findall(r'^    class (\w+)', mermaid, re.M))
    names.update(re.findall(r'^    (\w+) (?:-->|<\|--|\.\.>)', mermaid, re.M))
    names.update(re.findall(r'(?:-->|<\|--|\.\.>) (\w+)', mermaid))
    names.update(re.findall(r'"[^"]+"\s+(\w+)\s*:', mermaid))
    for name in names:
        name = aliases.get(name, name)
        assert name in declarations or re.search(r'\bclass\s+' + re.escape(name) + r'\b', android_text), f'Unknown class {name}'
    for filename in ['android-classes.jarbobo', 'backend-classes.jarbobo']:
        spec = read_jarbobo(DOCS / filename)
        class_ids = {item['id'] for item in spec['classes']}
        for item in spec['classes']:
            path = Path(item['file'])
            assert path.is_file(), f'Broken Jarbobo reference: {path}'
            lines = path.read_text(encoding='utf-8-sig').splitlines()
            assert re.search(r'\bclass\s+' + re.escape(item['name']) + r'\b', lines[item['line'] - 1]), f'Stale class reference: {item["name"]}'
            assert item.get('tooltip'), f'Missing class explanation: {item["name"]}'
        for relation in spec['relations']:
            assert relation['from'] in class_ids and relation['to'] in class_ids
    for doc in [DOCS / 'README.md', ROOT / 'docs/README.md', ROOT / 'tools/README.md']:
        for href in re.findall(r'\]\(([^)]+)\)', doc.read_text(encoding='utf-8')):
            if '://' not in href and not href.startswith('#'):
                assert (doc.parent / href.split('#')[0]).exists(), f'Broken link in {doc}: {href}'
    # Basic rendered-layout check; human visual inspection remains necessary.
    import pymupdf
    with pymupdf.open(PDF) as document:
        for i, page in enumerate(document):
            for block in page.get_text('dict')['blocks']:
                if block['type'] == 0:
                    x0, y0, x1, y1 = block['bbox']
                    assert x0 >= 35 and x1 <= page.rect.width - 35 and y0 >= 32 and y1 <= page.rect.height - 20, f'Text outside page margins on page {i + 1}'
    print(f'PASS: {len(FILES)} TODO-only files; exact ZIP parity; six PDF pages; class names and links resolve.')
    print(f'ZIP SHA256: {hashlib.sha256(ARCHIVE.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify', action='store_true', help='Validate existing outputs without rewriting them.')
    args = parser.parse_args()
    if not args.verify:
        generate()
    verify()
