'use client';

import { useState, useEffect, useCallback } from 'react';
import { useSearchParams, useRouter, usePathname } from 'next/navigation';
import { BurstButton } from '@/components/BurstButton';
import { CustomOrderForm } from '@/components/CustomOrderForm';
import { PollingToggle } from '@/components/PollingToggle';
import { LoadGenerator } from '@/components/LoadGenerator';
import { QueueIntervalControl } from '@/components/QueueIntervalControl';
import { EmptyQueuesButton } from '@/components/EmptyQueuesButton';
import { StatusTable } from '@/components/StatusTable';
import { fetchAllDashboardData, type DashboardData } from '@/lib/api';

const POLL_INTERVAL = 1000; // 1 second
const DEFAULT_PAGE_SIZE = 50;

export function Dashboard() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  
  // Read pagination and filter from URL
  const urlPage = parseInt(searchParams.get('page') ?? '0', 10);
  const urlSize = parseInt(searchParams.get('size') ?? String(DEFAULT_PAGE_SIZE), 10);
  const urlFilter = searchParams.get('filter') ?? '';

  const [data, setData] = useState<DashboardData>({
    mainAppStates: [],
    connectorStates: [],
    channelDetails: new Map(),
    allOrderIds: [],
    mainPagination: { page: 0, size: DEFAULT_PAGE_SIZE, totalElements: 0, totalPages: 0 },
    connectorPagination: { page: 0, size: DEFAULT_PAGE_SIZE, totalElements: 0, totalPages: 0 },
  });
  const [isLoading, setIsLoading] = useState(true);
  const [isPollingEnabled, setIsPollingEnabled] = useState(true);
  const [lastUpdate, setLastUpdate] = useState<Date | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Update URL when pagination/filter changes
  const updateUrl = useCallback((page: number, size: number, filter: string) => {
    const params = new URLSearchParams();
    if (page > 0) params.set('page', String(page));
    if (size !== DEFAULT_PAGE_SIZE) params.set('size', String(size));
    if (filter) params.set('filter', filter);

    const queryString = params.toString();
    const newUrl = queryString ? `${pathname}?${queryString}` : pathname;
    router.replace(newUrl, { scroll: false });
  }, [pathname, router]);

  const refreshData = useCallback(async () => {
    try {
      setError(null);
      const newData = await fetchAllDashboardData({ 
        page: urlPage, 
        size: urlSize, 
        filter: urlFilter || undefined
      });
      setData(newData);
      setLastUpdate(new Date());
    } catch (err) {
      const errorMsg = err instanceof Error ? err.message : 'Unknown error';
      console.error('[Dashboard] Failed to fetch data:', errorMsg, err);
      setError(errorMsg);
    } finally {
      setIsLoading(false);
    }
  }, [urlPage, urlSize, urlFilter]);

  // Handle pagination change from table
  const handlePaginationChange = useCallback((newPagination: { pageIndex: number; pageSize: number }) => {
    updateUrl(newPagination.pageIndex, newPagination.pageSize, urlFilter);
  }, [updateUrl, urlFilter]);

  // Handle filter change
  const handleFilterChange = useCallback((newFilter: string) => {
    // Reset to page 0 when filter changes
    updateUrl(0, urlSize, newFilter);
  }, [updateUrl, urlSize]);

  // Initial fetch and polling
  useEffect(() => {
    refreshData();
    
    if (!isPollingEnabled) return;
    
    const interval = setInterval(refreshData, POLL_INTERVAL);
    return () => clearInterval(interval);
  }, [refreshData, isPollingEnabled]);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">Distributed Channel Manager</h1>
          <p className="text-muted-foreground">POC Dashboard - Real-time Status Monitor</p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <PollingToggle 
            enabled={isPollingEnabled}
            onToggle={setIsPollingEnabled}
          />
          <LoadGenerator />
          <QueueIntervalControl />
          <BurstButton onBurstTriggered={refreshData} />
          <EmptyQueuesButton onQueuesCleared={refreshData} />
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <StatCard 
          title="Total Orders (Main)" 
          value={data.mainPagination?.totalElements ?? 0} 
        />
        <StatCard 
          title="Total Orders (Connector)" 
          value={data.connectorPagination?.totalElements ?? 0} 
        />
        <StatCard 
          title="Current Page" 
          value={`${data.mainAppStates.length} shown`}
          subtitle={`Page ${urlPage + 1}${urlFilter ? ` (${urlFilter})` : ''}`}
        />
        <StatCard 
          title="Last Update" 
          value={lastUpdate ? lastUpdate.toLocaleTimeString() : '-'} 
          isText
        />
      </div>

      {/* Custom Order Form */}
      <CustomOrderForm onOrderSubmitted={refreshData} />

      {/* Error Message */}
      {error && (
        <div className="rounded-lg border border-destructive bg-destructive/10 p-4 text-sm">
          <div className="font-semibold text-destructive">Error loading data:</div>
          <div className="text-muted-foreground mt-1">{error}</div>
          <button 
            onClick={() => refreshData()}
            className="mt-2 text-sm underline hover:no-underline"
          >
            Retry
          </button>
        </div>
      )}

      {/* Table */}
      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
        </div>
      ) : (
        <StatusTable
          mainAppStates={data.mainAppStates}
          connectorStates={data.connectorStates}
          channelDetails={data.channelDetails}
          allOrderIds={data.allOrderIds}
          pageIndex={urlPage}
          pageSize={urlSize}
          pageCount={data.mainPagination?.totalPages ?? 0}
          totalRows={data.mainPagination?.totalElements ?? 0}
          currentFilter={urlFilter}
          onPaginationChange={handlePaginationChange}
          onFilterChange={handleFilterChange}
        />
      )}
    </div>
  );
}

interface StatCardProps {
  title: string;
  value: string | number;
  subtitle?: string;
  isText?: boolean;
}

function StatCard({ title, value, subtitle, isText }: StatCardProps) {
  return (
    <div className="rounded-lg border bg-card p-4">
      <p className="text-sm text-muted-foreground">{title}</p>
      <p className={`font-bold ${isText ? 'text-lg' : 'text-2xl'}`}>{value}</p>
      {subtitle && <p className="text-xs text-muted-foreground">{subtitle}</p>}
    </div>
  );
}
