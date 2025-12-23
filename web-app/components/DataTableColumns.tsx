'use client';

import { ColumnDef } from '@tanstack/react-table';
import { ArrowUpDown, ExternalLink } from 'lucide-react';
import { StatusBadge } from '@/components/StatusBadge';
import type { ChannelOrderDetails, OrderState } from '@/lib/api';
import { Button } from './ui/button';

export interface TableRowData {
  orderId: string;
  mainStatus?: string;
  mainState?: OrderState;
  connectorStatus?: string;
  channelPrice?: number;
  channelStock?: number;
  channelPriceStatus?: string;
  channelStockStatus?: string;
}

export function createColumns(
  onOrderClick: (orderId: string) => void
): ColumnDef<TableRowData>[] {
  return [
    {
      accessorKey: 'orderId',
      header: ({ column }) => {
        return (
          <button
            className="flex items-center gap-1 hover:text-foreground transition-colors font-medium"
            onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}
          >
            Order ID
            <ArrowUpDown className="h-4 w-4" />
          </button>
        );
      },
      cell: ({ row }) => (
        <span className="font-mono text-sm">{row.getValue('orderId')}</span>
      ),
    },
    {
      accessorKey: 'mainStatus',
      header: ({ column }) => {
        return (
          <button
            className="flex items-center gap-1 hover:text-foreground transition-colors font-medium"
            onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}
          >
            Main App (8082)
            <ArrowUpDown className="h-4 w-4" />
          </button>
        );
      },
      cell: ({ row }) => {
        const status = row.getValue('mainStatus') as string | undefined;
        return (
          <button
            onClick={() => onOrderClick(row.original.orderId)}
            className="group flex items-center gap-2 hover:opacity-90 cursor-pointer transition-all focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 rounded-md p-1 -m-1"
            title="Click to view order details"
          >
            <StatusBadge status={status} />
            <ExternalLink className="h-3.5 w-3.5 text-muted-foreground" />
          </button>
        );
      },
      sortingFn: (rowA, rowB) => {
        const statusOrder: Record<string, number> = {
          completed: 5,
          partial: 4,
          processing: 3,
          accepted: 2,
          failed: 1,
          error: 0,
        };
        const a = statusOrder[rowA.getValue('mainStatus') as string] ?? -1;
        const b = statusOrder[rowB.getValue('mainStatus') as string] ?? -1;
        return a - b;
      },
      filterFn: (row, columnId, filterValue) => {
        if (!filterValue || filterValue.length === 0) return true;
        const status = row.getValue('mainStatus') as string | undefined;
        return filterValue.includes(status);
      },
    },
    {
      accessorKey: 'connectorStatus',
      header: ({ column }) => {
        return (
          <Button
            variant={"ghost"}
            className="flex items-center gap-1 hover:text-foreground transition-colors font-medium"
            onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}
          >
            Connector (8080)
            <ArrowUpDown className="h-4 w-4" />
          </Button>
        );
      },
      cell: ({ row }) => {
        const status = row.getValue('connectorStatus') as string | undefined;
        return <StatusBadge status={status} />;
      },
      sortingFn: (rowA, rowB) => {
        const statusOrder: Record<string, number> = {
          completed: 5,
          partial: 4,
          processing: 3,
          accepted: 2,
          failed: 1,
          error: 0,
        };
        const a = statusOrder[rowA.getValue('connectorStatus') as string] ?? -1;
        const b = statusOrder[rowB.getValue('connectorStatus') as string] ?? -1;
        return a - b;
      },
      filterFn: (row, columnId, filterValue) => {
        if (!filterValue || filterValue.length === 0) return true;
        const status = row.getValue('connectorStatus') as string | undefined;
        return filterValue.includes(status);
      },
    },
    {
      accessorKey: 'channelPrice',
      header: ({ column }) => {
        return (
          <button
            className="flex items-center gap-1 hover:text-foreground transition-colors font-medium"
            onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}
          >
            Channel Price (8081)
            <ArrowUpDown className="h-4 w-4" />
          </button>
        );
      },
      cell: ({ row }) => {
        const price = row.original.channelPrice;
        const priceStatus = row.original.channelPriceStatus;

        if (priceStatus || price !== undefined) {
          return (
            <div className="flex items-center gap-2">
              {price !== undefined && price !== null ? (
                <span className="font-mono">${price.toFixed(2)}</span>
              ) : (
                <span className="text-muted-foreground">-</span>
              )}
              <StatusBadge status={priceStatus} />
            </div>
          );
        }
        return <StatusBadge status={undefined} />;
      },
      sortingFn: (rowA, rowB) => {
        const a = rowA.original.channelPrice ?? -Infinity;
        const b = rowB.original.channelPrice ?? -Infinity;
        return a - b;
      },
      filterFn: (row, columnId, filterValue) => {
        if (!filterValue || (!filterValue.min && !filterValue.max)) return true;
        const value = row.original.channelPrice;
        if (value === undefined || value === null) return false;
        if (filterValue.min !== undefined && value < filterValue.min) return false;
        if (filterValue.max !== undefined && value > filterValue.max) return false;
        return true;
      },
    },
    {
      accessorKey: 'channelStock',
      header: ({ column }) => {
        return (
          <button
            className="flex items-center gap-1 hover:text-foreground transition-colors font-medium"
            onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}
          >
            Channel Stock (8081)
            <ArrowUpDown className="h-4 w-4" />
          </button>
        );
      },
      cell: ({ row }) => {
        const stock = row.original.channelStock;
        const stockStatus = row.original.channelStockStatus;

        if (stockStatus || stock !== undefined) {
          return (
            <div className="flex items-center gap-2">
              {stock !== undefined && stock !== null ? (
                <span className="font-mono">{stock}</span>
              ) : (
                <span className="text-muted-foreground">-</span>
              )}
              <StatusBadge status={stockStatus} />
            </div>
          );
        }
        return <StatusBadge status={undefined} />;
      },
      sortingFn: (rowA, rowB) => {
        const a = rowA.original.channelStock ?? -Infinity;
        const b = rowB.original.channelStock ?? -Infinity;
        return a - b;
      },
      filterFn: (row, columnId, filterValue) => {
        if (!filterValue || (!filterValue.min && !filterValue.max)) return true;
        const value = row.original.channelStock;
        if (value === undefined || value === null) return false;
        if (filterValue.min !== undefined && value < filterValue.min) return false;
        if (filterValue.max !== undefined && value > filterValue.max) return false;
        return true;
      },
    },
  ];
}
