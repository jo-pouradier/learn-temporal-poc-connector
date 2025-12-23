'use client';

import { useState, useEffect, useCallback } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Zap, Square, Activity } from 'lucide-react';
import { startLoad, stopLoad, getLoadStatus, type LoadStatus } from '@/lib/api';

interface LoadGeneratorProps {
  onStatusChange?: (active: boolean) => void;
}

export function LoadGenerator({ onStatusChange }: LoadGeneratorProps) {
  const [status, setStatus] = useState<LoadStatus>({ active: false, rate: 0, totalGenerated: 0 });
  const [targetRate, setTargetRate] = useState<number>(10);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Poll status every 2 seconds when active
  const fetchStatus = useCallback(async () => {
    try {
      const newStatus = await getLoadStatus();
      setStatus(newStatus);
    } catch (err) {
      console.error('Failed to fetch load status:', err);
    }
  }, []);

  useEffect(() => {
    fetchStatus();
    
    const interval = setInterval(fetchStatus, 2000);
    return () => clearInterval(interval);
  }, [fetchStatus]);

  const handleStart = async () => {
    setIsLoading(true);
    setError(null);
    
    try {
      await startLoad(targetRate);
      await fetchStatus();
      onStatusChange?.(true);
    } catch (err) {
      console.error('Failed to start load:', err);
      setError(err instanceof Error ? err.message : 'Failed to start');
    } finally {
      setIsLoading(false);
    }
  };

  const handleStop = async () => {
    setIsLoading(true);
    setError(null);
    
    try {
      await stopLoad();
      await fetchStatus();
      onStatusChange?.(false);
    } catch (err) {
      console.error('Failed to stop load:', err);
      setError(err instanceof Error ? err.message : 'Failed to stop');
    } finally {
      setIsLoading(false);
    }
  };

  const handleRateChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = parseInt(e.target.value, 10);
    if (!isNaN(value) && value >= 1 && value <= 1000) {
      setTargetRate(value);
    }
  };

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-2 px-3 py-2 rounded-lg border bg-card">
        <Zap className={`h-4 w-4 ${status.active ? 'text-yellow-500' : 'text-muted-foreground'}`} />
        
        {!status.active ? (
          <>
            <Input
              type="number"
              min={1}
              max={1000}
              value={targetRate}
              onChange={handleRateChange}
              className="w-20 h-7 text-sm"
              disabled={isLoading}
            />
            <span className="text-xs text-muted-foreground">req/s</span>
            <Button
              size="sm"
              onClick={handleStart}
              disabled={isLoading}
              className="h-7"
            >
              <Activity className="h-3 w-3 mr-1" />
              Start
            </Button>
          </>
        ) : (
          <>
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-green-600">
                {status.rate} req/s
              </span>
              <span className="text-xs text-muted-foreground">
                ({status.totalGenerated.toLocaleString()} total)
              </span>
            </div>
            <Button
              size="sm"
              variant="destructive"
              onClick={handleStop}
              disabled={isLoading}
              className="h-7"
            >
              <Square className="h-3 w-3 mr-1" />
              Stop
            </Button>
          </>
        )}
      </div>
      
      {error && (
        <p className="text-xs text-red-500 px-3">{error}</p>
      )}
    </div>
  );
}
