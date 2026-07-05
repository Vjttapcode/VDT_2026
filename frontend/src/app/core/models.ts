export type Role = 'USER' | 'MANAGER_CENTER' | 'MANAGER_COMPANY' | 'ADMIN';
export type DocType = 'CONTRACT' | 'LICENSE' | 'CERTIFICATE' | 'SR';
export type DocLevel = 'CENTER' | 'COMPANY' | 'GROUP';
export type DocStatus = 'DRAFT' | 'PENDING' | 'ACTIVE' | 'WARNING' | 'EXPIRED' | 'REJECTED';

export interface LoginResponse {
  token: string;
  userId: number;
  email: string;
  fullName: string;
  role: Role;
  departmentId: number | null;
  companyId: number | null;
}

export interface DocumentDto {
  id: number;
  title: string;
  description: string | null;
  type: DocType;
  level: DocLevel;
  status: DocStatus;
  ownerId: number;
  ownerName: string | null;
  departmentId: number | null;
  companyId: number | null;
  expiryDate: string;
  filePath: string | null;
  renewalCount: number;
  supersedesId: number | null;
  createdAt: string;
  updatedAt: string;
}

export type RelationType = 'REPLACE' | 'REPEAL' | 'AMEND';
export type RelationDirection = 'OUTGOING' | 'INCOMING';

/** Một quan hệ nghiệp vụ của văn bản (đã resolve chiều + văn bản đối tác). */
export interface DocRelation {
  id: number;
  type: RelationType;
  direction: RelationDirection;
  otherDocId: number;
  otherDocTitle: string | null;
  createdAt: string;
}

export const REL_TYPE_VN: Record<RelationType, string> = {
  REPLACE: 'Thay thế',
  REPEAL: 'Bãi bỏ',
  AMEND: 'Sửa đổi/bổ sung'
};

export const REL_TYPE_COLOR: Record<RelationType, string> = {
  REPLACE: '#7A5AF0',
  REPEAL: '#C62B26',
  AMEND: '#3B6BB5'
};

/** Nhãn quan hệ theo chiều: [OUTGOING, INCOMING]. */
export const REL_DIRECTION_VN: Record<RelationType, Record<RelationDirection, string>> = {
  REPLACE: { OUTGOING: 'Thay thế cho', INCOMING: 'Bị thay thế bởi' },
  REPEAL: { OUTGOING: 'Bãi bỏ', INCOMING: 'Bị bãi bỏ bởi' },
  AMEND: { OUTGOING: 'Sửa đổi/bổ sung cho', INCOMING: 'Được sửa đổi/bổ sung bởi' }
};

export type AuditAction = 'CREATE' | 'UPDATE' | 'SUBMIT' | 'APPROVE' | 'REJECT' | 'RENEW' | 'REPLACE' | 'REPEAL' | 'AMEND';

/** Một dòng lịch sử thay đổi (audit log) của văn bản. */
export interface AuditLog {
  id: number;
  action: AuditAction;
  actorId: number | null;
  actorName: string | null;
  comment: string | null;
  changes: string | null;   // JSON {field: {old, new}}
  createdAt: string;
}

export const AUDIT_ACTION_VN: Record<AuditAction, string> = {
  CREATE: 'Tạo mới',
  UPDATE: 'Chỉnh sửa',
  SUBMIT: 'Gửi duyệt',
  APPROVE: 'Phê duyệt',
  REJECT: 'Từ chối',
  RENEW: 'Gia hạn',
  REPLACE: 'Thay thế',
  REPEAL: 'Bãi bỏ',
  AMEND: 'Sửa đổi/bổ sung'
};

/** Màu chấm mốc thời gian theo loại hành động. */
export const AUDIT_ACTION_COLOR: Record<AuditAction, string> = {
  CREATE: '#3B6BB5',
  UPDATE: '#9A6400',
  SUBMIT: '#3B6BB5',
  APPROVE: '#1E8E5A',
  REJECT: '#C62B26',
  RENEW: '#1E8E5A',
  REPLACE: '#7A5AF0',
  REPEAL: '#C62B26',
  AMEND: '#3B6BB5'
};

/** Nhãn tiếng Việt cho các trường trong diff changes. */
export const FIELD_VN: Record<string, string> = {
  title: 'Tiêu đề',
  description: 'Mô tả',
  type: 'Loại văn bản',
  level: 'Cấp áp dụng',
  expiryDate: 'Ngày hết hạn',
  supersedesId: 'Văn bản thay thế'
};

