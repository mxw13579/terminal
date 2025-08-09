// Common types
export interface SshCredentials {
  host: string;
  port?: number;
  user: string;
  password: string;
}

export interface ConnectionConfig {
  host: string;
  port: number;
  user: string;
  password: string;
  sessionId?: string;
}

// Authentication types
export interface TokenResponse {
  success: boolean;
  token: string;
  expiresInSec: number;
  message?: string;
}

export interface AuthStatus {
  hasToken: boolean;
  tokenValid: boolean;
  remainingTime: number;
  retryCount: number;
  maxRetries: number;
  cryptoSupported: boolean;
}

export interface ConnectionHeaders {
  Authorization: string;
}

// Crypto types
export interface PublicKeyResponse {
  publicKey: string;
  algorithm: string;
  keyLength: number;
}

export interface CryptoStatus {
  supported: boolean;
  hasCachedKey: boolean;
  keyAge: number | null;
  algorithm: string;
  hash: string;
}

// File transfer types
export interface FileTransferProgress {
  uploadId?: string;
  loaded: number;
  total: number;
  percentage: number;
  speed?: number;
  status: 'uploading' | 'downloading' | 'completed' | 'error' | 'cancelled';
  estimatedTimeRemaining?: number;
}

export interface UploadResult {
  uploadId: string;
  status: string;
  message: string;
}

export interface DownloadResult {
  filename: string;
  blob: Blob;
}

export interface FileUploadInfo {
  xhr: XMLHttpRequest;
  files: string[];
  startTime: number;
  totalSize: number;
}

export interface FileDownloadInfo {
  controller: AbortController;
  startTime: number;
  totalSize: number;
}

// STOMP Message types
export interface StompMessage {
  type: string;
  data?: any;
  sessionId?: string;
  timestamp?: number;
}

export interface TerminalMessage extends StompMessage {
  type: 'data' | 'resize' | 'connect' | 'disconnect';
  data: string | { cols: number; rows: number } | ConnectionConfig;
}

export interface SftpMessage extends StompMessage {
  type: 'sftp_list' | 'sftp_upload' | 'sftp_download' | 'sftp_delete' | 'sftp_mkdir' | 'sftp_rename';
  data: {
    path?: string;
    files?: File[];
    newName?: string;
    oldPath?: string;
    newPath?: string;
  };
}

export interface MonitorMessage extends StompMessage {
  type: 'monitor_start' | 'monitor_stop' | 'monitor_data';
  data: {
    type?: 'system' | 'process' | 'network';
    interval?: number;
    metrics?: SystemMetrics;
  };
}

// System monitoring types
export interface SystemMetrics {
  cpu: {
    usage: number;
    cores: number;
    loadAvg: number[];
  };
  memory: {
    total: number;
    used: number;
    free: number;
    buffers: number;
    cached: number;
  };
  disk: {
    total: number;
    used: number;
    free: number;
  };
  network: {
    interfaces: NetworkInterface[];
  };
  processes: ProcessInfo[];
  timestamp: number;
}

export interface NetworkInterface {
  name: string;
  type: string;
  ip: string;
  mac?: string;
  rxBytes: number;
  txBytes: number;
  rxPackets: number;
  txPackets: number;
}

export interface ProcessInfo {
  pid: number;
  name: string;
  cpu: number;
  memory: number;
  command: string;
  user: string;
}

// Error types
export interface ErrorInfo {
  code: string;
  message: string;
  details?: any;
  timestamp: number;
  recoverable?: boolean;
}

export interface ValidationError {
  field: string;
  message: string;
  value?: any;
}

// Component state types
export interface LoadingState {
  isLoading: boolean;
  message?: string;
  progress?: number;
}

export interface ErrorState {
  hasError: boolean;
  error?: Error | string;
  canRetry?: boolean;
}

export interface EmptyState {
  isEmpty: boolean;
  message?: string;
  actionText?: string;
  onAction?: () => void;
}

// Theme types
export interface ThemeConfig {
  name: 'light' | 'dark' | 'auto';
  colors: {
    primary: string;
    secondary: string;
    accent: string;
    background: string;
    surface: string;
    error: string;
    warning: string;
    success: string;
    info: string;
    text: {
      primary: string;
      secondary: string;
      disabled: string;
    };
  };
  spacing: {
    xs: string;
    sm: string;
    md: string;
    lg: string;
    xl: string;
  };
  typography: {
    fontFamily: string;
    fontSize: {
      xs: string;
      sm: string;
      md: string;
      lg: string;
      xl: string;
    };
  };
}

// Accessibility types
export interface A11yConfig {
  focusVisible: boolean;
  highContrast: boolean;
  reducedMotion: boolean;
  screenReader: boolean;
}

// Utility types
export type EventCallback<T = any> = (data: T) => void;
export type AsyncEventCallback<T = any> = (data: T) => Promise<void>;

export interface EventListenerOptions {
  once?: boolean;
  passive?: boolean;
}

export interface Debounced<T extends (...args: any[]) => any> {
  (...args: Parameters<T>): Promise<ReturnType<T>>;
  cancel: () => void;
  flush: () => Promise<ReturnType<T>>;
}