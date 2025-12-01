# TODO: Achieve 80% Code Coverage

## Current Status
- **Overall Coverage**: 53% instruction coverage, 43% branch coverage
- **Target**: 80% instruction coverage
- **Gap to Close**: 27% additional coverage needed

## Coverage Analysis by Package

### High Priority (Critical Services with Low Coverage)

#### 1. PayrollReportService (0% coverage - 738 lines)
**Current**: 0% | **Target**: 80%
**Test File**: `PayrollReportTest.java` (NEW)

**Functional Tests to Create**:
- [ ] Test monthly payroll report generation for single employee
- [ ] Test monthly payroll report generation for multiple employees  
- [ ] Test annual payroll report with tax calculations
- [ ] Test PPh21 withholding report generation
- [ ] Test BPJS contribution report generation
- [ ] Test payroll summary by department/division
- [ ] Test export payroll reports to PDF and Excel
- [ ] Test payroll report with date range filters
- [ ] Test payroll report edge cases (no employees, zero salary)

**Coverage Impact**: ~700 lines, ~1.6% overall coverage gain

---

#### 2. ReceiptParserService + VisionOcrService (22%/0% coverage - 139+34 lines)
**Current**: 22%/0% | **Target**: 80%
**Test File**: `ReceiptParserTest.java` (NEW)

**Functional Tests to Create**:
- [ ] Test receipt upload with valid image (JPG, PNG)
- [ ] Test OCR text extraction from receipt image
- [ ] Test parsing receipt date from OCR text
- [ ] Test parsing receipt total amount from OCR text
- [ ] Test parsing merchant/vendor name from OCR text
- [ ] Test parsing line items from receipt
- [ ] Test handling of unclear/low-quality images
- [ ] Test handling of non-receipt images
- [ ] Test multiple receipt formats (supermarket, restaurant, gas station)
- [ ] Test integration with document storage
- [ ] Test draft transaction creation from parsed receipt

**Coverage Impact**: ~150 lines, ~0.35% overall coverage gain

---

#### 3. TelegramBotService (6% coverage - 164 lines)
**Current**: 6% | **Target**: 80%
**Test File**: `TelegramBotTest.java` (NEW)

**Integration Tests to Create**:
- [ ] Test Telegram webhook endpoint receives messages
- [ ] Test bot command: /start - welcome message
- [ ] Test bot command: /help - show available commands
- [ ] Test bot command: /balance - show account balances
- [ ] Test bot command: /report - request financial reports
- [ ] Test bot receives and processes receipt images
- [ ] Test bot authentication and authorization
- [ ] Test bot error handling and user feedback
- [ ] Test bot notification sending (tax deadlines, reminders)

**Coverage Impact**: ~140 lines, ~0.32% overall coverage gain

---

#### 4. DraftTransactionController + DraftTransactionService (0%/6% coverage - 41+101 lines)
**Current**: 0%/6% | **Target**: 80%
**Test File**: `DraftTransactionTest.java` (NEW)

**Functional Tests to Create**:
- [ ] Test create draft transaction from UI
- [ ] Test save draft transaction (partial data)
- [ ] Test list all draft transactions
- [ ] Test edit existing draft transaction
- [ ] Test delete draft transaction
- [ ] Test convert draft to final transaction
- [ ] Test draft auto-save functionality
- [ ] Test draft transaction validation (soft validation)
- [ ] Test draft transaction with attachments
- [ ] Test draft transaction status indicators

**Coverage Impact**: ~130 lines, ~0.30% overall coverage gain

---

#### 5. CompanyConfigService + CompanyBankAccountService (5%/1% coverage - 30+48 lines)
**Current**: 5%/1% | **Target**: 80%
**Test File**: `CompanyConfigTest.java` (NEW)

**Functional Tests to Create**:
- [ ] Test create company profile (name, address, tax ID)
- [ ] Test edit company profile information
- [ ] Test upload company logo
- [ ] Test add company bank account
- [ ] Test edit company bank account
- [ ] Test delete company bank account (with validation)
- [ ] Test mark bank account as default
- [ ] Test list all company bank accounts
- [ ] Test company fiscal year configuration
- [ ] Test company tax settings

