import assert from 'node:assert/strict';
import { chromium } from 'playwright';

const browser = await chromium.launch({
  executablePath: process.env.CHROMIUM_PATH || undefined,
  headless: true,
  args: ['--no-sandbox', '--disable-dev-shm-usage', '--no-zygote', '--single-process', '--disable-gpu']
});

try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));

  console.log('[1/4] Navigating to Screener...');
  await page.goto('http://localhost:4200/#screener');
  await page.getByRole('heading', { name: 'Market screener' }).waitFor();

  // Test searching various companies that were not in the original prepopulated list
  const testCompanies = [
    { query: 'Boeing', expectedSymbol: 'BA', expectedName: 'Boeing' },
    { query: 'Ford', expectedSymbol: 'F', expectedName: 'Ford Motor' },
    { query: 'Uber', expectedSymbol: 'UBER', expectedName: 'Uber Technologies' },
    { query: 'Airbnb', expectedSymbol: 'ABNB', expectedName: 'Airbnb' },
    { query: 'Walmart', expectedSymbol: 'WMT', expectedName: 'Walmart' },
    { query: 'Pfizer', expectedSymbol: 'PFE', expectedName: 'Pfizer' },
    { query: 'Caterpillar', expectedSymbol: 'CAT', expectedName: 'Caterpillar' },
    { query: 'Johnson', expectedSymbol: 'JNJ', expectedName: 'Johnson & Johnson' },
    { query: 'Disney', expectedSymbol: 'DIS', expectedName: 'Walt Disney' },
    { query: 'Tesla', expectedSymbol: 'TSLA', expectedName: 'Tesla' },
  ];

  for (const item of testCompanies) {
    const searchInput = page.getByLabel('Search stocks');
    await searchInput.fill(item.query);
    await page.waitForTimeout(200);

    const rows = page.locator('tbody tr');
    const count = await rows.count();
    assert(count >= 1, `Expected at least 1 result for query "${item.query}", found ${count}`);

    const text = await rows.first().innerText();
    assert(text.includes(item.expectedSymbol), `Expected row to contain symbol "${item.expectedSymbol}", got: ${text}`);
    console.log(`  ✓ Search "${item.query}" returned ${item.expectedSymbol} (${item.expectedName})`);
  }

  console.log('[2/4] Testing Trade ticket company name auto-resolution...');
  await page.locator('nav button').filter({ hasText: 'Trade' }).click();
  await page.getByRole('heading', { name: 'Order ticket' }).waitFor();

  const tradeTickers = [
    { input: 'Boeing', expectedSymbol: 'BA', expectedText: 'Boeing (BA)' },
    { input: 'Ford', expectedSymbol: 'F', expectedText: 'Ford Motor (F)' },
    { input: 'Uber', expectedSymbol: 'UBER', expectedText: 'Uber Technologies (UBER)' },
    { input: 'Airbnb', expectedSymbol: 'ABNB', expectedText: 'Airbnb (ABNB)' },
    { input: 'Walmart', expectedSymbol: 'WMT', expectedText: 'Walmart (WMT)' },
  ];

  const tickerInput = page.getByLabel('Stock ticker symbol');
  for (const item of tradeTickers) {
    await tickerInput.fill(item.input);
    await page.waitForTimeout(300);

    const assetSummary = await page.locator('.asset-summary strong').innerText();
    assert(assetSummary.includes(item.expectedSymbol), `Expected asset summary to include "${item.expectedSymbol}", got "${assetSummary}"`);
    console.log(`  ✓ Trade ticket resolved "${item.input}" -> "${assetSummary}"`);
  }

  console.log('[3/4] Testing demo order execution for non-prepopulated stock (Boeing)...');
  await tickerInput.fill('BA');
  await page.waitForTimeout(300);
  const qtyInput = page.getByLabel('Quantity');
  await qtyInput.fill('2');
  await page.getByRole('button', { name: 'Review buy order' }).click();
  await page.getByRole('button', { name: 'Place demo order' }).click();
  await page.locator('.receipt').waitFor();
  const receiptText = await page.locator('.receipt').innerText();
  assert(receiptText.includes('PENDING') || receiptText.includes('COMPLETED'), `Receipt text unexpected: ${receiptText}`);
  console.log('  ✓ Demo order placed successfully for BA!');

  console.log('[4/4] Testing Charts for non-prepopulated stock (Boeing)...');
  await page.locator('nav button').filter({ hasText: 'Charts' }).click();
  await page.getByRole('heading', { name: 'Explore symbols' }).waitFor();

  // Click Boeing row in chart symbol explorer
  await page.getByLabel('Search stocks').fill('Boeing');
  await page.waitForTimeout(200);
  await page.locator('tbody tr button.asset-cell').first().click();
  await page.waitForTimeout(300);

  const chartHeading = await page.locator('.chart-panel h3').innerText();
  assert(chartHeading.includes('Boeing') || chartHeading.includes('BA'), `Expected chart heading to include Boeing/BA, got: ${chartHeading}`);

  const svgPath = await page.locator('.chart svg path').first().getAttribute('d');
  assert(svgPath && svgPath.length > 20, `Expected SVG chart path to be rendered, got: ${svgPath}`);
  console.log(`  ✓ Chart rendered for ${chartHeading} with valid SVG path (${svgPath.substring(0, 30)}...)`);

  assert.deepEqual(errors, []);
  console.log('\nALL VERIFICATION TESTS PASSED SUCCESSFULLY! ✓');
} finally {
  await browser.close();
}
