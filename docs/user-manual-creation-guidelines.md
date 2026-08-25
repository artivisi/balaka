# User Manual Creation Guidelines

Guidelines for writing and maintaining user manual markdown files and their corresponding section definitions in `UserManualGenerator.java`.

## Architecture

```
docs/user-manual/*.md          → Markdown source files
UserManualGenerator.java       → Section definitions + HTML generation
ScreenshotCapture.java         → Screenshot definitions (PageDefinition)
Functional tests               → Auto-capture screenshots via takeManualScreenshot()
```

The generator reads markdown files, extracts specific H2 sections based on section definitions, and produces a single-page HTML manual.

## Section Extraction Rules

`extractSectionContent()` extracts content from a markdown file based on the section title. It applies these rules in order:

1. **Single section for the file**: If no other `Section` entry references the same markdown file, the whole file is returned. H2 registration is irrelevant — nothing can be dropped.

2. **H2 match**: If the section title matches an `## H2` heading **exactly** (case-insensitive, trimmed), return that H2's content (up to the next H2 or end of file).

3. **Aggregate**: If no H2 matches the title, return content from the start of the file (after H1), **excluding** any H2 that has its own `Section` entry.

### Title Matching Is Exact

`titlesMatch()` is a case-insensitive equality check. It previously also accepted substring and keyword-overlap matches, which silently mis-assigned content: the section titled `Transaksi PPh` matched the H2 `## Transaksi PPN` because both reduce to the single significant word *transaksi*, so the entire PPh chapter was replaced by a second copy of PPN and never rendered. Forty-one H2 sections were lost this way across ten files.

**Register every H2 with its exact heading text.** If you rename an H2 in the markdown, rename the `Section` title to match in the same commit.

### CRITICAL: Every H2 in a Multi-Section File Must Be Registered

**Problem**: When two or more `Section` entries reference the same markdown file and *all* of them match an H2, there is no aggregate. Any H2 without its own entry is then rendered nowhere — no error, no warning, the content simply never reaches the published page.

**Rules**:

1. **Each section title must exactly equal one H2 heading** in the markdown file, or be an intentional aggregate that matches no H2.

2. **When multiple sections reference the same file**, every H2 MUST have its own `Section` entry unless the file has an aggregate section to sweep it up.

3. **Section ids must be globally unique** — `all.html` concatenates every group, so a reused id produces a duplicate anchor. Prefix with the group id when a natural slug is already taken (`pendidikan-laporan-keuangan`).

4. **After adding a new H2 to a markdown file**, add the matching `Section` entry in `getSectionGroups()` in document order. `UserManualSectionCoverageTest` fails the build if you forget.

### Example: Correct Multi-Section File

```java
// 04-perpajakan.md H2s, in document order — every one registered with its exact text
new Section("jenis-pajak", "Jenis Pajak di Indonesia", "04-perpajakan.md", List.of()),
new Section("transaksi-ppn", "Transaksi PPN", "04-perpajakan.md", List.of(...)),
new Section("transaksi-pph", "Transaksi PPh", "04-perpajakan.md", List.of(...)),
...
new Section("perpajakan-tips-kepatuhan", "Tips Kepatuhan", "04-perpajakan.md", List.of())
```

Result: each section renders exactly its own H2. Nothing is duplicated, nothing is dropped.

### Example: Single-Section File

```java
// 12-lampiran-glosarium.md — entire file rendered as one section
new Section("glosarium", "Glosarium", "12-lampiran-glosarium.md", ...)
```

Result: Entire file content rendered (no H2 exclusions needed since no siblings).

## Title Matching Rules

`titlesMatch()` uses flexible matching:

1. **Exact** (case-insensitive): "Publikasi Laporan Analisis" == "publikasi laporan analisis"
2. **Contains**: "Konsep Dasar Akuntansi" contains/is-contained-by the H2 title
3. **Keyword overlap**: All significant words (length >= 4) from the shorter title must appear in the longer title

Be careful with short or generic titles that might accidentally match multiple H2 headings.

## Adding Screenshots

1. Define `PageDefinition` in `ScreenshotCapture.java` with id, name, description
2. Call `takeManualScreenshot("section/screenshot-id")` in functional test
3. Add the screenshot id to the `Section` screenshots list in `UserManualGenerator.java`
4. Reference in markdown: `![Alt text](screenshots/section/screenshot-id.png)`

## Markdown File Conventions

- H1 (`#`) is the file title — stripped during extraction
- H2 (`##`) are the section boundaries used for extraction
- H3+ are subsections within an H2 — included with their parent H2
- `---` horizontal rules are visual separators, not section boundaries
- Image references use relative paths: `screenshots/category/name.png`

## Checklist: Adding a New Section

1. Write content in the appropriate `docs/user-manual/*.md` file
2. Add `Section` entry in `getSectionGroups()` with title matching the H2 heading
3. If the markdown file already has other sections defined, verify no title conflicts
4. Add screenshot definitions if needed (ScreenshotCapture + functional test)
5. **Add the test class to `publish-manual.yml`** workflow's `-Dtest=` list so screenshots are captured in CI (see below)
6. Run `UserManualGenerator.main()` locally to verify output
7. Check the generated HTML for duplicate content before committing

## CI Screenshot Generation

The `publish-manual.yml` workflow captures screenshots by running specific functional tests. If your new section includes screenshots captured by a functional test, you **must** add that test class to the `-Dtest=` parameter in the workflow:

```yaml
# .github/workflows/publish-manual.yml
run: ./mvnw test -Dtest="Service*Test,...,YourNewTest"
```

Without this, screenshots will appear locally but show "Screenshot belum tersedia" on the live site at artivisi.com/balaka/.
