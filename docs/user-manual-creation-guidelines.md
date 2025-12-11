# User Manual Creation Guideline #

## Content and Structure ##

1. Initial app setup. 
   - What do the user see after the app is properly installed with bare bones setup (no seed preload)
   - Elaboration on what industry seed available, and how to import it. This section can be technical, because the intended audience is system administrator with Java app familiarity who previously setup the app in the first place. 
   - List of features that are common to all industries (general ledger, journal template, tax, payroll). Only short summary, and links to detailed explanation later on
   - User management
   - Telegram integration
   - Data security (encrypted document and PII)

2. Basic accounting tutorial. Explanation on core concepts of accounting, accounting life cycle. Intended audience is business owners who are well versed in their business operation, but have no accounting background at all. Explain how to record daily transaction (paying utility bills, issuing invoice, receiving payments, purchasing from supplier/vendors), how to journal them, and how it will end up in financial report. Explain about adjustments (rent amortization, cost adjustments for  period end reporting) and closings. This section is expected to be beefy and will become the crown jewel of the user manual.

3. Fixed assets. How to record them, what is depreciation, how to calculate, how to use automated journal schedule to record depreciation. What is the economic lifetime of each asset category according to Indonesian regulation.

4. Tax transactions. Explain about various taxes in Indonesia. PPh 21, 23, 25, PPn, etc. Which tax types are applicable to their businesses, how to calculate them, and how the app can help to calculate, journal, and report them properly. Add references to tax regulation with links to the actual official regulation docs. Fiscal period management should go in this section as well.

5. Payroll. How to setup payroll, salary component elaboration, tax and bpjs obligation. How the app will help them do these. Add notes about tax deductibility of allowance types. Which one is tax deductible, which one is not. Refer to latest Indonesian tax regulation. 

Actually, I'm a bit hesitant on which one should go first, tax or payroll, from the continuity standpoint.

6. Industry types explanation. What are types available in the whole world, which one are we currently supporting in the app. Why are we differentiating among them, in terms of accounting practices.

7. Detailed explanation on Service Industry. Why this is the simplest, what are the transactions, how we solve them using templates, what templates are available, how to create new template (with example), what are the reports, what do we expect to see in the reports, how we analyze the reports to improve our business. What are the salary components, asset types, taxes, that commonly applied to service industry.

8. Detailed explanation on trading (seller) industry. Same as above.

9. Detailed explanation on manufacturing industry. Same as above.

10. Lampiran

    * Glosarium
    * Referensi Template
    * Referensi Amortisasi dan Depresiasi Otomatis

## General guideline ##

- All sections will have the following structure:

    * Concept explanation, universal, can be exercised manually (with pen and paper) or Excel spreadsheet
    * Detailed step by step instruction with screenshot to execute the concept in the app. Screenshot content must reflect the case study (description and amount in screenshot correspond to case study being explained).
    * Expected result after execution. What the user expect to see in the app, provide menu direction and screenshot to display execution result.

- All screenshots required should already covered in our comprehensive functional tests. We only need to add screenshot taking statement in appropriate places. If there is any specific scenario that has not been covered yet, add one as functional test, not merely screenshot generator.

- All contents in Bahasa Indonesia

## Things to discuss ##

* Where to put Client Management and Project Management
* Where to put Administrasi & Keamanan