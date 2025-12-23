'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { submitCustomOrder } from '@/lib/api';
import { Package, DollarSign, Send, Loader2 } from 'lucide-react';

interface CustomOrderFormProps {
  onOrderSubmitted?: () => void;
}

export function CustomOrderForm({ onOrderSubmitted }: CustomOrderFormProps) {
  const [orderId, setOrderId] = useState('');
  const [price, setPrice] = useState('');
  const [stock, setStock] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const generateRandomOrderId = () => {
    const randomId = `custom-${Math.random().toString(36).substring(2, 10)}`;
    setOrderId(randomId);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);

    // Validation
    if (!orderId.trim()) {
      setError('Order ID is required');
      return;
    }

    const priceNum = parseFloat(price);
    const stockNum = parseInt(stock, 10);

    if (isNaN(priceNum) || priceNum < 0) {
      setError('Price must be a valid positive number');
      return;
    }

    if (isNaN(stockNum) || stockNum < 0) {
      setError('Stock must be a valid positive integer');
      return;
    }

    setIsSubmitting(true);

    try {
      const result = await submitCustomOrder(orderId.trim(), priceNum, stockNum);
      setSuccess(`Order ${result.orderId} submitted successfully! Status: ${result.status}`);
      
      // Clear form
      setOrderId('');
      setPrice('');
      setStock('');

      // Notify parent to refresh data
      if (onOrderSubmitted) {
        onOrderSubmitted();
      }

      // Clear success message after 3 seconds
      setTimeout(() => setSuccess(null), 3000);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to submit order');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Send className="h-5 w-5" />
          Send Custom Order
        </CardTitle>
        <CardDescription>
          Submit a custom price and stock request to test the system
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Order ID Field */}
          <div className="space-y-2">
            <Label htmlFor="orderId">Order ID</Label>
            <div className="flex gap-2">
              <Input
                id="orderId"
                type="text"
                placeholder="e.g., custom-abc123"
                value={orderId}
                onChange={(e) => setOrderId(e.target.value)}
                disabled={isSubmitting}
                className="flex-1"
              />
              <Button
                type="button"
                variant="outline"
                onClick={generateRandomOrderId}
                disabled={isSubmitting}
                title="Generate random Order ID"
              >
                Generate
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">
              Unique identifier for this order
            </p>
          </div>

          {/* Price and Stock Fields */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* Price Field */}
            <div className="space-y-2">
              <Label htmlFor="price" className="flex items-center gap-2">
                <DollarSign className="h-4 w-4" />
                Price
              </Label>
              <Input
                id="price"
                type="number"
                step="0.01"
                min="0"
                placeholder="e.g., 99.99"
                value={price}
                onChange={(e) => setPrice(e.target.value)}
                disabled={isSubmitting}
              />
            </div>

            {/* Stock Field */}
            <div className="space-y-2">
              <Label htmlFor="stock" className="flex items-center gap-2">
                <Package className="h-4 w-4" />
                Stock
              </Label>
              <Input
                id="stock"
                type="number"
                step="1"
                min="0"
                placeholder="e.g., 100"
                value={stock}
                onChange={(e) => setStock(e.target.value)}
                disabled={isSubmitting}
              />
            </div>
          </div>

          {/* Error Message */}
          {error && (
            <div className="bg-red-50 border border-red-200 rounded-md p-3">
              <p className="text-sm text-red-900">{error}</p>
            </div>
          )}

          {/* Success Message */}
          {success && (
            <div className="bg-green-50 border border-green-200 rounded-md p-3">
              <p className="text-sm text-green-900">{success}</p>
            </div>
          )}

          {/* Submit Button */}
          <Button
            type="submit"
            disabled={isSubmitting}
            className="w-full"
          >
            {isSubmitting ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Submitting...
              </>
            ) : (
              <>
                <Send className="mr-2 h-4 w-4" />
                Submit Order
              </>
            )}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
