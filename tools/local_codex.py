"""Use local Ollama to coordinate bounded Codex CLI work from a specification."""

import argparse
import json
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CWP = ROOT.parent / "CrazyWorldProgression"
OLLAMA = "http://127.0.0.1:11434"


def api(path, payload=None):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(OLLAMA + path, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=600) as response:
        return json.load(response)


def generate(model, prompt, structured=False):
    payload = {"model": model, "prompt": prompt, "stream": False, "options": {"temperature": 0.1}}
    if structured:
        payload["format"] = "json"
    response = api("/api/generate", payload).get("response", "").strip()
    if not response:
        raise ValueError("Ollama returned an empty response")
    return json.loads(response) if structured else response


def read_spec(path):
    if path.suffix.lower() in (".md", ".txt"):
        return path.read_text(encoding="utf-8-sig")
    if path.suffix.lower() == ".pdf":
        try:
            from pypdf import PdfReader
        except ImportError as exc:
            raise RuntimeError("PDF support requires: python -m pip install pypdf") from exc
        content = "\n\n".join(f"[Page {i+1}]\n{page.extract_text() or ''}" for i, page in enumerate(PdfReader(str(path)).pages))
        if not content.strip():
            raise RuntimeError("PDF has no extractable text; OCR it or export Markdown first")
        return content
    raise RuntimeError("Specification must be .md, .txt, or .pdf")


def summarize(model, content):
    chunks = [content[i:i+12000] for i in range(0, len(content), 12000)]
    summaries = []
    for i, chunk in enumerate(chunks, 1):
        print(f"Summarizing specification section {i}/{len(chunks)}...", flush=True)
        summaries.append(generate(model, "Summarize all implementation requirements, constraints, acceptance criteria, and exact identifiers in this section.\n\n" + chunk))
    combined = "\n\n".join(f"Section {i+1}: {part}" for i, part in enumerate(summaries))
    for _ in range(3):
        if len(combined) <= 24000:
            return combined
        parts = [combined[i:i+12000] for i in range(0, len(combined), 12000)]
        combined = "\n\n".join(
            generate(model, "Compress this requirement summary. Preserve all exact identifiers, constraints, and acceptance criteria.\n\n" + part)
            for part in parts
        )
    raise RuntimeError("Specification summary remains too long; split the specification into smaller files")


def decide(model, goal, summary, outcomes):
    prompt = (
        "You coordinate Codex CLI work on two Minecraft Fabric projects: EchelonCore and sibling "
        "CrazyWorldProgression (CWP). Return JSON with keys done (boolean), task (string), and "
        "reason (string). Choose ONE concrete next implementation or validation task in dependency "
        "order. Do not claim completion until Codex results establish the requirements and checks. "
        "If blocked, set done=true and explain the blocker. Never discard user edits.\n\n"
        f"Goal: {goal}\n\nSpecification summary:\n{summary}\n\nCodex results:\n"
        + ("\n\n".join(outcomes[-5:]) or "None")
    )
    decision = generate(model, prompt, structured=True)
    if not isinstance(decision, dict) or not isinstance(decision.get("done"), bool):
        raise ValueError("Ollama returned an invalid decision")
    if not decision["done"] and not isinstance(decision.get("task"), str):
        raise ValueError("Ollama did not provide a task")
    return decision


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("spec", type=Path, help="Markdown, text, or text-based PDF specification")
    parser.add_argument("task", nargs="*", help="Additional goal or priority")
    parser.add_argument("--model", default="qwen3:8b", help="Installed Ollama model")
    parser.add_argument("--max-steps", type=int, default=5, help="Maximum Codex calls")
    parser.add_argument("--execute", action="store_true", help="Run Codex after showing the first step")
    args = parser.parse_args()
    spec = args.spec.resolve()
    if not spec.is_file() or not CWP.is_dir() or args.max_steps < 1:
        parser.error("Check the specification path, sibling CWP project, and --max-steps")
    try:
        content = read_spec(spec)
        installed = [item["name"] for item in api("/api/tags").get("models", [])]
        if args.model not in installed:
            parser.error(f"Model {args.model!r} unavailable; installed: {', '.join(installed) or 'none'}")
        if args.execute and shutil.which("codex") is None:
            parser.error("Codex CLI is not on PATH")
        goal = " ".join(args.task) or "Implement the complete specification across both projects"
        summary = summarize(args.model, content)
        outcomes = []
        decision = decide(args.model, goal, summary, outcomes)
        if decision["done"]:
            print("Manager stopped:", decision.get("reason", ""))
            return
        print("\nFirst Codex step:\n" + decision["task"] + "\n", flush=True)
        if not args.execute:
            print("Preview only. Add --execute to start the loop.")
            return
        if input("Start the Codex loop? [y/N] ").strip().lower() not in ("y", "yes"):
            print("Cancelled.")
            return
        with tempfile.TemporaryDirectory(prefix="local-codex-") as run_dir:
            for step in range(1, args.max_steps + 1):
                print(f"\nCodex step {step}/{args.max_steps}: {decision['task']}\n", flush=True)
                result_file = Path(run_dir) / f"step-{step}.txt"
                prompt = (
                    f"{decision['task']}\n\nRead the complete specification at {spec} directly; "
                    "the manager summary may omit details. Work in EchelonCore and ../CrazyWorldProgression "
                    "as needed. Preserve existing user changes. Run meaningful checks and report what "
                    "passed, failed, and remains unfinished."
                )
                command = ["codex", "exec", "--cd", str(ROOT), "--add-dir", str(CWP),
                           "--sandbox", "workspace-write", "--output-last-message", str(result_file), "-"]
                code = subprocess.run(command, input=prompt, text=True, cwd=ROOT, check=False).returncode
                result = result_file.read_text(encoding="utf-8") if result_file.exists() else "No final message"
                outcomes.append(f"Step {step}; exit {code}; task: {decision['task']}\nResult: {result[-12000:]}")
                if code:
                    print(f"Codex exited {code}; stopping for inspection.")
                    sys.exit(code)
                decision = decide(args.model, goal, summary, outcomes)
                if decision["done"]:
                    print("\nManager stopped:", decision.get("reason", ""))
                    return
            print("\nStep limit reached. Review changes and rerun with a focused goal if needed.")
    except (OSError, ValueError, RuntimeError) as exc:
        parser.exit(1, f"Setup or model error: {exc}\n")


if __name__ == "__main__":
    main()
