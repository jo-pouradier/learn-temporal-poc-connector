'use client';

import { useState } from 'react';
import { Switch } from '@/components/ui/switch';
import { Zap } from 'lucide-react';
import { toggleBackgroundLoad } from '@/lib/api';

interface BackgroundLoadToggleProps {
  onToggle?: (active: boolean) => void;
}

export function BackgroundLoadToggle({ onToggle }: BackgroundLoadToggleProps) {
  const [isActive, setIsActive] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleToggle = async (checked: boolean) => {
    setIsLoading(true);
    setError(null);
    
    try {
      const action = checked ? 'activate' : 'deactivate';
      await toggleBackgroundLoad(action);
      setIsActive(checked);
      onToggle?.(checked);
    } catch (err) {
      console.error('Failed to toggle background load:', err);
      setError(err instanceof Error ? err.message : 'Failed to toggle');
      // Revert state on error
      setIsActive(!checked);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-center gap-2 px-3 py-2 rounded-lg border bg-card">
        <Zap className={`h-4 w-4 ${isActive ? 'text-yellow-500' : 'text-muted-foreground'}`} />
        <Switch 
          checked={isActive} 
          onCheckedChange={handleToggle}
          disabled={isLoading}
          aria-label="Toggle auto-generate orders"
        />
        <label 
          className={`text-sm font-medium select-none ${isLoading ? 'opacity-50' : 'cursor-pointer'}`}
          onClick={() => !isLoading && handleToggle(!isActive)}
        >
          Auto-generate {isActive ? <span className="text-green-600">(1/sec)</span> : <span className="text-muted-foreground">(off)</span>}
        </label>
      </div>
      {error && (
        <p className="text-xs text-red-500 px-3">{error}</p>
      )}
    </div>
  );
}
