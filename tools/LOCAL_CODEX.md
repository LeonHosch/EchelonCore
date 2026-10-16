# Local manager for Codex CLI

The local Ollama model reads a specification, selects one task at a time, and
passes each task to the authenticated Codex CLI. Codex can edit both EchelonCore
and the sibling `../CrazyWorldProgression` project. The loop stops after five
Codex calls by default, when the local model reports completion, or on an error.

From the EchelonCore directory in PowerShell:

```powershell
ollama list
python tools/local_codex.py "C:\path\to\spec.md"
python tools/local_codex.py "C:\path\to\spec.md" --execute --max-steps 5
```

You can add a goal after the file path, for example `"Implement the server
features first"`. The first command previews the initial task. The second
shows that task and asks once before running the bounded loop. Codex remains
inside its `workspace-write` sandbox. Review the changes and the terminal
output when it stops; the local model's completion judgment is advisory.

For a text-based PDF, install the extraction package once:

```powershell
python -m pip install pypdf
python tools/local_codex.py "C:\path\to\spec.pdf" --execute
```

Scanned PDFs need OCR or a Markdown export first. If Ollama is not running,
start the Ollama app or run `ollama serve` in another terminal. This launcher
uses the installed `qwen3:8b` model by default; select another with `--model`.
