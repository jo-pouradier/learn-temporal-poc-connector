'use client';

import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet';
import { StatusBadge } from '@/components/StatusBadge';
import type { OrderState, StateEvent } from '@/lib/api';
import { fetchOrderHistory } from '@/lib/api';
import { Check, X, Clock, Package, DollarSign, AlertCircle, Copy, CheckCircle2, History } from 'lucide-react';
import { useState, useEffect } from 'react';

interface OrderDetailDrawerProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  orderState: OrderState | null;
}

export function OrderDetailDrawer({ open, onOpenChange, orderState }: OrderDetailDrawerProps) {
  const [copiedCorrelationId, setCopiedCorrelationId] = useState(false);
  const [eventHistory, setEventHistory] = useState<StateEvent[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);

  useEffect(() => {
    if (open && orderState?.orderId) {
      setLoadingHistory(true);
      fetchOrderHistory(orderState.orderId)
        .then(history => {
          setEventHistory(history);
          setLoadingHistory(false);
        })
        .catch(err => {
          console.error('Failed to load history:', err);
          setLoadingHistory(false);
        });
    }
  }, [open, orderState?.orderId]);

  if (!orderState) {
    return null;
  }

  const formatTimestamp = (timestamp: string | null) => {
    if (!timestamp) return '-';
    const date = new Date(timestamp);
    return date.toLocaleString();
  };

  const formatRelativeTime = (timestamp: string | null) => {
    if (!timestamp) return '';
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffSec = Math.floor(diffMs / 1000);
    
    if (diffSec < 60) return `${diffSec}s ago`;
    const diffMin = Math.floor(diffSec / 60);
    if (diffMin < 60) return `${diffMin}m ago`;
    const diffHr = Math.floor(diffMin / 60);
    return `${diffHr}h ago`;
  };

  const copyCorrelationId = () => {
    if (orderState.correlationId) {
      navigator.clipboard.writeText(orderState.correlationId);
      setCopiedCorrelationId(true);
      setTimeout(() => setCopiedCorrelationId(false), 2000);
    }
  };

  const truncateId = (id: string, length = 8) => {
    if (id.length <= length) return id;
    return `${id.substring(0, length)}...`;
  };

  // Get event icon
  const getEventIcon = (eventType: string) => {
    const icons: Record<string, string> = {
      'REQUEST_RECEIVED': '📥',
      'REQUEST_ACCEPTED': '✅',
      'REQUEST_SUBMITTED': '📤',
      'BATCH_QUEUED': '📦',
      'BATCH_SENT': '🚀',
      'QUEUE_FULL': '⚠️',
      'PROCESSING_STARTED': '⚙️',
      'JOB_SENT_TO_CHANNEL': '📡',
      'STATUS_CHANGE': '🔄',
      'CALLBACK_RECEIVED': '📞',
      'CALLBACK_SENT': '📲',
      'ERROR': '❌',
      'VALIDATION_ERROR': '⚠️',
      'NETWORK_ERROR': '🌐'
    };
    return icons[eventType] || '•';
  };

  // Get status color
  const getStatusColor = (status: string) => {
    const upperStatus = status.toUpperCase();
    if (upperStatus.includes('COMPLETED')) return 'text-green-600 bg-green-50 border-green-200';
    if (upperStatus.includes('PENDING') || upperStatus.includes('PROCESS') || 
        upperStatus.includes('QUEUED') || upperStatus.includes('ACCEPTED')) 
      return 'text-yellow-600 bg-yellow-50 border-yellow-200';
    if (upperStatus.includes('FAIL') || upperStatus.includes('ERROR')) 
      return 'text-red-600 bg-red-50 border-red-200';
    return 'text-blue-600 bg-blue-50 border-blue-200';
  };

  // Format event type
  const formatEventType = (eventType: string) => {
    return eventType.replace(/_/g, ' ').toLowerCase()
      .split(' ')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  };

  // Timeline steps (old basic timeline - kept for backward compatibility)
  const timelineSteps = [
    { label: 'Submitted', timestamp: orderState.submittedAt, completed: !!orderState.submittedAt },
    { label: 'Accepted', timestamp: orderState.acceptedAt, completed: !!orderState.acceptedAt },
    { label: 'Sent to Connector', timestamp: orderState.acceptedAt, completed: orderState.status !== 'pending' && orderState.status !== 'accepted' },
    { label: 'Completed', timestamp: orderState.completedAt, completed: !!orderState.completedAt },
  ];

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-[95vw] sm:w-[700px] sm:max-w-[700px] overflow-y-auto">
        <SheetHeader>
          <SheetTitle className="flex items-center gap-2">
            <Package className="h-5 w-5" />
            Order: {orderState.orderId}
          </SheetTitle>
          <SheetDescription>
            Complete order details from Main App
          </SheetDescription>
        </SheetHeader>

        <div className="mt-6 space-y-6">
          {/* Status Section */}
          <div className="space-y-2">
            <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
              Current Status
            </h3>
            <div className="flex items-center gap-2">
              <StatusBadge status={orderState.status} />
            </div>
          </div>

          {/* Error Section - Only show if there's an error */}
          {orderState.error && (
            <div className="space-y-2">
              <h3 className="text-sm font-semibold text-red-600 uppercase tracking-wide flex items-center gap-2">
                <AlertCircle className="h-4 w-4" />
                Error
              </h3>
              <div className="bg-red-50 border border-red-200 rounded-md p-3">
                <p className="text-sm text-red-900">{orderState.error}</p>
              </div>
            </div>
          )}

          {/* Timeline Section - Event History */}
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide flex items-center gap-2">
              <History className="h-4 w-4" />
              Event History
            </h3>
            {loadingHistory ? (
              <div className="text-center py-8 text-muted-foreground">
                Loading event history...
              </div>
            ) : eventHistory.length > 0 ? (
              <div className="space-y-0">
                {eventHistory.map((event, index) => {
                  const isLast = index === eventHistory.length - 1;
                  const statusColor = getStatusColor(event.status);
                  
                  return (
                    <div key={index} className="relative flex gap-3 pb-6 last:pb-0">
                      {/* Timeline line */}
                      {!isLast && (
                        <div className="absolute left-4 top-8 bottom-0 w-0.5 bg-gray-200" />
                      )}
                      
                      {/* Icon */}
                      <div className="relative flex-shrink-0 w-8 h-8 rounded-full bg-white border-2 border-gray-200 flex items-center justify-center text-sm z-10">
                        {getEventIcon(event.eventType)}
                      </div>
                      
                      {/* Content */}
                      <div className="flex-1 min-w-0 pt-0.5">
                        <div className="flex items-start justify-between gap-2 mb-1">
                          <p className="text-sm font-medium text-foreground">
                            {formatEventType(event.eventType)}
                          </p>
                          <p className="text-xs text-muted-foreground whitespace-nowrap">
                            {formatRelativeTime(event.timestamp)}
                          </p>
                        </div>
                        
                        <div className={`inline-block px-2 py-0.5 rounded text-xs font-medium border ${statusColor} mb-1`}>
                          {event.status}
                        </div>
                        
                        {event.details && (
                          <p className="text-xs text-muted-foreground mt-1 leading-relaxed">
                            {event.details}
                          </p>
                        )}
                        
                        <p className="text-xs text-muted-foreground mt-1">
                          {formatTimestamp(event.timestamp)}
                        </p>
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className="text-center py-8 text-muted-foreground">
                No event history available
              </div>
            )}
          </div>

          {/* Completion Status Section */}
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide flex items-center gap-2">
              <CheckCircle2 className="h-4 w-4" />
              Completion Status
            </h3>
            <div className="grid grid-cols-2 gap-3">
              <div className={`border rounded-md p-3 ${orderState.priceCompleted ? 'bg-green-50 border-green-200' : 'bg-gray-50 border-gray-200'}`}>
                <div className="flex items-center gap-2">
                  {orderState.priceCompleted ? (
                    <Check className="h-4 w-4 text-green-600" />
                  ) : (
                    <X className="h-4 w-4 text-gray-400" />
                  )}
                  <span className={`text-sm font-medium ${orderState.priceCompleted ? 'text-green-900' : 'text-gray-600'}`}>
                    Price
                  </span>
                </div>
              </div>
              <div className={`border rounded-md p-3 ${orderState.stockCompleted ? 'bg-green-50 border-green-200' : 'bg-gray-50 border-gray-200'}`}>
                <div className="flex items-center gap-2">
                  {orderState.stockCompleted ? (
                    <Check className="h-4 w-4 text-green-600" />
                  ) : (
                    <X className="h-4 w-4 text-gray-400" />
                  )}
                  <span className={`text-sm font-medium ${orderState.stockCompleted ? 'text-green-900' : 'text-gray-600'}`}>
                    Stock
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* Original Request Section */}
          {orderState.originalRequest && (
            <div className="space-y-3">
              <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
                Original Request
              </h3>
              <div className="border rounded-md p-3 space-y-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <DollarSign className="h-4 w-4 text-muted-foreground" />
                    <span className="text-sm text-muted-foreground">Price</span>
                  </div>
                  <span className="text-sm font-mono font-medium">
                    ${orderState.originalRequest.price.toFixed(2)}
                  </span>
                </div>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Package className="h-4 w-4 text-muted-foreground" />
                    <span className="text-sm text-muted-foreground">Stock</span>
                  </div>
                  <span className="text-sm font-mono font-medium">
                    {orderState.originalRequest.stock} units
                  </span>
                </div>
              </div>
            </div>
          )}

          {/* Correlation ID Section */}
          <div className="space-y-2">
            <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
              Correlation ID
            </h3>
            <div className="flex items-center gap-2">
              <code className="flex-1 bg-muted px-3 py-2 rounded text-xs font-mono break-all">
                {orderState.correlationId}
              </code>
              <button
                onClick={copyCorrelationId}
                className="p-2 hover:bg-muted rounded transition-colors"
                title="Copy correlation ID"
              >
                {copiedCorrelationId ? (
                  <Check className="h-4 w-4 text-green-600" />
                ) : (
                  <Copy className="h-4 w-4 text-muted-foreground" />
                )}
              </button>
            </div>
          </div>

          {/* Callbacks Section */}
          {(orderState.priceCallback || orderState.stockCallback) && (
            <div className="space-y-3">
              <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
                Callbacks Received
              </h3>
              <div className="space-y-3">
                {orderState.priceCallback && (
                  <div>
                    <p className="text-sm font-medium mb-2">Price Callback</p>
                    <pre className="bg-muted p-3 rounded text-xs overflow-x-auto">
                      {JSON.stringify(orderState.priceCallback, null, 2)}
                    </pre>
                  </div>
                )}
                {orderState.stockCallback && (
                  <div>
                    <p className="text-sm font-medium mb-2">Stock Callback</p>
                    <pre className="bg-muted p-3 rounded text-xs overflow-x-auto">
                      {JSON.stringify(orderState.stockCallback, null, 2)}
                    </pre>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Final Response Section */}
          {orderState.finalResponse && (
            <div className="space-y-3">
              <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
                Final Response
              </h3>
              <pre className="bg-muted p-3 rounded text-xs overflow-x-auto">
                {JSON.stringify(orderState.finalResponse, null, 2)}
              </pre>
            </div>
          )}

          {/* Timestamps Detail Section */}
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
              Detailed Timestamps
            </h3>
            <div className="border rounded-md divide-y">
              <div className="p-3 flex justify-between items-center">
                <span className="text-sm text-muted-foreground">Submitted</span>
                <span className="text-sm font-mono">{formatTimestamp(orderState.submittedAt)}</span>
              </div>
              <div className="p-3 flex justify-between items-center">
                <span className="text-sm text-muted-foreground">Accepted</span>
                <span className="text-sm font-mono">{formatTimestamp(orderState.acceptedAt)}</span>
              </div>
              <div className="p-3 flex justify-between items-center">
                <span className="text-sm text-muted-foreground">Completed</span>
                <span className="text-sm font-mono">{formatTimestamp(orderState.completedAt)}</span>
              </div>
            </div>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}
