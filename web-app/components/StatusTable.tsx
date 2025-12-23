'use client';

import { useState, useMemo } from 'react';
import { OrderDetailDrawer } from '@/components/OrderDetailDrawer';
import { DataTable } from '@/components/DataTable';
import { createColumns, type TableRowData } from '@/components/DataTableColumns';
import type { OrderState, ChannelOrderDetails } from '@/lib/api';

interface StatusTableProps {
  mainAppStates: OrderState[];
  connectorStates: OrderState[];
  channelDetails: Map<string, ChannelOrderDetails>;
  allOrderIds: string[];
  // Pagination props
  pageIndex?: number;
  pageSize?: number;
  pageCount?: number;
  totalRows?: number;
  currentFilter?: string;
  onPaginationChange?: (pagination: { pageIndex: number; pageSize: number }) => void;
  onFilterChange?: (filter: string) => void;
}

export function StatusTable({
  mainAppStates,
  connectorStates,
  channelDetails,
  allOrderIds,
  pageIndex,
  pageSize,
  pageCount,
  totalRows,
  currentFilter,
  onPaginationChange,
  onFilterChange,
}: StatusTableProps) {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);

  // Create lookup maps for quick access
  const mainStatusMap = useMemo(() => new Map(mainAppStates.map(s => [s.orderId, s.status])), [mainAppStates]);
  const mainStateMap = useMemo(() => new Map(mainAppStates.map(s => [s.orderId, s])), [mainAppStates]);
  const connectorStatusMap = useMemo(() => new Map(connectorStates.map(s => [s.orderId, s.status])), [connectorStates]);

  const handleOrderClick = (orderId: string) => {
    setSelectedOrderId(orderId);
    setDrawerOpen(true);
  };

  const selectedOrderState = selectedOrderId ? mainStateMap.get(selectedOrderId) || null : null;

  // Transform data for DataTable
  const tableData: TableRowData[] = useMemo(() => {
    return allOrderIds.map((orderId) => {
      const channel = channelDetails.get(orderId);
      return {
        orderId,
        mainStatus: mainStatusMap.get(orderId),
        mainState: mainStateMap.get(orderId),
        connectorStatus: connectorStatusMap.get(orderId),
        channelPrice: channel?.price,
        channelStock: channel?.stock,
        channelPriceStatus: channel?.priceStatus,
        channelStockStatus: channel?.stockStatus,
      };
    });
  }, [allOrderIds, channelDetails, mainStatusMap, mainStateMap, connectorStatusMap]);

  // Create columns with click handler
  const columns = useMemo(() => {
    return createColumns(handleOrderClick);
  }, []);

  return (
    <>
      <DataTable 
        columns={columns} 
        data={tableData}
        pageIndex={pageIndex}
        pageSize={pageSize}
        pageCount={pageCount}
        totalRows={totalRows}
        currentFilter={currentFilter}
        onPaginationChange={onPaginationChange}
        onFilterChange={onFilterChange}
      />
      
      <OrderDetailDrawer
        open={drawerOpen}
        onOpenChange={setDrawerOpen}
        orderState={selectedOrderState}
      />
    </>
  );
}
