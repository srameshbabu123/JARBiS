export interface User {
  id: number;
  fullName: string;
  email: string;
}
export interface AuthResponse {
  user: User;
  token: string;
  tokenType: string;
}
export interface Account {
  id: number;
  accountType: string;
  currency: string;
  balance: number;
  ownerId: number;
}
export interface Holding {
  id: number;
  accountId: number;
  assetId: number;
  assetName: string;
  quantity: number;
  averagePrice: number;
  marketValue: number;
  costBasis: number;
  unrealizedPnL: number;
  unrealizedPnLPercentage: number;
  isProfitable: boolean;
}
export interface Order {
  id: number;
  asset: { id?: number; name: string; symbol?: string } | null;
  side: string;
  quantity: number;
  remainingQuantity?: number;
  price: number;
  status: string;
}
export interface CreateOrder {
  accountId: number;
  assetId: number;
  side: "BUY" | "SELL";
  quantity: number;
  price: number;
}
