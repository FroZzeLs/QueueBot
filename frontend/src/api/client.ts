export type ApiMe = {
  id: number;
  telegramTag: string;
  fio: string;
  subgroup: number;
  role: 'USER' | 'ADMIN' | 'SUPER_ADMIN';
  isPasswordSet: boolean;
};

export type QueueUpdateEvent = {
  subjectId: number;
  queueKind: string;
  subgroupNum: number | null;
};

const baseUrl = ''; // same origin when served by nginx

async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(baseUrl + path, {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(options?.headers ?? {}) },
    ...options,
  });

  if (!res.ok) {
    let data: any = null;
    try {
      data = await res.json();
    } catch {
      // ignore
    }
    throw new Error(data?.error || data?.state || `HTTP ${res.status}`);
  }

  return (await res.json()) as T;
}

export const api = {
  getMe: () => apiFetch<ApiMe>('/api/auth/me'),
  getBootstrapStatus: () => apiFetch<{ hasSuperAdmin: boolean }>('/api/auth/bootstrap-status'),
  verifySuperKey: (superKey: string) =>
    apiFetch<{ ok: boolean }>('/api/auth/bootstrap/verify-super-key', {
      method: 'POST',
      body: JSON.stringify({ superKey }),
    } as any),
  registerSuperAdmin: (payload: {
    telegramTag?: string;
    fio?: string;
    subgroup?: number;
    password: string;
    rememberDevice: boolean;
    superKey: string;
  }) =>
    apiFetch<{ ok: boolean }>(
      '/api/auth/bootstrap/register-super-admin',
      {
        method: 'POST',
        body: JSON.stringify({
          superKey: payload.superKey,
          telegramTag: payload.telegramTag,
          fio: payload.fio,
          subgroup: payload.subgroup,
          password: payload.password,
          rememberDevice: payload.rememberDevice,
        }),
      } as any,
    ),
  login: (payload: { telegramTag?: string; fio?: string; subgroup?: number; password: string | null; rememberDevice: boolean }) =>
    apiFetch<any>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({
        telegramTag: payload.telegramTag,
        fio: payload.fio,
        subgroup: payload.subgroup,
        password: payload.password,
        rememberDevice: payload.rememberDevice,
      }),
    } as any),
  loginWithTelegramToken: (token: string) =>
    apiFetch<any>('/api/tg-auth/login', {
      method: 'POST',
      body: JSON.stringify({ authToken: token }),
    } as any),
  requestTelegramAuthToken: (chatId: number) =>
    apiFetch<{ token: string; expiresAt: string }>('/api/tg-auth/request-token', {
      method: 'POST',
      body: JSON.stringify({ chatId }),
    } as any),
  setPassword: (payload: { telegramTag?: string; fio?: string; subgroup?: number; newPassword: string; rememberDevice: boolean }) =>
    apiFetch<any>('/api/auth/set-password', {
      method: 'POST',
      body: JSON.stringify({
        telegramTag: payload.telegramTag,
        fio: payload.fio,
        subgroup: payload.subgroup,
        newPassword: payload.newPassword,
        rememberDevice: payload.rememberDevice,
      }),
    } as any),

  getSubjects: () => apiFetch<{ subjects: Array<{ id: number; name: string; deliveryType: string }> }>('/api/subjects'),
  getActiveQueue: (params: { subjectId: number; queueKind: 'COMMON' | 'SUBGROUP'; subgroupNum?: number }) =>
    apiFetch<any>(
      `/api/queue/active?subjectId=${params.subjectId}&queueKind=${params.queueKind}${
        params.subgroupNum != null ? `&subgroupNum=${params.subgroupNum}` : ''
      }`,
    ),
  getBrigadeMembers: (brigadeId: number) => apiFetch<any>(`/api/brigades/${brigadeId}/members`),
  getBrigadesForSubject: (subjectId: number) =>
    apiFetch<{ brigades: Array<{ id: number; displayName: string; memberIds: number[]; memberNames: string[] }> }>(
      `/api/brigades/subject?subjectId=${subjectId}`
    ),
  getAllBrigadesWithSubjects: () =>
    apiFetch<{ subjects: Array<{ subjectId: number; subjectName: string; brigades: Array<{ id: number; displayName: string; memberIds: number[] }> }> }>(
      `/api/brigades/all-with-subjects`
    ),

  leaveQueue: (payload: { subjectId: number; queueKind: string; subgroupNum?: number }) =>
    apiFetch<any>('/api/queue/leave', {
      method: 'POST',
      body: JSON.stringify(payload),
    } as any),

  joinQueue: (payload: { subjectId: number; queueKind: string; subgroupNum?: number }) =>
    apiFetch<any>('/api/queue/join', {
      method: 'POST',
      body: JSON.stringify(payload),
    } as any),

  getQueueStatus: (payload: { subjectId: number; queueKind: string; subgroupNum?: number }) =>
    apiFetch<any>('/api/queue/status', {
      method: 'POST',
      body: JSON.stringify(payload),
    } as any),

  getMyBrigade: (subjectId: number, queueKind: string, subgroupNum?: number) =>
    apiFetch<{ brigadeId: number | null }>(
      `/api/queue/my-brigade?subjectId=${subjectId}&queueKind=${queueKind}${subgroupNum != null ? `&subgroupNum=${subgroupNum}` : ''}`
    ),

  selfMark: (payload: { subjectId: number; queueKind: string; subgroupNum?: number }) =>
    apiFetch<any>('/api/queue/self-mark', {
      method: 'POST',
      body: JSON.stringify(payload),
    } as any),

  markLastPassed: (payload: {
    subjectId: number;
    queueKind: string;
    subgroupNum?: number;
    physicalStudentId?: number;
    brigadeId?: number;
  }) =>
    apiFetch<any>('/api/queue/mark-last-passed', {
      method: 'POST',
      body: JSON.stringify(payload),
    } as any),

  createSwapRequest: (payload: any) =>
    apiFetch<any>('/api/swap-requests', {
      method: 'POST',
      body: JSON.stringify(payload),
    } as any),

  streamQueueUpdates: () => {
    return new EventSource('/api/queue/stream');
  },
};

