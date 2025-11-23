# User Manual Generator

This directory contains the user manual generator for Aplikasi Akunting. It uses Playwright to capture screenshots of the application and generates a static HTML documentation site.

## Prerequisites

- Node.js 22+
- Running instance of Aplikasi Akunting (or accessible via URL)

## Installation

```bash
cd user-manual
npm install
npx playwright install chromium
```

## Usage

### Generate Manual with Screenshots

1. Start the Aplikasi Akunting application:
   ```bash
   # From project root
   ./mvnw spring-boot:run
   ```

2. Capture screenshots and generate manual:
   ```bash
   cd user-manual
   npm run build
   ```

### Commands

| Command | Description |
|---------|-------------|
| `npm run capture` | Capture screenshots from the running application |
| `npm run generate` | Generate HTML manual from templates |
| `npm run build` | Run capture + generate |
| `npm run serve` | Serve the generated manual locally |

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `APP_URL` | `http://localhost:8080` | Base URL of the application |

## Directory Structure

```
user-manual/
├── package.json         # Node.js dependencies
├── README.md           # This file
├── scripts/
│   ├── capture-screenshots.js  # Playwright screenshot capture
│   └── generate-manual.js      # Manual HTML generator
├── templates/
│   └── manual.ejs      # EJS template for manual
├── screenshots/        # Captured screenshots (generated)
│   └── metadata.json   # Screenshot metadata
└── dist/               # Generated manual (output)
    ├── index.html      # Main manual page
    └── screenshots/    # Copied screenshots
```

## GitHub Pages Deployment

The user manual is automatically deployed to GitHub Pages when:
- Changes are pushed to the `main` branch
- Changes affect `user-manual/` or `src/main/resources/templates/`
- Manual workflow dispatch is triggered

### Manual Trigger

You can manually trigger the workflow from GitHub Actions with an option to skip screenshot capture (use existing screenshots).

## Adding New Pages

1. Edit `scripts/capture-screenshots.js` to add new page definitions
2. Edit `scripts/generate-manual.js` to add content to `manualStructure`
3. Run `npm run build` to regenerate the manual

## Customizing the Template

Edit `templates/manual.ejs` to customize the HTML output. The template uses:
- Tailwind CSS (via CDN)
- EJS templating
- Responsive design with print support
