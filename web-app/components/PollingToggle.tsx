'use client';

import { Switch } from '@/components/ui/switch';
import { RefreshCw } from 'lucide-react';

interface PollingToggleProps {
  enabled: boolean;
  onToggle: (enabled: boolean) => void;
}

export function PollingToggle({ enabled, onToggle }: PollingToggleProps) {
  return (
    <div className="flex items-center gap-2 px-3 py-2 rounded-lg border bg-card">
      <RefreshCw className={`h-4 w-4 text-muted-foreground ${enabled ? 'animate-spin' : ''}`} />
      <Switch 
        checked={enabled} 
        onCheckedChange={onToggle}
        aria-label="Toggle auto-refresh"
      />
      <label className="text-sm font-medium cursor-pointer select-none" onClick={() => onToggle(!enabled)}>
        Auto-refresh {enabled ? <span className="text-green-600">(1s)</span> : <span className="text-muted-foreground">(paused)</span>}
      </label>
    </div>
  );
}
