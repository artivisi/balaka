# Contributing

## Code Style

- Java 25 features are used where appropriate (records, pattern matching, virtual threads).
- Use Lombok (`@Getter`, `@Setter`, `@RequiredArgsConstructor`, `@Slf4j`) to reduce boilerplate.
- Use Java records for DTOs (request/response objects).
- Entity field names use camelCase; database columns use snake_case.
- Package imports: no wildcards.

## Error Handling

No fallback values. No silent defaults. Throw exceptions with descriptive messages:

```java
// Correct
Widget widget = widgetRepository.findById(id)
    .orElseThrow(() -> new IllegalArgumentException("Widget not found: " + id));

// Wrong: silent fallback
Widget widget = widgetRepository.findById(id).orElse(new Widget());
```

Log errors at appropriate levels and propagate them to the caller.

## SpotBugs Zero-Issue Policy

All code must pass SpotBugs analysis with zero issues:

```bash
./mvnw spotbugs:check
```

If you encounter a false positive, add an exclusion to `spotbugs-exclude.xml` with a justification comment:

```xml
<!-- False positive: XSS prevention handled by Thymeleaf auto-escaping.
     All user input rendered via th:text (escaped) not th:utext.
     CSP headers prevent inline script execution. -->
<Match>
    <Class name="com.artivisi.accountingfinance.controller.MyController"/>
    <Bug pattern="XSS_SERVLET"/>
</Match>
```

## Migration Strategy

V001-V004 have been applied to production databases, so editing them breaks Flyway checksum validation there and forces the schema to be created by hand. Add new schema as a new migration file (V005 onwards).

| File | Purpose |
|------|---------|
| `V001__security.sql` | Users, roles, permissions tables |
| `V002__core_schema.sql` | Core accounting tables |
| `V003__feature_schema.sql` | Feature-specific tables |
| `V004__seed_data.sql` | Default seed data (roles, permissions, system templates) |

If a migration has already been applied to production, you must:
1. Create a new migration file (V005+).
2. Or manually fix the schema on production and update the checksum in `flyway_schema_history`.

See `docs/03-operations-guide.md` for the production migration caveat.

## Alpine.js CSP Requirement

All Alpine.js components must use `Alpine.data()` registration in `src/main/resources/static/js/alpine-components.js`. No inline expressions in Thymeleaf templates.

```javascript
// alpine-components.js
Alpine.data('widgetForm', () => ({
    amount: 0,
    tax: 0,

    calculateTax() {
        this.tax = this.amount * 0.11;
    }
}));
```

```html
<!-- Template: reference by name only -->
<div x-data="widgetForm">
    <input type="number" x-model="amount" @input="calculateTax()">
    <span x-text="tax"></span>
</div>
```

Do not use inline `x-data` with object literals:

```html
<!-- WRONG: blocked by CSP -->
<div x-data="{ amount: 0, tax: 0 }">
```

## Feature Completion Criteria

A feature is only marked complete when it has a passing Playwright functional test. Unit and integration tests alone are not sufficient for feature sign-off.

## Documentation

- Use English for developer documentation, code comments, and API descriptions.
- Use Indonesian for user-facing UI text (labels, messages, templates).
- No marketing language. Strictly technical.
- Do not create documentation files unless explicitly requested.

## Commit Guidelines

- Concise commit messages focused on "why" not "what".
- Reference issue/bug numbers when applicable (e.g., `BUG-014`).
- Run `./mvnw spotbugs:check` before committing.

## PR Guidelines

- One feature or fix per PR.
- Include the Playwright test that verifies the feature.
- Ensure SpotBugs passes.
- Update `CLAUDE.md` status section if completing a milestone.

---

Previous: [Testing](05-testing.md)
