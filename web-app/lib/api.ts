// API configuration
const API_MAIN = 'http://localhost:8082';
const API_CONNECTOR = 'http://localhost:8080';
const API_CHANNEL = 'http://localhost:8081';

// Types
export interface StateEvent {
  timestamp: string;
  eventType: string;
  status: string;
  details: string | null;
}

export interface OrderState {
  orderId: string;
  correlationId: string;
  status: string;
  submittedAt: string | null;
  acceptedAt: string | null;
  completedAt: string | null;
  originalRequest: {
    orderId: string;
    price: number;
    stock: number;
  } | null;
  // TODO create POJO
  finalResponse: any | null;
  error: string | null;
  // TODO create POJO
  priceCallback: any | null;
  // TODO create POJO
  stockCallback: any | null;
  priceCompleted: boolean;
  stockCompleted: boolean;
  eventHistory?: StateEvent[];
}

export interface MainAppStatus {
  states: OrderState[];
}

export interface ConnectorStatus {
  states: OrderState[];
}

// Paginated response types
export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PaginationParams {
  page?: number;
  size?: number;
  filter?: string;
}

export interface ChannelOrderDetails {
  orderId: string;
  price?: number;
  stock?: number;
  priceStatus?: string;
  stockStatus?: string;
  lastUpdated?: string;
}

export interface DashboardData {
  mainAppStates: OrderState[];
  connectorStates: OrderState[];
  channelDetails: Map<string, ChannelOrderDetails>;
  allOrderIds: string[];
  // Pagination info
  mainPagination: { page: number; size: number; totalElements: number; totalPages: number };
  connectorPagination: { page: number; size: number; totalElements: number; totalPages: number };
}

export interface ClearQueuesResponse {
  mainApp: {
    batchQueueCleared: number;
    statesCleared: number;
  };
  connectorApp: {
    priceQueueCleared: number;
    stockQueueCleared: number;
    statesCleared: number;
    timestamp: string;
  };
  channelApp: {
    processesCleared: number;
    orderStatesCleared: number;
    asyncQueuePurged: boolean;
    timestamp: string;
  };
  totalCleared: number;
}

// API functions
export async function triggerBurst(count: number = 20): Promise<void> {
  const response = await fetch(`${API_MAIN}/burst/${count}`, {
    method: 'POST',
  });
  if (!response.ok) {
    throw new Error(`Failed to trigger burst: ${response.statusText}`);
  }
}

export async function submitCustomOrder(orderId: string, price: number, stock: number): Promise<{ orderId: string; status: string }> {
  const response = await fetch(`${API_MAIN}/priceAndStock`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      orderId,
      price,
      stock,
    }),
  });
  
  if (!response.ok) {
    throw new Error(`Failed to submit order: ${response.statusText}`);
  }
  
  return await response.json();
}

export async function toggleBackgroundLoad(action: 'activate' | 'deactivate'): Promise<string> {
  const response = await fetch(`${API_MAIN}/background-load/${action}`, {
    method: 'POST',
  });
  
  if (!response.ok) {
    throw new Error(`Failed to ${action} background load: ${response.statusText}`);
  }
  
  const text = await response.text();
  return text;
}

// Load generator types and functions
export interface LoadStatus {
  active: boolean;
  rate: number;
  totalGenerated: number;
}

export interface LoadStartResponse {
  status: string;
  rate: number;
  message: string;
}

export interface LoadStopResponse {
  status: string;
  totalGenerated: number;
  previousRate: number;
}

export async function startLoad(rate: number): Promise<LoadStartResponse> {
  const response = await fetch(`${API_MAIN}/load/start?rate=${rate}`, {
    method: 'POST',
  });
  
  if (!response.ok) {
    const error = await response.text();
    throw new Error(error || `Failed to start load: ${response.statusText}`);
  }
  
  return await response.json();
}

export async function stopLoad(): Promise<LoadStopResponse> {
  const response = await fetch(`${API_MAIN}/load/stop`, {
    method: 'POST',
  });
  
  if (!response.ok) {
    throw new Error(`Failed to stop load: ${response.statusText}`);
  }
  
  return await response.json();
}

export async function getLoadStatus(): Promise<LoadStatus> {
  const response = await fetch(`${API_MAIN}/load/status`, {
    cache: 'no-store',
  });
  
  if (!response.ok) {
    throw new Error(`Failed to get load status: ${response.statusText}`);
  }
  
  return await response.json();
}

export async function clearAllQueues(): Promise<ClearQueuesResponse> {
  const response = await fetch(`${API_MAIN}/queues`, {
    method: 'DELETE',
  });
  
  if (!response.ok) {
    throw new Error(`Failed to clear queues: ${response.statusText}`);
  }
  
  return await response.json();
}

