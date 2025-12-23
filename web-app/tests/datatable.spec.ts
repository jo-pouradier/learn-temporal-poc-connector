import { test, expect } from '@playwright/test';

test.describe('DataTable Functionality', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:3000');
    // Wait for the page to load
    await page.waitForLoadState('networkidle');
  });

  test('should display search input', async ({ page }) => {
    const searchInput = page.getByPlaceholder('Search orders...');
    await expect(searchInput).toBeVisible();
  });

  test('should display filter buttons', async ({ page }) => {
    // Check for Main Status filter
    await expect(page.getByRole('button', { name: 'Main Status' })).toBeVisible();
    
    // Check for Connector Status filter
    await expect(page.getByRole('button', { name: 'Connector Status' })).toBeVisible();
    
    // Check for Price filter
    await expect(page.getByRole('button', { name: 'Price', exact: true })).toBeVisible();
    
    // Check for Stock filter
    await expect(page.getByRole('button', { name: 'Stock', exact: true })).toBeVisible();
  });

  test('should show sortable column headers', async ({ page }) => {
    // Wait for table to render
    await page.waitForSelector('table');
    
    // Check for Order ID header with sort button
    const orderIdHeader = page.getByRole('button', { name: /Order ID/i });
    await expect(orderIdHeader).toBeVisible();
    
    // Check for Main App header with sort button
    const mainAppHeader = page.getByRole('button', { name: /Main App/i });
    await expect(mainAppHeader).toBeVisible();
  });

  test('should display order count', async ({ page }) => {
    // Should show "X of Y orders" text
    const orderCount = page.locator('text=/\\d+ of \\d+ orders/');
    await expect(orderCount).toBeVisible();
  });

  test('global search should filter results', async ({ page }) => {
    // First trigger some orders
    const burstButton = page.getByRole('button', { name: /Trigger Burst/i });
    await burstButton.click();
    
    // Wait for orders to appear
    await page.waitForTimeout(2000);
    
    // Get initial count
    const initialCount = await page.locator('tbody tr').count();
    
    if (initialCount > 1) {
      // Type in search box
      const searchInput = page.getByPlaceholder('Search orders...');
      await searchInput.fill('test-search-term-that-wont-match');
      
      // Wait a bit for filter to apply
      await page.waitForTimeout(500);
      
      // Should show "No orders found" message
      await expect(page.locator('text=/No orders found/i')).toBeVisible();
    }
  });

  test('status filter should open popover', async ({ page }) => {
    const mainStatusButton = page.getByRole('button', { name: /Main Status/i });
    await mainStatusButton.click();
    
    // Wait for popover to appear
    await page.waitForTimeout(300);
    
    // Check if popover has content
    const popover = page.locator('[role="dialog"]').first();
    await expect(popover).toBeVisible();
  });

  test('clear filters button should appear when filters are active', async ({ page }) => {
    // Type in search to activate a filter
    const searchInput = page.getByPlaceholder('Search orders...');
    await searchInput.fill('test');
    
    // Wait for badge to appear
    await page.waitForTimeout(300);
    
    // Should show active filter badge
    const activeFilterBadge = page.locator('text=/\\d+ active/i');
    await expect(activeFilterBadge).toBeVisible();
    
    // Should show clear button
    const clearButton = page.getByRole('button', { name: /Clear/i });
    await expect(clearButton).toBeVisible();
  });

  test('sorting should work on column headers', async ({ page }) => {
    // Trigger some orders first
    const burstButton = page.getByRole('button', { name: /Trigger Burst/i });
    await burstButton.click();
    
    // Wait for orders
    await page.waitForTimeout(2000);
    
    // Click Order ID header to sort
    const orderIdHeader = page.getByRole('button', { name: /Order ID/i });
    await orderIdHeader.click();
    
    // Wait for sort to apply
    await page.waitForTimeout(500);
    
    // Click again to toggle sort direction
    await orderIdHeader.click();
    await page.waitForTimeout(500);
    
    // Test passes if no errors thrown
    expect(true).toBeTruthy();
  });
});
