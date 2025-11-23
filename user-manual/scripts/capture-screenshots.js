const { chromium } = require('@playwright/test');
const path = require('path');
const fs = require('fs');

const BASE_URL = process.env.APP_URL || 'http://localhost:8080';
const SCREENSHOTS_DIR = path.join(__dirname, '..', 'screenshots');

// Page definitions for screenshot capture
const pages = [
  // Authentication
  {
    id: 'login',
    name: 'Halaman Login',
    url: '/login',
    requiresAuth: false,
    description: 'Halaman login untuk masuk ke aplikasi',
    section: 'authentication'
  },

  // Dashboard
  {
    id: 'dashboard',
    name: 'Dashboard',
    url: '/dashboard',
    requiresAuth: true,
    description: 'Tampilan utama dengan ringkasan keuangan',
    section: 'dashboard'
  },

  // Chart of Accounts
  {
    id: 'accounts-list',
    name: 'Daftar Akun',
    url: '/accounts',
    requiresAuth: true,
    description: 'Daftar semua akun dalam bagan akun (Chart of Accounts)',
    section: 'accounts'
  },
  {
    id: 'accounts-form-new',
    name: 'Tambah Akun Baru',
    url: '/accounts/new',
    requiresAuth: true,
    description: 'Form untuk menambahkan akun baru',
    section: 'accounts'
  },

  // Transactions
  {
    id: 'transactions-list',
    name: 'Daftar Transaksi',
    url: '/transactions',
    requiresAuth: true,
    description: 'Daftar semua transaksi dengan filter status dan periode',
    section: 'transactions'
  },
  {
    id: 'transactions-form-new',
    name: 'Transaksi Baru',
    url: '/transactions/new',
    requiresAuth: true,
    description: 'Form untuk membuat transaksi baru menggunakan template',
    section: 'transactions'
  },
  {
    id: 'transactions-detail',
    name: 'Detail Transaksi',
    url: '/transactions/TRX-2025-0001',
    requiresAuth: true,
    description: 'Tampilan detail transaksi dengan jurnal dan audit trail',
    section: 'transactions'
  },

  // Journal Entries (Buku Besar)
  {
    id: 'journals-list',
    name: 'Buku Besar',
    url: '/journals',
    requiresAuth: true,
    description: 'Tampilan buku besar dengan filter akun dan periode',
    section: 'journals'
  },
  {
    id: 'journals-detail',
    name: 'Detail Jurnal',
    url: '/journals/JE-2025-0001',
    requiresAuth: true,
    description: 'Detail entri jurnal dengan dampak ke akun',
    section: 'journals'
  },

  // Templates
  {
    id: 'templates-list',
    name: 'Daftar Template',
    url: '/templates',
    requiresAuth: true,
    description: 'Daftar template jurnal dengan kategori',
    section: 'templates'
  },
  {
    id: 'templates-detail',
    name: 'Detail Template',
    url: '/templates/TPL-001',
    requiresAuth: true,
    description: 'Konfigurasi template jurnal dan formula',
    section: 'templates'
  },
  {
    id: 'templates-form-new',
    name: 'Template Baru',
    url: '/templates/new',
    requiresAuth: true,
    description: 'Form untuk membuat template jurnal baru',
    section: 'templates'
  }
];

// Export pages for use in manual generator
module.exports = { pages };

async function captureScreenshots() {
  console.log('Starting screenshot capture...');
  console.log(`Base URL: ${BASE_URL}`);

  // Ensure screenshots directory exists
  if (!fs.existsSync(SCREENSHOTS_DIR)) {
    fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
  }

  const browser = await chromium.launch({
    headless: true
  });

  const context = await browser.newContext({
    viewport: { width: 1280, height: 800 },
    locale: 'id-ID'
  });

  const page = await context.newPage();

  // Capture metadata for the manual generator
  const capturedPages = [];

  // Login first if we have authenticated pages
  const hasAuthPages = pages.some(p => p.requiresAuth);
  if (hasAuthPages) {
    console.log('Logging in...');
    try {
      await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle', timeout: 30000 });
      await page.fill('input[name="username"]', 'admin');
      await page.fill('input[name="password"]', 'admin');
      await page.click('button[type="submit"]');
      await page.waitForURL('**/dashboard', { timeout: 10000 });
      console.log('Login successful');
    } catch (error) {
      console.warn('Login failed or skipped:', error.message);
      console.log('Continuing with unauthenticated captures...');
    }
  }

  // Capture each page
  for (const pageConfig of pages) {
    console.log(`Capturing: ${pageConfig.name} (${pageConfig.url})`);

    try {
      await page.goto(`${BASE_URL}${pageConfig.url}`, {
        waitUntil: 'networkidle',
        timeout: 30000
      });

      // Wait for page to stabilize
      await page.waitForTimeout(500);

      // Take screenshot
      const screenshotPath = path.join(SCREENSHOTS_DIR, `${pageConfig.id}.png`);
      await page.screenshot({
        path: screenshotPath,
        fullPage: false
      });

      capturedPages.push({
        ...pageConfig,
        screenshot: `${pageConfig.id}.png`,
        captured: true
      });

      console.log(`  Saved: ${screenshotPath}`);
    } catch (error) {
      console.error(`  Failed to capture ${pageConfig.name}: ${error.message}`);
      capturedPages.push({
        ...pageConfig,
        screenshot: null,
        captured: false,
        error: error.message
      });
    }
  }

  await browser.close();

  // Save metadata for manual generator
  const metadataPath = path.join(SCREENSHOTS_DIR, 'metadata.json');
  fs.writeFileSync(metadataPath, JSON.stringify(capturedPages, null, 2));
  console.log(`\nMetadata saved to: ${metadataPath}`);

  // Summary
  const successful = capturedPages.filter(p => p.captured).length;
  const failed = capturedPages.filter(p => !p.captured).length;
  console.log(`\nCapture complete: ${successful} successful, ${failed} failed`);

  return capturedPages;
}

// Run if called directly
if (require.main === module) {
  captureScreenshots().catch(console.error);
}