**Coverage Impact**: ~70 lines, ~0.16% overall coverage gain

---

### Medium Priority (Enhance Existing Tests)

#### 6. InvoiceService + InvoiceController (42%/39% coverage - 123+112 lines)
**Current**: 42%/39% | **Target**: 80%
**Test File**: Enhance `InvoiceTest.java` (EXISTS)

**Additional Functional Tests**:
- [ ] Test invoice with multiple line items and taxes
- [ ] Test invoice amount calculations with discounts
- [ ] Test invoice with PPh23 withholding tax
- [ ] Test invoice with PPN (VAT) tax
- [ ] Test invoice payment recording (partial/full)
- [ ] Test invoice aging report
- [ ] Test invoice due date notifications
- [ ] Test invoice export to PDF with template
- [ ] Test invoice numbering sequence
- [ ] Test invoice reversal/credit note creation
- [ ] Test invoice with foreign currency

**Coverage Impact**: ~70 lines, ~0.16% overall coverage gain

---

#### 7. EmployeeService + SalaryComponentService (35%/26% coverage - 67+83 lines)
**Current**: 35%/26% | **Target**: 80%
**Test File**: Enhance `EmployeeTest.java` (EXISTS)

**Additional Functional Tests**:
- [ ] Test create salary component (earnings type)
- [ ] Test create salary component (deduction type)
- [ ] Test assign salary component to employee
- [ ] Test edit salary component assignment
- [ ] Test remove salary component from employee
- [ ] Test salary component with formula calculation
- [ ] Test employee tax configuration (PTKP status)
- [ ] Test employee BPJS enrollment
- [ ] Test employee resignation/termination
- [ ] Test employee salary history tracking

**Coverage Impact**: ~70 lines, ~0.16% overall coverage gain

---

#### 8. ProjectService + MilestoneController + PaymentTermController (36%/37%/25% coverage)
**Current**: 36%/37%/25% | **Target**: 80%
**Test File**: Enhance `ProjectTest.java` (EXISTS)

**Additional Functional Tests**:
- [ ] Test project creation with milestones
- [ ] Test project milestone completion tracking
- [ ] Test project payment terms configuration
- [ ] Test milestone payment generation
- [ ] Test project cost tracking and budget monitoring
- [ ] Test project time tracking integration
- [ ] Test project profitability calculation
- [ ] Test project status workflow (draft > active > completed)
- [ ] Test project invoice generation from milestones
- [ ] Test project reporting (actual vs budget)

**Coverage Impact**: ~90 lines, ~0.21% overall coverage gain

---

#### 9. DocumentService + DocumentStorageService + DocumentController (41%/44%/24% coverage)
**Current**: 41%/44%/24% | **Target**: 80%
**Test File**: `DocumentStorageTest.java` (NEW)

**Functional Tests to Create**:
- [ ] Test upload document (PDF, Excel, Image)
- [ ] Test attach document to transaction
- [ ] Test attach document to invoice
- [ ] Test attach multiple documents to entity
- [ ] Test download document
- [ ] Test view document thumbnail/preview
- [ ] Test delete document (with permission check)
- [ ] Test document list by entity
- [ ] Test document search by name/type
- [ ] Test document storage quota management
- [ ] Test document retention policy

**Coverage Impact**: ~100 lines, ~0.23% overall coverage gain

---

#### 10. UserService + UserController (21%/24% coverage - 58+96 lines)
**Current**: 21%/24% | **Target**: 80%
**Test File**: Enhance `UserManagementTest.java` (EXISTS)

**Additional Functional Tests**:
- [ ] Test user registration with validation
- [ ] Test user email verification
- [ ] Test user password change
- [ ] Test user forgot password flow
- [ ] Test user role assignment (admin, user, viewer)
- [ ] Test user permissions verification
- [ ] Test user deactivation/reactivation
- [ ] Test user login attempts tracking
- [ ] Test user session management
- [ ] Test user audit log

**Coverage Impact**: ~80 lines, ~0.18% overall coverage gain

