# Coffee Shop Industry Seed Pack

## Business Profile
- **Name**: Kedai Kopi Nusantara (Coffee & Pastry Shop)
- **Industry**: Manufacturing (Food & Beverage)
- **Business Type**: Retail Coffee Shop with In-House Bakery
- **Tax Status**: Non-PKP (under Rp 4.8 billion revenue)

## Features Covered
- Chart of Accounts (74 accounts) for manufacturing business
- Raw materials inventory tracking
- Finished goods inventory tracking
- Bill of Materials (BOM) for production
- Production orders and costing
- COGS calculation
- Payroll with BPJS
- Tax compliance (PPh 21, non-PKP)

## Seed Data Contents

### Master Data
- **Chart of Accounts**: 74 accounts with manufacturing-specific accounts (raw materials, WIP, finished goods)
- **Journal Templates**: 8 templates for common transactions
- **Salary Components**: 17 components (standard Indonesian payroll)
- **Tax Deadlines**: 8 recurring deadlines (PPh 21, 23, 25, 4(2), PPN)
- **Asset Categories**: 4 categories (coffee equipment, bakery equipment, computers, furniture)
- **Fiscal Periods**: Jan-Mar 2024

### Manufacturing Data
- **Product Categories**: 5 categories (raw materials for coffee/bakery, finished goods)
- **Products**: 12 products (10 raw materials + 2 finished goods)
- **BOMs**: 2 recipes (Croissant, Roti Bakar Coklat)
- **Production Orders**: 2 completed orders
- **Inventory Transactions**: 29 transactions (purchases, production, sales)
- **Inventory Balances**: Current stock levels after all transactions

## Test Data Scenario

### Products
**Raw Materials (Coffee)**:
- Biji Kopi Arabica
- Susu Segar
- Gula Aren Cair
- Es Batu

**Raw Materials (Bakery)**:
- Tepung Terigu
- Butter
- Telur Ayam
- Ragi Instan
- Garam
- Coklat Blok

**Finished Goods**:
- Croissant (25,000/pcs, unit cost 4,455)
- Roti Bakar Coklat (20,000/pcs, unit cost 4,388)

### Production Scenario
**Production Order 001** (Jan 8, 2024):
- BOM: Croissant (24 pcs batch)
- Component consumption: 3kg tepung, 1.5kg butter, 12 telur, 10g ragi, 6g garam, 0.6L susu
- Total cost: Rp 106,920 (Rp 4,455/pc)

**Production Order 002** (Jan 8, 2024):
- BOM: Roti Bakar Coklat (20 pcs batch)
- Component consumption: 2.5kg tepung, 1kg butter, 10 telur, 8g ragi, 5g garam, 0.4kg coklat
- Total cost: Rp 87,760 (Rp 4,388/pc)

### Sales Scenario
- Croissant: 15 sold @ 25,000 = Rp 375,000 (COGS: Rp 66,825, margin: 82%)
- Roti Bakar: 12 sold @ 20,000 = Rp 240,000 (COGS: Rp 52,656, margin: 78%)

### Expected Inventory After All Transactions
- Croissant: 9 pcs @ Rp 4,455
- Roti Bakar Coklat: 8 pcs @ Rp 4,388
- Raw materials: Various balances after production consumption

## Usage

Import this seed pack via:
1. Settings > Import Data
2. Upload `coffee-shop-seed.zip`
3. System will load COA, templates, products, BOMs, and initial inventory

## Notes
- Production costs use weighted average method
- COGS auto-calculated using FIFO layers
- All dates in Jan 2024 for testing consistency
- Designed for functional test validation
