'use client';

import * as React from 'react';
import {
  ColumnDef,
  SortingState,
  ColumnFiltersState,
  PaginationState,
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  getFilteredRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { Search, Filter, X, ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Checkbox } from '@/components/ui/checkbox';
import { Badge } from '@/components/ui/badge';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';

interface DataTableProps<TData, TValue> {
  columns: ColumnDef<TData, TValue>[];
  data: TData[];
  // Server-side pagination props
  pageCount?: number;
  pageIndex?: number;
  pageSize?: number;
  totalRows?: number;
  currentFilter?: string;
  onPaginationChange?: (pagination: { pageIndex: number; pageSize: number }) => void;
  onFilterChange?: (filter: string) => void;
}

// Status Filter Dropdown Component
function StatusFilterDropdown({
  column,
  title,
  options,
}: {
  column: any;
  title: string;
  options: string[];
}) {
  const filterValue = (column?.getFilterValue() as string[]) ?? [];

  const handleToggle = (value: string) => {
    const current = filterValue;
    const updated = current.includes(value)
      ? current.filter((v) => v !== value)
      : [...current, value];
    column?.setFilterValue(updated.length > 0 ? updated : undefined);
  };

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="outline" size="sm" className="h-8 border-dashed">
          <Filter className="mr-2 h-4 w-4" />
          {title}
          {filterValue.length > 0 && (
            <Badge variant="secondary" className="ml-2 rounded-sm px-1 font-normal">
              {filterValue.length}
            </Badge>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[200px] p-2" align="start">
        <div className="space-y-2">
          <div className="text-sm font-medium">{title}</div>
          {options.length === 0 ? (
            <div className="text-sm text-muted-foreground">No options</div>
          ) : (
            options.map((option) => (
              <div key={option} className="flex items-center space-x-2">
                <Checkbox
                  id={`${title}-${option}`}
                  checked={filterValue.includes(option)}
                  onCheckedChange={() => handleToggle(option)}
                />
                <label
                  htmlFor={`${title}-${option}`}
                  className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70 cursor-pointer"
                >
                  {option}
                </label>
              </div>
            ))
          )}
        </div>
      </PopoverContent>
    </Popover>
  );
}

// Range Filter Component
function RangeFilter({
  column,
  title,
  prefix = '',
}: {
  column: any;
  title: string;
  prefix?: string;
}) {
  const filterValue = (column?.getFilterValue() as { min?: number; max?: number }) ?? {};
  const [min, setMin] = React.useState<string>(filterValue.min?.toString() ?? '');
  const [max, setMax] = React.useState<string>(filterValue.max?.toString() ?? '');

  const applyFilter = () => {
    const minNum = min ? parseFloat(min) : undefined;
    const maxNum = max ? parseFloat(max) : undefined;
    
    if (minNum !== undefined || maxNum !== undefined) {
      column?.setFilterValue({ min: minNum, max: maxNum });
    } else {
      column?.setFilterValue(undefined);
    }
  };

  const clearFilter = () => {
    setMin('');
    setMax('');
    column?.setFilterValue(undefined);
  };

  const hasFilter = filterValue.min !== undefined || filterValue.max !== undefined;

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="outline" size="sm" className="h-8 border-dashed">
          <Filter className="mr-2 h-4 w-4" />
          {title}
          {hasFilter && (
            <Badge variant="secondary" className="ml-2 rounded-sm px-1 font-normal">
              1
            </Badge>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[240px] p-3" align="start">
        <div className="space-y-3">
          <div className="text-sm font-medium">{title} Range</div>
          <div className="grid gap-2">
            <div className="flex items-center gap-2">
              <Input
                type="number"
                placeholder="Min"
                value={min}
                onChange={(e) => setMin(e.target.value)}
                className="h-8"
              />
              <span className="text-muted-foreground">to</span>
              <Input
                type="number"
                placeholder="Max"
                value={max}
                onChange={(e) => setMax(e.target.value)}
                className="h-8"
              />
            </div>
          </div>
          <div className="flex gap-2">
            <Button size="sm" onClick={applyFilter} className="flex-1">
              Apply
            </Button>
            <Button size="sm" variant="outline" onClick={clearFilter} className="flex-1">
              Clear
            </Button>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}

export function DataTable<TData, TValue>({
  columns,
  data,
  pageCount,
  pageIndex = 0,
  pageSize = 50,
  totalRows,
  currentFilter,
  onPaginationChange,
  onFilterChange,
}: DataTableProps<TData, TValue>) {
  const [sorting, setSorting] = React.useState<SortingState>([]);
  const [columnFilters, setColumnFilters] = React.useState<ColumnFiltersState>([]);
  const [globalFilter, setGlobalFilter] = React.useState('');

  // Determine if we're in server-side pagination mode
  const isServerPagination = onPaginationChange !== undefined;
  const isServerFiltering = onFilterChange !== undefined;

  const table = useReactTable({
    data,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onSortingChange: setSorting,
    onColumnFiltersChange: setColumnFilters,
    onGlobalFilterChange: setGlobalFilter,
    globalFilterFn: 'includesString',
    // Server-side pagination config
    manualPagination: isServerPagination,
    pageCount: pageCount ?? -1,
    state: {
      sorting,
      columnFilters,
      globalFilter,
      pagination: isServerPagination ? { pageIndex, pageSize } : undefined,
    },
  });

  // Available status options for server-side filtering
  const statusOptions = [
    'ACCEPTED',
    'QUEUED', 
    'SENT_TO_CONNECTOR',
    'PROCESSING_PRICE',
    'PROCESSING_STOCK',
    'COMPLETED',
    'PARTIAL',
    'FAILED',
    'ERROR',
  ];

  // Get unique status values from data for client-side filters
  const uniqueMainStatuses = React.useMemo(() => {
    const statuses = new Set<string>();
    data.forEach((row: any) => {
      if (row.mainStatus) statuses.add(row.mainStatus);
    });
    return Array.from(statuses).sort();
  }, [data]);

  const uniqueConnectorStatuses = React.useMemo(() => {
    const statuses = new Set<string>();
    data.forEach((row: any) => {
      if (row.connectorStatus) statuses.add(row.connectorStatus);
    });
    return Array.from(statuses).sort();
  }, [data]);

  // Get active filter count
  const activeFilterCount = columnFilters.length + (globalFilter ? 1 : 0) + (currentFilter ? 1 : 0);

  // Reset all filters
  const resetFilters = () => {
    setGlobalFilter('');
    setColumnFilters([]);
    if (isServerFiltering) {
      onFilterChange('');
    }
  };

  return (
    <div className="space-y-4">
      {/* Search Bar and Filters */}
      <div className="flex items-center gap-2">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search orders..."
            value={globalFilter ?? ''}
            onChange={(event) => setGlobalFilter(event.target.value)}
            className="pl-9"
          />
        </div>
        <div className="flex items-center gap-2">
          {activeFilterCount > 0 && (
            <>
              <Badge variant="secondary" className="gap-1">
                <Filter className="h-3 w-3" />
                {activeFilterCount} active
              </Badge>
              <Button
                variant="ghost"
                size="sm"
                onClick={resetFilters}
                className="h-8 px-2"
              >
                <X className="h-4 w-4" />
                Clear
              </Button>
            </>
          )}
          <div className="text-sm text-muted-foreground">
            {isServerPagination 
              ? `${table.getRowModel().rows.length} of ${totalRows ?? 0} orders`
              : `${table.getRowModel().rows.length} of ${data.length} orders`
            }
          </div>
        </div>
      </div>

      {/* Inline Column Filters */}
      <div className="flex items-center gap-2 flex-wrap">
        {/* Server-side Status Filter (syncs to URL) */}
        {isServerFiltering && (
          <Select
            value={currentFilter || 'all'}
            onValueChange={(value) => onFilterChange(value === 'all' ? '' : value)}
          >
            <SelectTrigger className="h-8 w-[180px]">
              <div className="flex items-center gap-2">
                <Filter className="h-4 w-4" />
                <SelectValue placeholder="Filter by status" />
              </div>
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Statuses</SelectItem>
              {statusOptions.map((status) => (
                <SelectItem key={status} value={status}>
                  {status}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}

        {/* Client-side Main Status Filter (only when not using server filtering) */}
        {!isServerFiltering && (
          <StatusFilterDropdown
            column={table.getColumn('mainStatus')}
            title="Main Status"
            options={uniqueMainStatuses}
          />
        )}

        {/* Client-side Connector Status Filter (only when not using server filtering) */}
        {!isServerFiltering && (
          <StatusFilterDropdown
            column={table.getColumn('connectorStatus')}
            title="Connector Status"
            options={uniqueConnectorStatuses}
          />
        )}

        {/* Price Range Filter */}
        <RangeFilter
          column={table.getColumn('channelPrice')}
          title="Price"
          prefix="$"
        />

        {/* Stock Range Filter */}
        <RangeFilter
          column={table.getColumn('channelStock')}
          title="Stock"
        />
      </div>

      {/* Table */}
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id}>
                {headerGroup.headers.map((header) => {
                  return (
                    <TableHead key={header.id}>
                      {header.isPlaceholder
                        ? null
                        : flexRender(
                            header.column.columnDef.header,
                            header.getContext()
                          )}
                    </TableHead>
                  );
                })}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {table.getRowModel().rows?.length ? (
              table.getRowModel().rows.map((row) => (
                <TableRow
                  key={row.id}
                  data-state={row.getIsSelected() && 'selected'}
                >
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id}>
                      {flexRender(
                        cell.column.columnDef.cell,
                        cell.getContext()
                      )}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell
                  colSpan={columns.length}
                  className="h-24 text-center"
                >
                  {globalFilter || columnFilters.length > 0 ? (
                    <div className="text-muted-foreground">
                      No orders found matching your filters.
                    </div>
                  ) : (
                    <div className="text-muted-foreground">
                      No orders yet. Click &quot;Trigger Burst&quot; to create some orders.
                    </div>
                  )}
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination Controls */}
      {isServerPagination && (
        <div className="flex items-center justify-between px-2 py-4">
          <div className="flex items-center gap-2">
            <span className="text-sm text-muted-foreground">Rows per page</span>
            <Select
              value={pageSize.toString()}
              onValueChange={(value) => {
                onPaginationChange?.({ pageIndex: 0, pageSize: Number(value) });
              }}
            >
              <SelectTrigger className="h-8 w-[70px]">
                <SelectValue placeholder={pageSize.toString()} />
              </SelectTrigger>
              <SelectContent side="top">
                {[10, 25, 50, 100].map((size) => (
                  <SelectItem key={size} value={size.toString()}>
                    {size}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="flex items-center gap-2">
            <span className="text-sm text-muted-foreground">
              Page {pageIndex + 1} of {pageCount || 1}
            </span>
            <div className="flex items-center gap-1">
              <Button
                variant="outline"
                size="sm"
                onClick={() => onPaginationChange?.({ pageIndex: 0, pageSize })}
                disabled={pageIndex === 0}
                className="h-8 w-8 p-0"
              >
                <ChevronsLeft className="h-4 w-4" />
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => onPaginationChange?.({ pageIndex: pageIndex - 1, pageSize })}
                disabled={pageIndex === 0}
                className="h-8 w-8 p-0"
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => onPaginationChange?.({ pageIndex: pageIndex + 1, pageSize })}
                disabled={pageIndex >= (pageCount ?? 1) - 1}
                className="h-8 w-8 p-0"
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => onPaginationChange?.({ pageIndex: (pageCount ?? 1) - 1, pageSize })}
                disabled={pageIndex >= (pageCount ?? 1) - 1}
                className="h-8 w-8 p-0"
              >
                <ChevronsRight className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
