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

1. **H2 match**: If the section title matches an `## H2` heading in the file, return that H2's content (up to the next H2 or end of file).

2. **H1 match / fallback (aggregate)**: If no H2 matches, return content from the start of the file (after H1), **excluding** any H2 sections that have their own separate section definitions (sibling sections referencing the same file).

### CRITICAL: Avoid Duplicate Section Rendering

**Problem**: When multiple `Section` entries reference the same markdown file, a section whose title matches the file's H1 heading will aggregate all H2 content — EXCEPT H2 sections that are separately defined as their own `Section` entries. If a new H2 is added to the markdown but not registered as a separate section, it will be included in the H1-matching section's content.

**Rules to prevent duplicates**:

1. **Each section title must match exactly one H2 heading** in the markdown file. The title matching uses `titlesMatch()` (case-insensitive, contains, keyword overlap).

2. **When multiple sections reference the same file**, every H2 that should be rendered separately MUST have its own `Section` entry. The "primary" section (matching H1 or acting as catch-all) will automatically exclude H2 sections that have their own entries.

3. **Never use the H1 title as a section title** if you also want to split H2s into separate sections AND want precise control over which H2s are included in the primary section. The H1-matching section acts as a catch-all for un-mapped H2s.

4. **After adding a new H2 to a markdown file**, check `getSectionGroups()` to verify it will be rendered correctly — either as part of an existing aggregate section or as its own new `Section` entry.

### Example: Correct Multi-Section File

```java
// 13-bantuan-ai.md has H1 "Bantuan AI untuk Pencatatan Transaksi"
// and H2s: "Cara Kerja", "Setup Autentikasi", ..., "Publikasi Laporan Analisis", ...

new Section("bantuan-ai", "Bantuan AI untuk Pencatatan Transaksi", "13-bantuan-ai.md", ...),
new Section("publikasi-analisis", "Publikasi Laporan Analisis", "13-bantuan-ai.md", ...)
```

Result:
- Section "Bantuan AI untuk Pencatatan Transaksi" renders ALL H2s EXCEPT "Publikasi Laporan Analisis"
- Section "Publikasi Laporan Analisis" renders only that specific H2

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
5. Run `UserManualGenerator.main()` locally to verify output
6. Check the generated HTML for duplicate content before committing
