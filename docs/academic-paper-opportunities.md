# Academic Paper Opportunities

**Author**: Endy Muhardin (Lecturer)
**Target**: SINTA 1-3 journals
**Requirement**: 1-2 papers annually
**Date**: 2026-02-13

---

## Strongest Paper Topics for SINTA 1-3

### 1. **Security & Compliance** (Most Ready)

**Title**: "Implementing GDPR/UU PDP Compliance in Indonesian SME Accounting Software: A DevSecOps Approach"

**Key contributions**:
- Field-level encryption architecture (AES-256-GCM)
- Automated security testing pipeline (SpotBugs, ZAP DAST, CodeQL)
- GDPR/UU PDP adaptation for Indonesian context
- Security regression testing framework

**Target journals**:
- Jurnal Sistem Informasi (JSI) - SINTA 1
- Jurnal Ilmiah Teknik Elektro Komputer dan Informatika (JITEKI) - SINTA 2
- Kinetik: Game Technology, Information System, Computer Network, Computing, Electronics, and Control - SINTA 2

**Evidence from this project**:
- 164→0 SpotBugs issues fixed
- 0 HIGH/MEDIUM DAST findings
- Comprehensive audit logging implementation
- Documented security exclusions with justifications (`spotbugs-exclude.xml`)
- ZAP graybox testing with 111 endpoints

**Repo artifacts**:
- `docs/06-implementation-plan.md` (Phase 6 details)
- `docs/user-manual/11-keamanan-kepatuhan.md`
- `spotbugs-exclude.xml`
- `src/test/java/.../functional/security/`
- Security test reports in `target/security-reports/`

---

### 2. **Software Engineering** (Technical Depth)

**Title**: "Industry-Specific Seed Data Architecture for Multi-Tenant Accounting Systems"

**Key contributions**:
- Reusable seed pack pattern (CSV-based)
- Industry specialization (IT services, trading, manufacturing, education)
- Data migration vs. seed data strategy
- Test data management for functional testing

**Target journals**:
- TELKOMNIKA Telecommunication Computing Electronics and Control - SINTA 2
- International Journal of Advanced Computer Science and Applications (IJACSA) - SINTA 3

**Evidence from this project**:
- 4 industry seed packs implemented
- 44 manufacturing functional tests passing
- DataImportService architecture
- Clear separation: production migrations (V001-V004) vs. test data (V900-V912) vs. industry seeds

**Repo artifacts**:
- `industry-seed/{it-service,online-seller,coffee-shop,campus}/`
- `src/main/java/.../service/DataImportService.java`
- `src/test/resources/db/test/integration/` (V900-V912)
- `src/test/java/.../functional/manufacturing/` (44 tests)
- `src/test/java/.../config/CoffeeTestDataInitializer.java`

---

### 3. **Educational Technology** (Your Academic Context)

**Title**: "Tutorial-Driven Accounting Software: Bridging Theory and Practice for Indonesian SMEs"

**Key contributions**:
- 12-section user manual structure (pedagogy)
- Progressive complexity (setup → basic → industry-specific)
- Screenshot-driven documentation (77 screenshots from automated tests)
- Real-world accounting scenarios (coffee shop, campus billing)

**Target journals**:
- Jurnal Pendidikan Teknologi dan Kejuruan - SINTA 2
- Cakrawala Pendidikan - SINTA 2
- Register: Jurnal Ilmiah Teknologi Sistem Informasi - SINTA 3

**Evidence from this project**:
- "Crown jewel" tutorial (02-tutorial-akuntansi.md)
- 4 industry-specific guides
- 77 automated screenshots via Playwright
- Campus billing case study (scholarship, installment payment, financial aid)

**Repo artifacts**:
- `docs/user-manual/` (15 files, 12-section structure)
- `docs/user-manual/02-tutorial-akuntansi.md` (the "crown jewel")
- `docs/user-manual/09-industri-manufaktur.md` (manufacturing guide)
- `docs/user-manual/10-industri-pendidikan.md` (education guide)
- `target/functional-screenshots/` (automated screenshot generation)

---

### 4. **Information Systems** (Business Focus)

**Title**: "Open-Source ERP Localization for Indonesian Tax Compliance: Design and Implementation"

**Key contributions**:
- PPh 21/23 automation
- PPN calculation engine
- Indonesian COA structure (8-digit hierarchy)
- Industry-specific accounting flows

**Target journals**:
- Jurnal Sistem Informasi Bisnis - SINTA 2
- CommIT Journal - SINTA 2

**Evidence from this project**:
- Tax compliance module (Phase 2 complete)
- 4 industry implementations with different tax scenarios
- Indonesian localization (language, regulations, COA)

