'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Trash2 } from 'lucide-react';
import { clearAllQueues } from '@/lib/api';

interface EmptyQueuesButtonProps {
  onQueuesCleared?: () => void;
}

export function EmptyQueuesButton({ onQueuesCleared }: EmptyQueuesButtonProps) {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const handleClick = async () => {
    const confirmed = window.confirm(
      'Clear All Queues?\n\n' +
      'This will permanently clear:\n' +
      '• Connector price & stock queues\n' +
      '• Main app batch queue\n' +
      '• Channel app async processing\n' +
      '• All request state maps\n\n' +
      'This action cannot be undone.'
    );

    if (!confirmed) return;

    setIsLoading(true);
    setError(null);
    setSuccess(null);

    try {
      const result = await clearAllQueues();
      const totalCleared = result.totalCleared;
      setSuccess(`Successfully cleared ${totalCleared} items from all queues`);
      
      // Clear success message after 5 seconds
      setTimeout(() => setSuccess(null), 5000);
      
      // Notify parent to refresh data
      onQueuesCleared?.();
    } catch (err) {
      console.error('Failed to clear queues:', err);
      setError(err instanceof Error ? err.message : 'Failed to clear queues');
      
      // Clear error message after 10 seconds
      setTimeout(() => setError(null), 10000);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex flex-col gap-1">
      <Button
        onClick={handleClick}
        disabled={isLoading}
        variant="destructive"
        size="lg"
        className="w-full sm:w-auto"
      >
        <Trash2 className="h-4 w-4 mr-2" />
        {isLoading ? 'Clearing...' : 'Empty All Queues'}
      </Button>
      {error && (
        <p className="text-xs text-red-500 px-3">{error}</p>
      )}
      {success && (
        <p className="text-xs text-green-600 px-3">{success}</p>
      )}
    </div>
  );
}