export async function fetchMainAppStatus(params: PaginationParams = {}): Promise<PagedResponse<OrderState>> {
  const { page = 0, size = 50, filter } = params;
  try {
    const url = new URL(`${API_MAIN}/status`);
    url.searchParams.set('page', page.toString());
    url.searchParams.set('size', size.toString());
    if (filter) url.searchParams.set('filter', filter);
    
    const response = await fetch(url.toString(), {
      cache: 'no-store',
    });
    if (!response.ok) {
      console.error('[API] Main app request failed:', response.statusText);
      return { content: [], page: 0, size, totalElements: 0, totalPages: 0 };
    }
    const data = await response.json();
    return data;
  } catch (error) {
    console.error('[API] Main app fetch error:', error);
    return { content: [], page: 0, size, totalElements: 0, totalPages: 0 };
  }
}

export async function fetchConnectorStatus(params: PaginationParams = {}): Promise<PagedResponse<OrderState>> {
  const { page = 0, size = 50, filter } = params;
  try {
    const url = new URL(`${API_CONNECTOR}/status`);
    url.searchParams.set('page', page.toString());
    url.searchParams.set('size', size.toString());
    if (filter) url.searchParams.set('filter', filter);
    
    const response = await fetch(url.toString(), {
      cache: 'no-store',
    });
    if (!response.ok) {
      console.error('[API] Connector request failed:', response.statusText);
      return { content: [], page: 0, size, totalElements: 0, totalPages: 0 };
    }
    const data = await response.json();
    return data;
  } catch (error) {
    console.error('[API] Connector fetch error:', error);
    return { content: [], page: 0, size, totalElements: 0, totalPages: 0 };
  }
}

export async function fetchChannelOrderDetails(orderId: string): Promise<ChannelOrderDetails | null> {
  try {
    const response = await fetch(`${API_CHANNEL}/orders/${orderId}`, {
      cache: 'no-store',
    });
    if (!response.ok) {
      return null;
    }
    return await response.json();
  } catch {
    return null;
  }
}

export async function fetchOrderHistory(orderId: string): Promise<StateEvent[]> {
  try {
    const response = await fetch(`${API_MAIN}/status/${orderId}/history`, {
      cache: 'no-store',
    });
    if (!response.ok) {
      console.error('[API] Order history request failed:', response.statusText);
      return [];
    }
    return await response.json();
  } catch (error) {
    console.error('[API] Order history fetch error:', error);
    return [];
  }
}

export async function fetchAllDashboardData(params: PaginationParams = {}): Promise<DashboardData> {
  // Fetch main and connector status in parallel with pagination
  const [mainStatus, connectorStatus] = await Promise.all([
    fetchMainAppStatus(params),
    fetchConnectorStatus(params),
  ]);

  // Collect unique order IDs from current page
  const orderIdSet = new Set<string>();
  mainStatus.content.forEach(s => orderIdSet.add(s.orderId));
  connectorStatus.content.forEach(s => orderIdSet.add(s.orderId));
  
  const allOrderIds = Array.from(orderIdSet).sort();

  // Fetch channel details only for orders on current page
  const channelDetailsArray = await Promise.all(
    allOrderIds.map(id => fetchChannelOrderDetails(id))
  );

  const channelDetails = new Map<string, ChannelOrderDetails>();
  allOrderIds.forEach((id, index) => {
    const details = channelDetailsArray[index];
    if (details) {
      channelDetails.set(id, details);
    }
  });

  return {
    mainAppStates: mainStatus.content,
    connectorStates: connectorStatus.content,
    channelDetails,
    allOrderIds,
    mainPagination: {
      page: mainStatus.page,
      size: mainStatus.size,
      totalElements: mainStatus.totalElements,
      totalPages: mainStatus.totalPages,
    },
    connectorPagination: {
      page: connectorStatus.page,
      size: connectorStatus.size,
      totalElements: connectorStatus.totalElements,
      totalPages: connectorStatus.totalPages,
    },
  };
}

// Connector config types and functions
export interface QueueIntervalConfig {
  intervalMs: number;
  schedulerRunning: boolean;
  priceQueueActive: boolean;
  stockQueueActive: boolean;
}

export interface SetIntervalResponse {
  previousIntervalMs: number;
  newIntervalMs: number;
  message: string;
}

export async function getQueueInterval(): Promise<QueueIntervalConfig> {
  const response = await fetch(`${API_CONNECTOR}/config/queue-interval`, {
    cache: 'no-store',
  });
  
  if (!response.ok) {
    throw new Error(`Failed to get queue interval: ${response.statusText}`);
  }
  
  return await response.json();
}

export async function setQueueInterval(ms: number): Promise<SetIntervalResponse> {
  const response = await fetch(`${API_CONNECTOR}/config/queue-interval?ms=${ms}`, {
    method: 'POST',
  });
  
  if (!response.ok) {
    const error = await response.text();
    throw new Error(error || `Failed to set queue interval: ${response.statusText}`);
  }
  
  return await response.json();
}
