'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { triggerBurst } from '@/lib/api';

interface BurstButtonProps {
  onBurstTriggered?: () => void;
}

export function BurstButton({ onBurstTriggered }: BurstButtonProps) {
  const [isLoading, setIsLoading] = useState(false);

  const handleClick = async () => {
    setIsLoading(true);
    try {
      await triggerBurst(20);
      // Wait a bit before allowing next burst
      setTimeout(() => {
        setIsLoading(false);
        onBurstTriggered?.();
      }, 500);
    } catch (error) {
      console.error('Failed to trigger burst:', error);
      setIsLoading(false);
    }
  };

  return (
    <Button 
      onClick={handleClick} 
      disabled={isLoading}
      size="lg"
      className="w-full sm:w-auto"
    >
      {isLoading ? 'Triggering...' : 'Trigger Burst (20 updates)'}
    </Button>
  );
}