---

#### 11. SettingsController (6% coverage - 97 lines)
**Current**: 6% | **Target**: 80%
**Test File**: `SettingsTest.java` (NEW)

**Functional Tests to Create**:
- [ ] Test view general settings page
- [ ] Test update system preferences
- [ ] Test update email notification settings
- [ ] Test update tax configuration settings
- [ ] Test update accounting period settings
- [ ] Test update report preferences
- [ ] Test export settings backup
- [ ] Test import settings from backup
- [ ] Test reset settings to default

**Coverage Impact**: ~80 lines, ~0.18% overall coverage gain

---

#### 12. JournalTemplateService + TemplateExecutionEngine (44%/84% coverage)
**Current**: 44%/84% | **Target**: 80%
**Test File**: Enhance `JournalTemplateTest.java` (EXISTS)

**Additional Functional Tests**:
- [ ] Test template with complex formula calculations
- [ ] Test template with conditional logic
- [ ] Test template with dynamic account selection
- [ ] Test template execution with missing variables
- [ ] Test template versioning and updates
- [ ] Test template copy/clone functionality
- [ ] Test template usage analytics
- [ ] Test template parameter validation
- [ ] Test bulk template execution

**Coverage Impact**: ~60 lines, ~0.14% overall coverage gain

---

### Lower Priority (Utility and Configuration)

#### 13. Utility Classes (0% coverage - 29 lines)
**Current**: 0% | **Target**: 80%
**Test File**: `UtilityTest.java` (NEW)

**Unit/Integration Tests**:
- [ ] Test date utility functions (formatting, parsing, calculation)
- [ ] Test number utility functions (rounding, formatting)
- [ ] Test string utility functions (validation, transformation)
- [ ] Test currency conversion utilities
- [ ] Test file utilities (size check, type validation)

**Coverage Impact**: ~25 lines, ~0.06% overall coverage gain

---

#### 14. Exception Handling Classes (0% coverage - 4 lines)
**Test File**: Integration into existing tests

**Tests to Add**:
- [ ] Test custom exception throwing in services
- [ ] Test exception handler responses in controllers
- [ ] Test validation error responses
- [ ] Test not found error responses
- [ ] Test business rule violation responses

**Coverage Impact**: ~4 lines, minimal impact

---

#### 15. Security Configuration (32% coverage - 12 lines)
**Test File**: `SecurityTest.java` (NEW)

**Integration Tests**:
- [ ] Test unauthorized access to protected endpoints
- [ ] Test authentication with valid credentials
- [ ] Test authentication with invalid credentials
- [ ] Test JWT token generation and validation
- [ ] Test role-based access control
- [ ] Test CSRF protection
- [ ] Test password encryption
- [ ] Test session timeout

**Coverage Impact**: ~10 lines, ~0.02% overall coverage gain

---

## Integration Test Scenarios (Cross-cutting)

#### 16. End-to-End Transaction Flows
**Test File**: `TransactionIntegrationTest.java` (NEW)

**Scenarios to Test**:
- [ ] Complete purchase transaction flow: Invoice → Receipt → Payment → Journal Entry → Report
- [ ] Complete sales transaction flow: Project → Invoice → Payment → Revenue Recognition → Report
- [ ] Complete payroll flow: Employee Setup → Salary Calculation → Payment → Journal Entry → Report
- [ ] Complete tax flow: Transaction → Tax Calculation → Tax Report → Tax Filing → Payment
- [ ] Complete month-end closing flow: Amortization → Accruals → Reconciliation → Reports → Period Lock

**Coverage Impact**: Multiple services, ~300+ lines, ~0.7% overall coverage gain

---

## Summary & Prioritization

### Phase 1: High-Impact Tests (Target: +5% coverage)
1. PayrollReportTest - 1.6%
2. DocumentStorageTest - 0.23%
3. DraftTransactionTest - 0.30%
4. ReceiptParserTest - 0.35%
5. TelegramBotTest - 0.32%
6. TransactionIntegrationTest - 0.7%

