'use client';

import { Badge } from '@/components/ui/badge';

interface StatusBadgeProps {
  status: string | undefined;
}

export function StatusBadge({ status }: StatusBadgeProps) {
  if (!status) {
    return <Badge variant="outline">-</Badge>;
  }

  const upperStatus = status.toUpperCase();

  // Determine variant based on status
  if (upperStatus.includes('COMPLETED') || upperStatus.includes('SUCCESS')) {
    return <Badge className="bg-green-500 hover:bg-green-600">{status}</Badge>;
  }

  if (upperStatus.includes('PARTIAL')) {
    return <Badge className="bg-amber-500 hover:bg-amber-600 text-white">{status}</Badge>;
  }

  if (upperStatus.includes('PENDING') || upperStatus.includes('PROCESS') || upperStatus.includes('WAITING')) {
    return <Badge className="bg-yellow-500 hover:bg-yellow-600 text-black">{status}</Badge>;
  }

  if (upperStatus.includes('FAIL') || upperStatus.includes('ERROR')) {
    return <Badge variant="destructive">{status}</Badge>;
  }

  return <Badge variant="secondary">{status}</Badge>;
}