export interface DashboardStats {
  active: number;
  warning: number;
  expired: number;
  pending: number;
  expiringIn30Days: { docId: number; title: string; level: DocLevel; daysLeft: number }[];
}

export const TYPE_VN: Record<DocType, string> = {
  CONTRACT: 'Hợp đồng',
  LICENSE: 'Giấy phép',
  CERTIFICATE: 'Chứng chỉ',
  SR: 'SR nội bộ'
};

export const TYPE_CODE: Record<DocType, string> = {
  CONTRACT: 'HĐ',
  LICENSE: 'GP',
  CERTIFICATE: 'CC',
  SR: 'SR'
};

export const LEVEL_VN: Record<DocLevel, string> = {
  CENTER: 'Trung tâm',
  COMPANY: 'Công ty',
  GROUP: 'Tập đoàn'
};

export const ROLE_VN: Record<Role, string> = {
  USER: 'Nhân viên',
  MANAGER_CENTER: 'Trưởng Trung tâm',
  MANAGER_COMPANY: 'Trưởng Công ty',
  ADMIN: 'Quản trị Tập đoàn'
};

/** Seed data hiện có 3 trung tâm; fallback khi không gọi được API departments. */
export const DEPT_VN: Record<number, string> = {
  1: 'TT Phát triển phần mềm',
  2: 'TT Hạ tầng mạng',
  3: 'TT Kinh doanh'
};

export interface StatusTheme { vn: string; bg: string; color: string; stripe: string; }

export const STATUS_THEME: Record<DocStatus, StatusTheme> = {
  ACTIVE:   { vn: 'Còn hiệu lực', bg: 'rgba(30,142,90,.12)',   color: '#1B7F50', stripe: '#1E8E5A' },
  WARNING:  { vn: 'Sắp hết hạn',  bg: 'rgba(224,162,46,.14)',  color: '#9A6400', stripe: '#E0A22E' },
  EXPIRED:  { vn: 'Đã hết hạn',   bg: 'rgba(226,47,41,.10)',   color: '#C62B26', stripe: '#E22F29' },
  PENDING:  { vn: 'Chờ duyệt',    bg: 'rgba(75,123,229,.14)',  color: '#3B6BB5', stripe: '#3B6BB5' },
  DRAFT:    { vn: 'Nháp',         bg: '#F0EDF0',               color: '#5A6072', stripe: '#9A95A2' },
  REJECTED: { vn: 'Bị từ chối',   bg: 'rgba(122,116,128,.16)', color: '#6E6876', stripe: '#7A7480' }
};

/** [iconBg, iconFg] theo loại văn bản */
export const TYPE_THEME: Record<DocType, [string, string]> = {
  CONTRACT:    ['rgba(75,123,229,.14)', '#3B6BB5'],
  LICENSE:     ['rgba(226,47,41,.12)', '#E22F29'],
  CERTIFICATE: ['rgba(30,142,90,.14)', '#1E8E5A'],
  SR:          ['rgba(224,162,46,.16)', '#E0A22E']
};

/** Văn bản đã "trình bày" cho UI */
export interface DocView extends DocumentDto {
  code: string;
  typeVn: string;
  levelVn: string;
  deptName: string;
  ownerVn: string;
  daysLeft: number;
  /** trạng thái hiển thị: ACTIVE/WARNING được suy lại từ daysLeft cho realtime */
  dispStatus: DocStatus;
  statusVn: string;
  badgeBg: string;
  badgeColor: string;
  stripe: string;
  daysVn: string;
  iconBg: string;
  iconFg: string;
  expiryVn: string;
  updatedVn: string;
  issuedVn: string;
}

/* ===== date helpers ===== */

export function toDate(iso: string): Date {
  return new Date(iso.length <= 10 ? iso + 'T00:00:00' : iso);
}

export function fmtDate(d: Date): string {
  const dd = String(d.getDate()).padStart(2, '0');
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  return `${dd}/${mm}/${d.getFullYear()}`;
}

export function fmtIso(d: Date): string {
  const dd = String(d.getDate()).padStart(2, '0');
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}

export function daysFromToday(iso: string): number {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const target = toDate(iso);
  target.setHours(0, 0, 0, 0);
  return Math.round((target.getTime() - today.getTime()) / 86400000);
}

export function daysText(n: number): string {
  if (n < 0) return `Quá hạn ${-n} ngày`;
  if (n === 0) return 'Hết hạn hôm nay';
  return `Còn ${n} ngày`;
}
