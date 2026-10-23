export interface Profile {
  id: string;
  email: string;
  fullName: string;
  tenantId: string;
  tenantCode: string;
  tenantName: string;
  roles: string[];
  permissions: string[];
  mfaEnabled: boolean;
}

export interface Session {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: Profile;
}

export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED';

export interface Account {
  id: string;
  number: string;
  ownerId: string | null;
  ownerName: string | null;
  type: 'CHECKING' | 'SAVINGS' | 'INTERNAL';
  currency: string;
  balance: number;
  status: AccountStatus;
  dailyLimit: number;
  alias: string | null;
  createdAt: string;
}

export interface Movement {
  id: number;
  type: 'TRANSFER' | 'CASH_DEPOSIT' | 'CASH_WITHDRAWAL';
  reference: string;
  description: string | null;
  amount: number;
  balanceAfter: number;
  bookedAt: string;
}

export type TransferStatus =
  'PENDING_APPROVAL' | 'HELD_FOR_REVIEW' | 'ACCEPTED' | 'COMPLETED' | 'FAILED' | 'REJECTED';

export interface Transfer {
  id: string;
  status: TransferStatus;
  sourceAccountNumber: string;
  destinationAccountNumber: string;
  amount: number;
  currency: string;
  endToEndId: string;
  uetr: string;
  remittanceInfo: string | null;
  purposeCode: string | null;
  riskScore: number | null;
  riskSignals: string[];
  failureCode: string | null;
  failureReason: string | null;
  initiatedBy: string;
  reviewedBy: string | null;
  reviewNote: string | null;
  batchId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PaymentEvent {
  eventId: string;
  type: string;
  tenantId: string;
  transferId: string;
  sourceNumber: string;
  destinationNumber: string;
  amountMinor: number;
  currency: string;
  status: TransferStatus;
  riskScore: number | null;
  riskSignals: string | null;
  failureCode: string | null;
  occurredAt: string;
}

export interface TransferNotification {
  transferId: string;
  type: string;
  direction: 'IN' | 'OUT';
  amount: number;
  currency: string;
  counterparty: string;
  status: TransferStatus;
  failureCode: string | null;
}

export interface UserView {
  id: string;
  email: string;
  fullName: string;
  nationalId: string | null;
  status: 'ACTIVE' | 'LOCKED' | 'DISABLED';
  locked: boolean;
  roles: string[];
  createdAt: string;
}

export interface Role {
  id: string;
  name: string;
  description: string | null;
  system: boolean;
  permissions: string[];
}

export interface PermissionInfo {
  code: string;
  description: string;
  scope: 'TENANT' | 'PLATFORM';
}

export type InstallmentStatus = 'PAID' | 'PENDING' | 'OVERDUE';

export interface Installment {
  number: number;
  dueDate: string;
  principal: number;
  interest: number;
  amount: number;
  status: InstallmentStatus;
}

export interface Loan {
  id: string;
  customerName: string;
  accountNumber: string;
  principal: number;
  outstanding: number;
  annualRate: number;
  termMonths: number;
  status: 'ACTIVE' | 'PAID_OFF';
  createdAt: string;
  next: Installment | null;
}

export interface LoanDetail {
  loan: Loan;
  schedule: Installment[];
}

/** Loans and interest: not payments, so they travel on their own topic (CustomerNotices on the server). */
export interface CustomerNotice {
  id: string;
  type: 'LoanDisbursed' | 'InstallmentCollected' | 'InstallmentMissed' | 'InterestCredited';
  amount: number;
  currency: string;
  installment: number | null;
  installments: number | null;
}

export type Notification = TransferNotification | CustomerNotice;
