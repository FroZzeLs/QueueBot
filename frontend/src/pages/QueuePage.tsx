import React, { useEffect, useMemo, useState } from 'react';
import {
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { api, type ApiMe } from '../api/client';

type QueueItem =
  | { position: number; id: number; fio: string; subgroup: number }
  | { position: number; id: number; displayName: string };

export function QueuePage({ me }: { me: ApiMe; onMeChange?: (me: ApiMe) => void }) {
  const [subjects, setSubjects] = useState<Array<{ id: number; name: string; deliveryType: string }>>([]);
  const [loading, setLoading] = useState(true);
  const [selectedSubjectId, setSelectedSubjectId] = useState<number | null>(null);
  const [queueKind, setQueueKind] = useState<'COMMON' | 'SUBGROUP'>('COMMON');
  const [subgroupNum, setSubgroupNum] = useState<number>(1);
  const [queueItems, setQueueItems] = useState<any[]>([]);
  const [queueLoading, setQueueLoading] = useState(false);
  const [queueDeliveryType, setQueueDeliveryType] = useState<'INDIVIDUAL' | 'BRIGADE'>('INDIVIDUAL');

  const [swapDialogOpen, setSwapDialogOpen] = useState(false);
  const [selectedTargetStudentId, setSelectedTargetStudentId] = useState<number | ''>('');
  const [selectedTargetBrigadeId, setSelectedTargetBrigadeId] = useState<number | ''>('');
  const [membersDialogLoading, setMembersDialogLoading] = useState(false);
  const [targetMembers, setTargetMembers] = useState<Array<{ id: number; fio: string; telegramTag: string }>>([]);
  const [selectedNotifyStudentId, setSelectedNotifyStudentId] = useState<number | ''>('');

  const [markDialogOpen, setMarkDialogOpen] = useState(false);
  const [markPhysicalStudentId, setMarkPhysicalStudentId] = useState<number | ''>('');
  const [markOptionsLoading, setMarkOptionsLoading] = useState(false);
  const [markOptions, setMarkOptions] = useState<Array<{ id: number; fio: string }>>([]);

  const isAdminLike = me.role === 'ADMIN' || me.role === 'SUPER_ADMIN';
  const selectedSubject = useMemo(() => subjects.find((s) => s.id === selectedSubjectId) || null, [subjects, selectedSubjectId]);

  async function refreshSubjects() {
    const list = await api.getSubjects();
    setSubjects(list);
    if (list.length > 0 && selectedSubjectId == null) setSelectedSubjectId(list[0].id);
  }

  async function refreshQueue() {
    if (selectedSubjectId == null) return;
    setQueueLoading(true);
    try {
      const active = await api.getActiveQueue({ subjectId: selectedSubjectId, queueKind, subgroupNum });
      setQueueItems(active.items || []);
      setQueueDeliveryType(selectedSubject?.deliveryType === 'BRIGADE' ? 'BRIGADE' : 'INDIVIDUAL');
    } finally {
      setQueueLoading(false);
    }
  }

  useEffect(() => {
    let alive = true;
    setLoading(true);
    refreshSubjects()
      .then(() => {
        if (!alive) return;
      })
      .catch(() => {})
      .finally(() => {
        if (!alive) return;
        setLoading(false);
      });
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    refreshQueue().catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedSubjectId, queueKind, subgroupNum, selectedSubject?.deliveryType]);

  async function handleLeave() {
    if (!selectedSubjectId) return;
    await api.leaveQueue({ subjectId: selectedSubjectId, queueKind, subgroupNum: queueKind === 'SUBGROUP' ? subgroupNum : undefined });
    await refreshQueue();
  }

  async function openSwapDialog() {
    setSwapDialogOpen(true);
    setSelectedTargetStudentId('');
    setSelectedTargetBrigadeId('');
    setSelectedNotifyStudentId('');
    setTargetMembers([]);
  }

  async function handleChooseTargetBrigade(bid: number) {
    setSelectedTargetBrigadeId(bid);
    setSelectedNotifyStudentId('');
    setTargetMembers([]);
    setMembersDialogLoading(true);
    try {
      const res = await api.getBrigadeMembers(bid);
      setTargetMembers(res.members || []);
    } finally {
      setMembersDialogLoading(false);
    }
  }

  async function handleCreateSwap() {
    if (!selectedSubjectId) return;
    if (!selectedSubject) return;

    const payload: any = {
      subjectId: selectedSubjectId,
      queueKind,
      subgroupNum: queueKind === 'SUBGROUP' ? subgroupNum : undefined,
    };

    if (selectedSubject.deliveryType === 'INDIVIDUAL') {
      if (selectedTargetStudentId === '') return;
      payload.targetStudentId = selectedTargetStudentId;
    } else {
      if (selectedTargetBrigadeId === '' || selectedNotifyStudentId === '') return;
      payload.targetBrigadeId = selectedTargetBrigadeId;
      payload.targetNotifyStudentId = selectedNotifyStudentId;
    }

    await api.createSwapRequest(payload);
    setSwapDialogOpen(false);
  }

  async function openMarkDialog() {
    if (!selectedSubjectId || !selectedSubject) return;
    setMarkDialogOpen(true);
    setMarkPhysicalStudentId('');
    setMarkOptions([]);
    if (selectedSubject.deliveryType === 'INDIVIDUAL') {
      const opts = (queueItems as any[])
        .filter((it) => it.id != null)
        .map((it) => ({ id: it.id as number, fio: it.fio as string }));
      setMarkOptions(opts);
      setMarkOptionsLoading(false);
      return;
    }

    // BRIGADE: build from all active brigades members
    const brigadeIds = (queueItems as any[]).map((it) => it.id as number);
    setMarkOptionsLoading(true);
    try {
      const all: Array<{ id: number; fio: string }> = [];
      for (const bid of brigadeIds) {
        const res = await api.getBrigadeMembers(bid);
        const ms = res.members || [];
        for (const m of ms) all.push({ id: m.id, fio: m.fio });
      }
      setMarkOptions(all);
    } finally {
      setMarkOptionsLoading(false);
    }
  }

  async function handleMarkLastPassed() {
    if (!selectedSubjectId || markPhysicalStudentId === '' || !selectedSubject) return;
    await api.markLastPassed({
      subjectId: selectedSubjectId,
      queueKind,
      subgroupNum: queueKind === 'SUBGROUP' ? subgroupNum : undefined,
      physicalStudentId: markPhysicalStudentId,
    });
    setMarkDialogOpen(false);
    await refreshQueue();
  }

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="60vh">
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box padding={3} sx={{ background: '#f5faff', minHeight: '100vh' }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2} sx={{ mb: 2 }}>
        <Typography variant="h6" fontWeight={800} color="primary.main">
          Очереди
        </Typography>
        <Stack direction="row" spacing={1} alignItems="center">
          <Chip label={me.role === 'SUPER_ADMIN' ? 'Суперадмин' : me.role === 'ADMIN' ? 'Админ' : 'Пользователь'} color="primary" />
          <Button variant="outlined" onClick={() => (window.location.href = '/login')} size="small">
            Сменить аккаунт
          </Button>
          {isAdminLike ? (
            <Button variant="contained" onClick={() => (window.location.href = '/admin')} size="small">
              Админка
            </Button>
          ) : null}
        </Stack>
      </Stack>

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
        <Card sx={{ width: { md: 320 }, padding: 2, borderRadius: 3 }}>
          <Typography fontWeight={700} sx={{ mb: 1 }}>
            Предметы
          </Typography>
          <Stack spacing={1}>
            {subjects.map((s) => (
              <Button
                key={s.id}
                variant={s.id === selectedSubjectId ? 'contained' : 'text'}
                onClick={() => setSelectedSubjectId(s.id)}
                sx={{ justifyContent: 'flex-start' }}
              >
                {s.name} · {s.deliveryType === 'BRIGADE' ? 'Бригадный' : 'Индивидуальный'}
              </Button>
            ))}
            {subjects.length === 0 ? <Typography color="text.secondary">Пока нет предметов.</Typography> : null}
          </Stack>
        </Card>

        <Card sx={{ flex: 1, padding: 2, borderRadius: 3 }}>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ md: 'center' }}>
            <Box>
              <Typography fontWeight={700} sx={{ mb: 0.5 }}>
                {selectedSubject ? `Пара: ${selectedSubject.name}` : 'Выберите предмет'}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {queueDeliveryType === 'BRIGADE' ? 'Очередь крутится по бригадам' : 'Очередь крутится по студентам'}
              </Typography>
            </Box>
            <Stack direction="row" spacing={1} alignItems="center">
              <FormControl size="small" sx={{ minWidth: 200 }}>
                <InputLabel>Тип очереди</InputLabel>
                <Select
                  label="Тип очереди"
                  value={queueKind}
                  onChange={(e) => setQueueKind(e.target.value as any)}
                >
                  <MenuItem value="COMMON">Общая</MenuItem>
                  <MenuItem value="SUBGROUP">Подгрупповая</MenuItem>
                </Select>
              </FormControl>
              {queueKind === 'SUBGROUP' ? (
                <FormControl size="small" sx={{ minWidth: 120 }}>
                  <InputLabel>Подгруппа</InputLabel>
                  <Select
                    label="Подгруппа"
                    value={subgroupNum}
                    onChange={(e) => setSubgroupNum(Number(e.target.value))}
                  >
                    <MenuItem value={1}>1</MenuItem>
                    <MenuItem value={2}>2</MenuItem>
                  </Select>
                </FormControl>
              ) : null}
              <Button variant="outlined" onClick={handleLeave}>
                Сняться
              </Button>
              <Button variant="contained" onClick={openSwapDialog} disabled={!selectedSubjectId}>
                Запрос на смену
              </Button>
              {isAdminLike ? (
                <Button variant="outlined" onClick={openMarkDialog} disabled={queueItems.length === 0}>
                  Отметить последнего
                </Button>
              ) : null}
            </Stack>
          </Stack>

          <Box sx={{ mt: 2 }}>
            {queueLoading ? (
              <Box display="flex" justifyContent="center" padding={2}>
                <CircularProgress />
              </Box>
            ) : (
              <Stack spacing={1}>
                {queueItems.length === 0 ? (
                  <Typography color="text.secondary">Очередь пуста.</Typography>
                ) : (
                  queueItems.map((it) => (
                    <Box key={it.id} sx={{ display: 'flex', justifyContent: 'space-between', paddingY: 0.5 }}>
                      <Typography>
                        <b>{it.position}.</b>{' '}
                        {selectedSubject?.deliveryType === 'BRIGADE' ? it.displayName : it.fio}
                      </Typography>
                      <Typography color="text.secondary" variant="body2">
                        {selectedSubject?.deliveryType === 'INDIVIDUAL' ? `подгруппа ${it.subgroup}` : 'бригада'}
                      </Typography>
                    </Box>
                  ))
                )}
              </Stack>
            )}
          </Box>
        </Card>
      </Stack>

      <Dialog open={swapDialogOpen} onClose={() => setSwapDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Запрос на обмен</DialogTitle>
        <DialogContent>
          {!selectedSubject ? null : selectedSubject.deliveryType === 'INDIVIDUAL' ? (
            <Stack spacing={2} sx={{ mt: 1 }}>
              <FormControl fullWidth>
                <InputLabel>Кого выбрать</InputLabel>
                <Select
                  label="Кого выбрать"
                  value={selectedTargetStudentId}
                  onChange={(e) => setSelectedTargetStudentId(e.target.value as any)}
                >
                  {queueItems.map((it) => (
                    <MenuItem key={it.id} value={it.id}>
                      {it.fio} (подгруппа {it.subgroup})
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Stack>
          ) : (
            <Stack spacing={2} sx={{ mt: 1 }}>
              <FormControl fullWidth>
                <InputLabel>Бригада</InputLabel>
                <Select
                  label="Бригада"
                  value={selectedTargetBrigadeId}
                  onChange={(e) => handleChooseTargetBrigade(Number(e.target.value))}
                >
                  {queueItems.map((it) => (
                    <MenuItem key={it.id} value={it.id}>
                      {it.displayName}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              {membersDialogLoading ? (
                <Box display="flex" justifyContent="center">
                  <CircularProgress size={24} />
                </Box>
              ) : (
                <FormControl fullWidth disabled={targetMembers.length === 0}>
                  <InputLabel>Кому отправить запрос</InputLabel>
                  <Select
                    label="Кому отправить запрос"
                    value={selectedNotifyStudentId}
                    onChange={(e) => setSelectedNotifyStudentId(Number(e.target.value))}
                  >
                    {targetMembers.map((m) => (
                      <MenuItem key={m.id} value={m.id}>
                        {m.fio}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              )}
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSwapDialogOpen(false)} variant="outlined">
            Отмена
          </Button>
          <Button onClick={handleCreateSwap} variant="contained">
            Отправить
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={markDialogOpen} onClose={() => setMarkDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Отметить последнего сдавшего</DialogTitle>
        <DialogContent>
          {markOptionsLoading ? (
            <Box display="flex" justifyContent="center" padding={2}>
              <CircularProgress />
            </Box>
          ) : (
            <FormControl fullWidth>
              <InputLabel>Студент</InputLabel>
              <Select
                label="Студент"
                value={markPhysicalStudentId}
                onChange={(e) => setMarkPhysicalStudentId(Number(e.target.value))}
              >
                {markOptions.map((o) => (
                  <MenuItem key={o.id} value={o.id}>
                    {o.fio}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setMarkDialogOpen(false)} variant="outlined">
            Отмена
          </Button>
          <Button onClick={handleMarkLastPassed} variant="contained" disabled={markPhysicalStudentId === ''}>
            Сохранить
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

