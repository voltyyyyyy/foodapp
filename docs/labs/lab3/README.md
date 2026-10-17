# Lab 3: AI-based project initialisation

This package reconstructs the initialisation exercise from the existing FoodBridge implementation and the updated SRS supplied on 18 October 2026. It does not claim that the finished application was generated from the TODO skeleton.

| Material | Purpose |
| --- | --- |
| [Report source](report.md) | Prompts, current AI responses, comparison and critical evaluation |
| [PDF report](../../../output/pdf/foodbridge-lab3-report.pdf) | Short report for section 3.3.5 |
| [TODO-only ZIP](../../../output/lab3/foodbridge-ai-todo-skeleton.zip) | Separate AI initialisation output for section 3.3.3; not the full application |
| [Expanded TODO skeleton](ai-todo-skeleton/) | Comment-only files mirrored from the implemented source boundaries |
| [Architecture and source map](architecture.md) | Domain concepts, actual classes/modules, storage and SRS differences |
| [Class diagram source](class-diagram.mmd) | Editable Mermaid overview of selected implemented classes |
| [Jarbobo Android classes](android-classes.jarbobo) | Source-linked Android coordination class diagram |
| [Jarbobo backend classes](backend-classes.jarbobo) | Source-linked backend validation/persistence class diagram |
| [Recovered Lab 2 evidence](lab2-evidence.md) | Prompt and response extracted from the archived course document |

The full implemented application remains in the existing `android/`, `backend/` and `staff-portal/` directories. No runtime files are relocated or refactored by this exercise. The separate lab package provides the organisational improvement without risking imports, deployment paths, database compatibility or existing Android edits.

## Rebuild and verify

From the repository root, with Python packages `reportlab`, `PyMuPDF` and `pypdf` available:

```powershell
python tools/lab3/generate.py
python tools/lab3/generate.py --verify
git diff --check
```

The generator also recognises packages installed in ignored `tools/.local/lab3-runtime/`. It renders PDF previews under `tmp/pdfs/lab3/`. PDF pages use the same text as `report.md`; page 3 adapts the saved Jarbobo specifications into a compact print layout. It is not a screenshot of Jarbobo's editor layout. Open the `.jarbobo` files with the Jarbobo extension for source links, attributes and relationship details. Jarbobo created these files successfully, but its live editor panel was unavailable in this session.

The generator verifies that every skeleton file contains only TODO comments, Python placeholders have no executable AST nodes, the ZIP exactly matches the expanded skeleton, diagram class names exist in source, Markdown links resolve, and the report has six readable pages. It does not connect to the application, import backend modules, read credentials or include local data in the ZIP.

## Evidence limits

The archived document records a Lab 2 prompt and response but does not establish the model identity, settings or complete original diagram revision. The current prompts are replayable reconstructions, and the current responses were prepared during this repository review, not in a separately executed fresh chat. The full SRS was supplied in the conversation; the report includes a focused input summary rather than reproducing it in full. Review and replay the prompts in a fresh chat if the course requires evidence of that procedure.
