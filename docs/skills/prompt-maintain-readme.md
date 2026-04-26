# Prompt: Create or update `README.md` (standard structure)

Use this document as the **system/user prompt** when asking an assistant (or yourself) to **create** a new root `README.md` or **refresh** an existing one so it stays accurate and easy to navigate.

---

## Goal

Produce a single **root `README.md`** that:

- Onboards a new developer in minutes (what it is, how to run, how to test).
- Points to deeper docs instead of duplicating them.
- Matches the **standard section order** for this repository (below) unless a section is truly N/A (then omit or mark briefly).
- Uses **real** commands, ports, env vars, and paths from the current tree (verify against `package.json`, `pom.xml`, `docker compose`, `scripts/`, `docs/`).
- If the project tracks **AI-assisted work**, keep the **AI-assisted development** table in sync when those areas change materially.

---

## Instructions for the executor

1. **Read the repository** (or the relevant subtree): services, `docs/`, `docs/qa-instruction.md`, `docker compose` / `compose.yaml`, `scripts/*.sh`, `tasks/` for scope and this prompt.
2. **Diff** the current `README.md` against reality: fix broken links, wrong ports, outdated test commands, missing services.
3. **Preserve** project-specific value: scripts table, **Demo (first sight)**, **Service URLs** (priority-ordered), **compact Tests** with link to **`docs/qa-instruction.md#tests-and-coverage-detail`**, **Roadmap** (P0 / P1 / P2 / **Future architecture** with stable **#** ids, **Scope** column, and legend), `scripts/run-all.sh` + **CNT-1001** / **`docs/seed.sql`**, import **multipart** field `file` (in URLs table + QA). **Do not** paste long import walkthrough, JaCoCo tables, or per-service run details into the README (those live in **`docs/qa-instruction.md`**) unless the user asks to inline them again.
4. **Do not** replace `README.md` with a generic template that drops repo-specific content unless the user asked for a full rewrite.
5. **Links**: repo-relative for files (e.g. `[docs/qa-instruction.md](docs/qa-instruction.md)` from the repository root in `README.md`). Full URLs for **localhost** in tables only.
6. **Markdown**: avoid decorative emoji unless the team standard uses them. **Roadmap** status: keep markers consistent (e.g. ⬜ Todo, ✅ Done).
7. **AI-assisted table** (if present after **Tests**): do not remove without user request. When you change ownership of an area, prompt the user to update **Approx. %** and **My actions**; default placeholders are **60%** and *Reviewing* when unknown.

---

## `README.md` section order (this monorepo)

Use these **level-2 headings** in order. Subsections and tables below mirror the current root `README.md` for **payment + import** services.

| # | Section | What it must contain |
|---|---------|------------------------|
| 1 | **Title + one-paragraph summary** | `H1` name; stack; idempotency / import. **First line** clarifies: **`README`** = **short demo map**; **`docs/qa-instruction.md`** = full runbook. |
| 2 | **Prerequisites** | JDK, Maven, Node, Docker, Bash. |
| 3 | **Bash scripts (`scripts/`)** | `sed` / CRLF note. **Table** of all `scripts/*.sh` (incl. `cleanup`, `smoke-test`). **Quick start** + **:3000 refused** one-liner. |
| 4 | **Demo (first sight)** | 3 bullets: `build` + `run-all`; open **Health → Import UI → Swagger**; **CNT-1001** + link **`docs/qa-instruction.md`** (anchors `#qa-demo-walkthrough`, `#import-a-file`). **No** long step lists here. |
| 5 | **Service URLs (local defaults)** | **Priority-ordered** table: health → import → Swagger → example API → service roots → metrics. |
| 6 | **Tests** | `mvn test` + `npm test` commands; one line: integration profile + JaCoCo path; **link** **`docs/qa-instruction.md#tests-and-coverage-detail`** for depth (no JaCoCo table in README by default). |
| 7 | **AI-assisted development** | Optional transparency table (areas, %, actions). |
| 8 | **Roadmap** | Legend (**Shipped** / **Planned** / **Future architecture**); P0, **Future architecture** (e.g. **#004**), P1, P2; preserve all **#** ids. |
| 9 | **Documentation** | `docs/`, `samples/`, Postman, **`docs/qa-instruction.md`** (full local manual), **`tasks/prompt-maintain-readme.md`**. |

**Cross-cutting topics** (weave in where they fit, do not necessarily need their own H2): **idempotency**, **import field `file`**, **seed** / Flyway, **WSL2** and **localhost:3000** (import is a second process), **mvn from `payment-service/`** (no root POM).

---

## Quality checklist (before finishing)

- [ ] No broken internal links (including `README` → `tasks/`, `docs/`, `samples/`, `postman/`).
- [ ] Java / Node / Spring versions match `pom.xml` / `package.json` / project reality.
- [ ] **Tests:** README has commands + link to **QA** for JaCoCo / Surefire / coverage tips; **`docs/qa-instruction.md`** has the long **Tests and coverage (detail)** section.
- [ ] **Import** examples use **`file`**; **Service URLs** include **`/payments/import`**; seed path **`docs/seed.sql`** and contract **CNT-1001** are consistent.
- [ ] **Roadmap** rows and **#** ids match the latest scope; **Scope** column + **Future architecture** section for evolution items (e.g. **#004**); P0 “done” items stay **✅** if still true.
- [ ] If the **AI-assisted development** table exists, rows cover the agreed **areas**; **%** and **My actions** are plausible or left as explicit placeholders to edit.
- [ ] **Import** walkthrough, manual seed, Postman, `curl`, per-service `mvn`/`npm` run, **stop/cleanup** — in **`docs/qa-instruction.md`**, not duplicated in README.
- [ ] Long WSL/ops detail stays in **`docs/qa-instruction.md`**; README only summarizes and links.
- [ ] This file **`tasks/prompt-maintain-readme.md`** remains listed under **Documentation** so maintainers can find the prompt.

---

## Example one-shot user message (paste with the goal + instructions)

> Update the root `README.md` to match `tasks/prompt-maintain-readme.md`. Keep it **short** (demo, scripts, Service URLs, compact Tests, AI-assisted table, Roadmap, Documentation). Put or keep **long** import steps, JaCoCo table, and per-service run instructions in **`docs/qa-instruction.md`**, not the README.

---

## File location

This prompt is stored at: **`tasks/prompt-maintain-readme.md`**

When versioning prompts, add a suffix (e.g. `prompt-maintain-readme-v2.md`) rather than overwriting, if you need history.
