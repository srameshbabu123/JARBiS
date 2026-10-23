export interface MarketStock {
  id: number;
  symbol: string;
  name: string;
  price: number;
  change: number;
  sector: string;
  cap: number;
  color: string;
}

export interface MarketSymbolMetadata {
  symbol: string;
  name: string;
  sector: string;
  marketCap: number;
}
