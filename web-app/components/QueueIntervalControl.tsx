'use client';

import { useState, useEffect, useCallback } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Timer, Check } from 'lucide-react';
import { getQueueInterval, setQueueInterval, durationToMs, type QueueIntervalConfig } from '@/lib/api';

interface QueueIntervalControlProps {
  onIntervalChange?: (intervalMs: number) => void;
}

export function QueueIntervalControl({ onIntervalChange }: QueueIntervalControlProps) {
  const [config, setConfig] = useState<QueueIntervalConfig | null>(null);
  const [inputValue, setInputValue] = useState<string>('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const fetchConfig = useCallback(async () => {
    try {
      const data = await getQueueInterval();
      setConfig(data);
      // Convert duration string to milliseconds for display
      const ms = durationToMs(data.interval);
      setInputValue(ms.toString());
    } catch (err) {
      console.error('Failed to fetch queue interval:', err);
    }
  }, []);

  useEffect(() => {
    fetchConfig();
  }, [fetchConfig]);

  const handleApply = async () => {
    const ms = parseInt(inputValue, 10);
    if (isNaN(ms) || ms < 10 || ms > 60000) {
      setError('Interval must be between 10ms and 60000ms');
      return;
    }

    setIsLoading(true);
    setError(null);
    setSuccess(false);

    try {
      await setQueueInterval(ms);
      await fetchConfig();
      setSuccess(true);
      onIntervalChange?.(ms);
      
      // Clear success after 2 seconds
      setTimeout(() => setSuccess(false), 2000);
    } catch (err) {
      console.error('Failed to set queue interval:', err);
      setError(err instanceof Error ? err.message : 'Failed to update');
    } finally {
      setIsLoading(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleApply();
    }
  };

  const hasChanged = config && inputValue !== durationToMs(config.interval).toString();

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-2 px-3 py-2 rounded-lg border bg-card">
        <Timer className="h-4 w-4 text-green-500" />
        
        <span className="text-xs text-muted-foreground whitespace-nowrap">Dequeue:</span>
        
        <Input
          type="number"
          min={10}
          max={60000}
          step={100}
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyDown={handleKeyDown}
          className="w-20 h-7 text-sm"
          disabled={isLoading}
        />
        
        <span className="text-xs text-muted-foreground">ms</span>
        
        {hasChanged && (
          <Button
            size="sm"
            onClick={handleApply}
            disabled={isLoading}
            className="h-7"
          >
            {success ? <Check className="h-3 w-3" /> : 'Apply'}
          </Button>
        )}
        
        {!hasChanged && config && (
          <span className="text-xs text-green-600">
            Active
          </span>
        )}
      </div>
      
      {error && (
        <p className="text-xs text-red-500 px-3">{error}</p>
      )}
    </div>
  );
}