**Subtotal**: ~3.5% direct + 1.5% indirect = **5% coverage gain**

### Phase 2: Enhancement Tests (Target: +5% coverage)
7. Enhance InvoiceTest - 0.16%
8. Enhance EmployeeTest - 0.16%
9. Enhance ProjectTest - 0.21%
10. Enhance UserManagementTest - 0.18%
11. Enhance JournalTemplateTest - 0.14%
12. CompanyConfigTest - 0.16%
13. SettingsTest - 0.18%

**Subtotal**: ~1.2% direct + 3.8% indirect = **5% coverage gain**

### Phase 3: Complete Remaining Gaps (Target: +17% coverage)
- Expand all existing tests to cover edge cases
- Add error scenario testing
- Add validation testing
- Add performance/load testing scenarios
- Test all CRUD operations completely
- Test all report variations
- Test all export formats
- Test all calculation formulas

**Estimated Total**: **80%+ coverage**

---

## Testing Strategy

### Functional Test Approach (Primary)
- Use Playwright for UI-driven functional tests
- One functional test covers: Template → Controller → Service → Entity → Repository
- High ROI: ~5-10 lines test code covers ~50-100 lines production code
- Tests real user workflows and scenarios

### Integration Test Approach (Secondary)
- Use Spring Boot Test with TestContainers for database
- Test service layer integration with real database
- Test complex business logic and transaction management
- Test concurrent operations and data consistency

### Unit Test Approach (Minimal)
- Only for pure utility functions
- Only for complex calculation logic in isolation
- Avoid unit testing simple CRUD operations
- Avoid mocking when integration testing is feasible

---

## Execution Plan

### Week 1-2: High-Impact Tests
- Create PayrollReportTest with comprehensive payroll scenarios
- Create DocumentStorageTest with file upload/download scenarios
- Create DraftTransactionTest with draft workflow scenarios

### Week 3-4: New Feature Tests
- Create ReceiptParserTest with OCR and parsing scenarios
- Create TelegramBotTest with bot command scenarios
- Create TransactionIntegrationTest with E2E flows

### Week 5-6: Enhancement Tests
- Enhance InvoiceTest with advanced invoice scenarios
- Enhance EmployeeTest with salary component scenarios
- Enhance ProjectTest with milestone and payment term scenarios

### Week 7-8: Configuration & Settings Tests
- Create CompanyConfigTest with company setup scenarios
- Create SettingsTest with system configuration scenarios
- Enhance UserManagementTest with security scenarios

### Week 9-10: Edge Cases & Refinement
- Add error handling tests to all test files
- Add validation tests for all forms
- Add edge case tests (empty data, large data, special characters)
- Review and improve test coverage reports

---

## Success Metrics

- **Primary Metric**: Instruction coverage ≥ 80%
- **Secondary Metric**: Branch coverage ≥ 70%
- **Quality Metric**: All tests pass consistently
- **Maintainability Metric**: Test execution time < 5 minutes for functional tests
- **ROI Metric**: Lines of test code : Lines of production code covered ratio ≥ 1:10

---

## Notes

1. **Focus on Value**: Prioritize tests that verify business-critical features
2. **Real Scenarios**: Test actual user workflows, not artificial unit test scenarios
3. **Data Setup**: Use realistic test data, not minimal mock data
4. **Error Cases**: Don't forget negative scenarios and validation
5. **Maintenance**: Keep tests maintainable - use page objects, helper methods, test data builders
6. **CI/CD Integration**: Ensure tests run automatically in CI pipeline
7. **Documentation**: Document complex test scenarios and expected behaviors

---

## Coverage Tracking

Update this section weekly:

| Week | Coverage % | Tests Added | Notes |
|------|------------|-------------|-------|
| Baseline | 53% | - | Initial JaCoCo report |
| Week 1 | | | |
| Week 2 | | | |
| Week 3 | | | |
| Week 4 | | | |
| Week 5 | | | |
| Week 6 | | | |
| Week 7 | | | |
| Week 8 | | | |
| Week 9 | | | |
| Week 10 | | | |

Target completion: 10 weeks
Target coverage: 80%+