**Repo artifacts**:
- `docs/04-tax-compliance.md`
- `src/main/java/.../entity/tax/`
- `src/main/java/.../service/tax/`
- Industry-specific COA in seed packs

---

## Recommended Publication Strategy

### Year 1
**Paper #1 (Security)** - Target: Q2-Q3 2026
- **Why start here**: Most quantifiable results, timely topic (UU PDP enforcement), clear methodology
- **Strengths**: 164→0 bugs, 0 security issues, reproducible DevSecOps pipeline
- **Journal**: JITEKI (SINTA 2) or Kinetik (SINTA 2)

**Paper #3 (Education)** - Target: Q4 2026
- **Why second**: Aligns with lecturer role, appeals to education + CS journals
- **Strengths**: Lower barrier (case study vs. pure technical), strong pedagogical narrative
- **Journal**: Register (SINTA 3) or Cakrawala Pendidikan (SINTA 2)

### Year 2 (Optional)
**Paper #2 (Software Engineering)** - Target: Q2 2027
- **Why later**: Needs more comparative analysis with other ERP systems
- **Journal**: TELKOMNIKA (SINTA 2)

**Paper #4 (Information Systems)** - Target: Q4 2027
- **Why later**: Could benefit from user adoption data
- **Journal**: CommIT (SINTA 2)

---

## Next Steps for Paper #1 (Security & Compliance)

### Additional Data/Experiments Needed
1. **Benchmark comparison**: Compare DevSecOps metrics with similar open-source projects
2. **Performance impact**: Measure encryption overhead (before/after)
3. **DSAR request handling**: Document actual data export time/completeness
4. **Literature review**: Recent SINTA papers on UU PDP compliance (2023-2026)

### Paper Structure
1. **Introduction**: UU PDP enforcement (2024), SME compliance challenges
2. **Related Work**: Survey of accounting software security practices
3. **Methodology**: DevSecOps pipeline architecture
4. **Implementation**: Field-level encryption, audit logging, DAST integration
5. **Results**: 164→0 bugs, 0 critical vulnerabilities, test coverage metrics
6. **Discussion**: Trade-offs, lessons learned, generalizability
7. **Conclusion**: Reproducible security hardening for Indonesian SME software

### Research Questions
- RQ1: How can DevSecOps practices be adapted for resource-constrained SME software projects?
- RQ2: What is the performance impact of field-level encryption in web-based accounting systems?
- RQ3: How effective is automated security testing in detecting compliance violations?

---

## Next Steps for Paper #3 (Educational Technology)

### Additional Data/Experiments Needed
1. **User study**: Test manual with accounting students or SME owners
2. **Comprehension metrics**: Quiz before/after using tutorial
3. **Pedagogy framework**: Map to established instructional design models (ADDIE, Bloom's taxonomy)
4. **Comparative analysis**: Compare with existing accounting software documentation

### Paper Structure
1. **Introduction**: Gap between accounting education and SME practice
2. **Related Work**: Educational software design, technical documentation pedagogy
3. **Design Principles**: Progressive disclosure, screenshot-driven, industry-specific
4. **Implementation**: 12-section structure, automated screenshot generation
5. **Evaluation**: User study results, comprehension metrics
6. **Discussion**: Reusability, scalability to other domains
7. **Conclusion**: Tutorial-driven approach as bridge between theory and practice

### Research Questions
- RQ1: How does tutorial-driven software documentation affect user comprehension compared to traditional manuals?
- RQ2: What is the optimal structure for progressive accounting tutorials for non-accountants?
- RQ3: How can automated screenshot generation improve documentation maintenance?

---

## Action Items

- [ ] Choose primary paper topic (Security or Education)
- [ ] Create new repo for paper artifacts (LaTeX, data, scripts)
- [ ] Conduct literature review (search SINTA journals 2023-2026)
- [ ] Design additional experiments (if needed)
- [ ] Draft abstract and submit to target journal
- [ ] Extract/anonymize data from this repo
- [ ] Write methodology section (map repo artifacts to research methods)

---

## Resources

### SINTA Journal Search
- https://sinta.kemdikbud.go.id/journals

### Target Journal Websites
- JSI: http://jsi.cs.ui.ac.id/ (SINTA 1)
- JITEKI: https://jiteki.uho.ac.id/ (SINTA 2)
- Kinetik: http://kinetik.umm.ac.id/ (SINTA 2)
- Register: https://journal.unipdu.ac.id/index.php/register (SINTA 3)
- Cakrawala Pendidikan: https://journal.uny.ac.id/index.php/cp (SINTA 2)

### Related Keywords for Literature Search
- DevSecOps Indonesia
- UU PDP compliance
- SME accounting software
- Educational ERP systems
- Open-source accounting security
- Indonesian tax automation
- Tutorial-driven software
- Security testing automation
