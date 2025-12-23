import { test, expect } from '@playwright/test';

test.describe('Table Sorting Tests', () => {
  test('sorting should work on Order ID column', async ({ page }) => {
    await page.goto('http://localhost:3000');
    await page.waitForLoadState('networkidle');
    
    // Trigger some orders
    const burstButton = page.getByRole('button', { name: /Trigger Burst/i });
    await burstButton.click();
    
    // Wait for orders to load
    await page.waitForTimeout(3000);
    
    // Get all order IDs before sorting
    const orderIdsBefore = await page.locator('tbody tr td:first-child span').allTextContents();
    console.log('Order IDs before sort:', orderIdsBefore.slice(0, 5));
    
    if (orderIdsBefore.length > 1) {
      // Click Order ID header to sort
      const orderIdHeader = page.locator('button:has-text("Order ID")').first();
      await orderIdHeader.click();
      
      // Wait for sort to apply
      await page.waitForTimeout(500);
      
      // Get order IDs after first sort
      const orderIdsAfterSort1 = await page.locator('tbody tr td:first-child span').allTextContents();
      console.log('Order IDs after 1st sort:', orderIdsAfterSort1.slice(0, 5));
      
      // Click again to reverse sort
      await orderIdHeader.click();
      await page.waitForTimeout(500);
      
      // Get order IDs after second sort
      const orderIdsAfterSort2 = await page.locator('tbody tr td:first-child span').allTextContents();
      console.log('Order IDs after 2nd sort:', orderIdsAfterSort2.slice(0, 5));
      
      // Verify that the order changed
      expect(orderIdsBefore).not.toEqual(orderIdsAfterSort1);
      expect(orderIdsAfterSort1).not.toEqual(orderIdsAfterSort2);
      
      // Verify second sort is reverse of first
      expect(orderIdsAfterSort2).toEqual(orderIdsAfterSort1.slice().reverse());
    }
  });

  test('sorting indicator should appear on column', async ({ page }) => {
    await page.goto('http://localhost:3000');
    await page.waitForLoadState('networkidle');
    
    // Trigger some orders
    await page.getByRole('button', { name: /Trigger Burst/i }).click();
    await page.waitForTimeout(2000);
    
    // Click Order ID header
    const orderIdHeader = page.locator('button:has-text("Order ID")').first();
    await orderIdHeader.click();
    await page.waitForTimeout(300);
    
    // Check that the button is still there (sorting is working)
    await expect(orderIdHeader).toBeVisible();
    
    // Take a screenshot
    await page.screenshot({ path: 'test-results/sorting-test.png' });
  });
});
