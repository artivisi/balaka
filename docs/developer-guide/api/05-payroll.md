# Payroll API

Payroll management, PPh 21 calculation, and 1721-A1 generation. All endpoints require `Authorization: Bearer <token>`.
Read endpoints need the `tax-export:read` scope; write endpoints need `payroll:write`. The token user's
role must also hold the permission (see [Authentication](01-authentication.md#scopes-and-authorization)):

| Endpoint | Scope | Permission |
|----------|-------|------------|
| `GET /api/payroll/**` | `tax-export:read` | `PAYROLL_VIEW` |
| `POST /api/payroll`, `POST /api/payroll/{id}/calculate`, `DELETE /api/payroll/{id}` | `payroll:write` | `PAYROLL_CREATE` |
| `POST /api/payroll/{id}/approve` | `payroll:write` | `PAYROLL_APPROVE` |
| `POST /api/payroll/{id}/post` | `payroll:write` | `PAYROLL_POST` |
| `POST /api/payroll/schedule`, `DELETE /api/payroll/schedule` | `payroll:write` | `PAYROLL_CREATE` and `PAYROLL_APPROVE` |
| `GET /api/employees/**` | `tax-export:read` | `EMPLOYEE_VIEW` |
| `POST /api/employees` | `payroll:write` | `EMPLOYEE_CREATE` |
| `PUT /api/employees/{id}`, `POST/PUT /api/employees/{id}/salary-components/**` | `payroll:write` | `EMPLOYEE_EDIT` |
| `GET /api/salary-components` | `tax-export:read` | `SALARY_COMPONENT_VIEW` |
| `POST/PUT/DELETE /api/salary-components/**` | `payroll:write` | `SALARY_COMPONENT_MANAGE` |

Base URL: `/api/payroll`

## Payroll Run Lifecycle

```
DRAFT ──calculate──> CALCULATED ──approve──> APPROVED ──post──> POSTED
  │                       │
  └──delete               └──recalculate (back to CALCULATED)
```

- **DRAFT**: payroll run created, no calculations yet.
- **CALCULATED**: BPJS and PPh 21 computed for all active employees.
- **APPROVED**: reviewed and approved, ready for posting.
- **POSTED**: journal entry created. Payment is recorded. Payroll data appears in summary and 1721-A1 endpoints.

## List Payroll Runs

```http
GET /api/payroll?year=2025&status=POSTED&page=0&size=20
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `year` | int | no | Filter by year |
| `status` | enum | no | Filter by status: `DRAFT`, `CALCULATED`, `APPROVED`, `POSTED` |
| `page` | int | no | Page number (0-based) |
| `size` | int | no | Page size |

Response: paginated `PayrollRunResponse` list.

## Create Payroll Run

```http
POST /api/payroll
Content-Type: application/json
```

```json
{
  "payrollPeriod": "2025-12",
  "notes": "December 2025 payroll"
}
```

Response: `201 Created`

```json
{
  "id": "uuid",
  "payrollPeriod": "2025-12",
  "status": "DRAFT",
  "employeeCount": 0,
  "totalGross": 0,
  "totalDeductions": 0,
  "totalNet": 0,
  "totalPph21": 0,
  "notes": "December 2025 payroll"
}
```

Only one payroll run per period is allowed. Returns `400` if a run already exists for that period.

## Get Payroll Run Detail

```http
GET /api/payroll/{id}
```

Returns the payroll run with all employee payroll details (gross salary, deductions, PPh 21, net salary per employee).

## Bonus and THR

```
GET    /api/payroll/{id}/additions
POST   /api/payroll/{id}/additions
DELETE /api/payroll/{id}/additions/{additionId}
```

Writes need scope `payroll:write` and `PAYROLL_CREATE`; reads need `tax-export:read` and `PAYROLL_VIEW`.

```json
{ "employeeId": "uuid", "type": "BONUS", "amount": 5000000, "description": "Kinerja" }
```

`type` is `BONUS` or `THR`. The run must be DRAFT or CALCULATED (otherwise `409`); a CALCULATED run is recalculated at once with its base salary and JKK class. The employee must be active (otherwise `400`). A calculation fails with `409` if a row belongs to an employee who has since become inactive.

On the detail: `grossSalary = baseSalary + bonus + thr`. The TER rate and base use `grossSalary + benefitInKind`; BPJS uses `baseSalary`. December `pph21` is negative when the year was over-withheld, and adds to `netPay`.

## Calculate Payroll

```http
POST /api/payroll/{id}/calculate
Content-Type: application/json
```

```json
{
  "baseSalary": 5000000,
  "jkkRiskClass": 2
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `baseSalary` | decimal | yes | Base salary for calculation |
| `jkkRiskClass` | int | no | JKK risk class 1-5 (default: 1) |

Calculates BPJS contributions and PPh 21 for all active employees. PPh 21 uses TER method (PMK 168/2023) for January--November. December uses annual reconciliation with progressive tax brackets (PP 58/2023).

Transitions status to `CALCULATED`. Returns `400` if no active employees exist.

## Approve Payroll

```http
POST /api/payroll/{id}/approve
```

Transitions from `CALCULATED` to `APPROVED`. Returns `400` if not in `CALCULATED` status.

## Post Payroll

```http
POST /api/payroll/{id}/post
```

Creates a journal entry using the payroll template (configured via `app.payroll.template-id`). Template lines with zero amounts are skipped (e.g., PPh 21 = 0 for low-salary employees). Transitions to `POSTED`.

Returns `500` if the payroll template is not found.

## Delete Payroll Run

```http
DELETE /api/payroll/{id}
```

Only `DRAFT` payroll runs can be deleted. Response: `204 No Content`.

## Reconciliation

```
GET /api/payroll/reconciliation?year=2026
GET /api/payroll/reconciliation/2026-03
```

Scope `tax-export:read` and `PAYROLL_VIEW`. Salary accounts are the debit lines of the payroll template with formula `grossSalary`, `bonus` or `thr`. Ledger figures are net debits from POSTED journals, closing entries excluded.

| Field | Meaning |
|-------|---------|
| `runId`, `runStatus` | Run of the period, `null` when none or cancelled |
| `runSalary`, `runBonus`, `runThr` | Parts of `runCashGross` |
| `runBenefitInKind` | Natura counted in the run; not on salary accounts |
| `runTaxableIncome`, `runPph21` | PPh 21 base and tax of the masa, posted or not; see `runStatus` |
| `ledgerSalaryExpense` | `ledgerFromRun` + `ledgerOutsideRun` |
| `ledgerPph21FromRun` | Net credit on the PPh 21 account from the run's journal |
| `difference` | `ledgerSalaryExpense - runCashGross` when POSTED, else `ledgerSalaryExpense` |
| `status` | `MATCHED`, `DIFFERENCE`, `RUN_NOT_POSTED`, `LEDGER_WITHOUT_RUN`, `NO_ACTIVITY` |
| `journalsOutsideRun` | Journals outside the run debiting a salary account, including DRAFT |

The year response has `periods` (12 rows) and `ledgerSalaryExpense`, `postedCashGross`, `postedTaxableIncome`, `postedPph21`, `difference`.

Java callers use `PayrollReconciliationService.reconcile(YearMonth)` and `reconcileYear(int)`.

`warnings` on `POST /api/payroll`, `/calculate`, `/approve`, `/post` and `GET /api/payroll/{id}` lists salary journals outside the run. The field is absent on list rows.

## 1721-A1 (Employee Tax Proof)

```http
GET /api/payroll/employees/{employeeId}/1721-a1?year=2025
```

Generates annual PPh 21 tax proof (bukti potong) data for a single employee. Only includes data from `POSTED` payroll runs.

Response:

```json
{
  "year": 2025,
  "employee": {
    "name": "John Doe",
    "npwp": "12.345.678.9-012.000",
    "nikKtp": "1234567890123456",
    "ptkpStatus": "TK_0",
    "hireDate": "2024-01-15",
    "resignDate": null,
    "monthCount": 12
  },
  "calculation": {
    "penghasilanBruto": 72000000,
    "biayaJabatan": 3600000,
    "penghasilanNeto": 68400000,
    "ptkp": 54000000,
    "pkp": 14400000,
    "pph21Terutang": 720000,
    "pph21Dipotong": 720000,
    "pph21KurangBayar": 0
  },
  "monthlyBreakdown": [
    { "period": "2025-01", "grossSalary": 6000000, "pph21": 60000 },
    { "period": "2025-02", "grossSalary": 6000000, "pph21": 60000 }
  ]
}
```

Returns `404` if the employee has no `POSTED` payroll data for the given year.

## Benefit in Kind (Natura)

```http
GET    /api/benefits-in-kind?year=2025        # tax-export:read + PAYROLL_VIEW
POST   /api/benefits-in-kind                  # payroll:write + PAYROLL_CREATE
DELETE /api/benefits-in-kind/{id}             # payroll:write + PAYROLL_CREATE
```

```json
{"transactionId": "...", "employeeId": "...", "amount": 2000000, "description": "Fasilitas mobil"}
```

The transaction must be `POSTED` (409 otherwise) and the recipients' total cannot exceed its expense debit (400). The first recipient adds the system tag `NATURA`. `POST /api/payroll/{id}/calculate` adds rows dated from 1 January to the period end, not yet counted by a run, to the recipient's PPh 21 base; cash `grossSalary` is unchanged. `payrollPeriod` in the response shows the counting run; such a row cannot be deleted (409) and its transaction cannot be voided until the run is recalculated or cancelled.

`penghasilanBruto` on 1721-A1, `totalGross` on the PPh 21 annual summary, and the annual e-Bupot export include natura from `POSTED` runs.

## PPh 21 Annual Summary

```http
GET /api/payroll/pph21/summary?year=2025
```

Aggregates PPh 21 data across all employees from `POSTED` payroll runs. Returns total gross, total PPh 21, and per-employee breakdown.

## Payroll Schedule (Automated Runs)

### Get Schedule

```http
GET /api/payroll/schedule
```

Returns the current payroll schedule configuration, or `404` if not configured.

### Create/Update Schedule

```http
POST /api/payroll/schedule
Content-Type: application/json
```

```json
{
  "dayOfMonth": 25,
  "baseSalary": 5000000,
  "jkkRiskClass": 2,
  "autoCalculate": true,
  "autoApprove": false,
  "active": true
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `dayOfMonth` | int | yes | Day of month to create payroll (1-28) |
| `baseSalary` | decimal | yes | Base salary for auto-calculation |
| `jkkRiskClass` | int | yes | JKK risk class (1-5) |
| `autoCalculate` | boolean | yes | Automatically calculate after creation |
| `autoApprove` | boolean | no | Automatically approve after calculation |
| `active` | boolean | no | Enable/disable the schedule (default: true) |

Only one schedule exists at a time. `POST` replaces the existing configuration. The scheduler runs daily at 6:30 AM and creates a DRAFT payroll run if the day matches `dayOfMonth`. Posting is always manual -- it signifies actual payment.

### Delete Schedule

```http
DELETE /api/payroll/schedule
```

Response: `204 No Content`.

---

Next: [Tax Export](06-tax-export.md)
