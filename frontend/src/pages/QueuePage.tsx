import React, { useEffect, useState, useRef } from 'react';
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
  IconButton,
} from '@mui/material';
import { Brightness4, Brightness7 } from '@mui/icons-material';
import { api, type ApiMe, type QueueUpdateEvent } from '../api/client';
import { useTheme } from '../theme/themeContext';

type QueueItem =
  | { position: number; id: number; fio: string; subgroup: number }
  | { position: number; id: number; displayName: string };

export function QueuePage({ me }: { me: ApiMe; onMeChange?: (me: ApiMe) => void }) {
  const { toggleTheme, isDarkMode } = useTheme();
  const [subjects, setSubjects] = useState<Array<{ id: number; name: string; deliveryType: string }>>([]);
  const [loading, setLoading] = useState(true);
  const [selectedSubjectId, setSelectedSubjectId] = useState<number | null>(null);
  const [queueKind, setQueueKind] = useState<'COMMON' | 'SUBGROUP'>('COMMON');
  const [subgroupNum, setSubgroupNum] = useState<number>(1);
  const [queueItems, setQueueItems] = useState<any[]>([]);
  const [queueLoading, setQueueLoading] = useState(false);
  const [queueDeliveryType, setQueueDeliveryType] = useState<'INDIVIDUAL' | 'BRIGADE'>('INDIVIDUAL');
  const [inQueue, setInQueue] = useState<boolean>(false);
  const [statusLoading, setStatusLoading] = useState(false);

  const [swapDialogOpen, setSwapDialogOpen] = useState(false);
  const [selectedTargetStudentId, setSelectedTargetStudentId] = useState<number | ''>('');
  const [selectedTargetBrigadeId, setSelectedTargetBrigadeId] = useState<number | ''>('');
  const [membersDialogLoading, setMembersDialogLoading] = useState(false);
  const [targetMembers, setTargetMembers] = useState<Array<{ id: number; fio: string; telegramTag: string }>>([]);
  const [selectedNotifyStudentId, setSelectedNotifyStudentId] = useState<number | ''>('');

  const [markDialogOpen, setMarkDialogOpen] = useState(false);
  const [markPhysicalStudentId, setMarkPhysicalStudentId] = useState<number | ''>('');
  const [markBrigadeId, setMarkBrigadeId] = useState<number | ''>('');
  const [markOptionsLoading, setMarkOptionsLoading] = useState(false);
  const [markOptions, setMarkOptions] = useState<Array<{ id: number; fio: string }>>([]);
  const [markBrigadeOptions, setMarkBrigadeOptions] = useState<Array<{ id: number; displayName: string }>>([]);

  const isAdminLike = me.role === 'ADMIN' || me.role === 'SUPER_ADMIN';
  const selectedSubject = subjects.find((s) => s.id === selectedSubjectId) || null;

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

  async function refreshQueueStatus() {
    if (selectedSubjectId == null) return;
    setStatusLoading(true);
    try {
      const status = await api.getQueueStatus({ subjectId: selectedSubjectId, queueKind, subgroupNum: queueKind === 'SUBGROUP' ? subgroupNum : undefined });
      setInQueue(status.inQueue ?? false);
    } finally {
      setStatusLoading(false);
    }
  }

  useEffect(() => {
    let alive = true;
    setLoading(true);
    refreshSubjects()
      .finally(() => {
        if (!alive) return;
        setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  useEffect(() => {
    refreshQueue().catch(() => {});
  }, [selectedSubjectId, queueKind, subgroupNum, selectedSubject?.deliveryType]);

  useEffect(() => {
    refreshQueueStatus().catch(() => {});
  }, [selectedSubjectId, queueKind, subgroupNum, selectedSubject?.deliveryType]);

  useEffect(() => {
    const eventSource = api.streamQueueUpdates();
    
    eventSource.onmessage = (event) => {
      try {
        const data: QueueUpdateEvent = JSON.parse(event.data);
        // Check if this update is for the currently selected subject/queue
        if (data.subjectId === selectedSubjectId &&
            data.queueKind === queueKind &&
            data.subgroupNum === (queueKind === 'SUBGROUP' ? subgroupNum : null)) {
          refreshQueue();
          refreshQueueStatus();
        }
      } catch (e) {
        console.error('Failed to parse SSE message:', e);
      }
    };

    eventSource.onerror = (error) => {
      console.error('SSE connection error:', error);
      eventSource.close();
    };

    return () => {
      eventSource.close();
    };
  }, [selectedSubjectId, queueKind, subgroupNum]);

  useEffect(() => {
    refreshQueueStatus().catch(() => {});
  }, [selectedSubjectId, queueKind, subgroupNum, selectedSubject?.deliveryType]);

  async function handleLeave() {
    if (!selectedSubjectId) return;
    await api.leaveQueue({ subjectId: selectedSubjectId, queueKind, subgroupNum: queueKind === 'SUBGROUP' ? subgroupNum : undefined });
    await Promise.all([refreshQueue(), refreshQueueStatus()]);
  }

  async function handleJoin() {
    if (!selectedSubjectId) return;
    await api.joinQueue({ subjectId: selectedSubjectId, queueKind, subgroupNum: queueKind === 'SUBGROUP' ? subgroupNum : undefined });
    await Promise.all([refreshQueue(), refreshQueueStatus()]);
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
    setMarkBrigadeId('');
    setMarkOptions([]);
    setMarkBrigadeOptions([]);
    if (selectedSubject.deliveryType === 'INDIVIDUAL') {
      const opts = (queueItems as any[])
        .filter((it) => it.id != null)
        .map((it) => ({ id: it.id as number, fio: it.fio as string }));
      setMarkOptions(opts);
      setMarkOptionsLoading(false);
      return;
    }

    // For brigade queues, show brigades directly
    const brigadeOpts = (queueItems as any[])
      .filter((it) => it.id != null)
      .map((it) => ({ id: it.id as number, displayName: it.displayName as string }));
    setMarkBrigadeOptions(brigadeOpts);
    setMarkOptionsLoading(false);
  }

  async function handleMarkLastPassed() {
    if (!selectedSubjectId || !selectedSubject) return;
    
    const payload: any = {
      subjectId: selectedSubjectId,
      queueKind,
      subgroupNum: queueKind === 'SUBGROUP' ? subgroupNum : undefined,
    };
    
    if (selectedSubject.deliveryType === 'INDIVIDUAL') {
      if (markPhysicalStudentId === '') return;
      payload.physicalStudentId = markPhysicalStudentId;
    } else {
      if (markBrigadeId === '') return;
      payload.brigadeId = markBrigadeId;
    }
    
    await api.markLastPassed(payload);
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
    <Box sx={{ p: 3, minHeight: '100vh', bgcolor: 'background.default' }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2} sx={{ mb: 3 }}>
        <Typography variant="h4" fontWeight={700} color="primary.main">
          Queue
        </Typography>
        <Stack direction="row" spacing={1.5} alignItems="center">
          <Chip label={me.role === 'SUPER_ADMIN' ? 'Super' : me.role === 'ADMIN' ? 'Admin' : 'User'} color="primary" size="small" />
          <IconButton onClick={toggleTheme} color="inherit" size="small">
            {isDarkMode ? <Brightness7 /> : <Brightness4 />}
          </IconButton>
          <Button variant="outlined" onClick={() => (window.location.href = '/login')} size="small">
            Switch
          </Button>
          {isAdminLike ? (
            <Button variant="contained" onClick={() => (window.location.href = '/admin')} size="small">
              Admin
            </Button>
          ) : null}
        </Stack>
      </Stack>

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
        <Card sx={{ width: { md: 320 }, p: 2.5, borderRadius: 3 }}>
          <Typography fontWeight={700} sx={{ mb: 1.5 }} variant="h6">
            Subjects
          </Typography>
          <Stack spacing={1}>
            {subjects.map((s) => (
              <Button
                key={s.id}
                variant={s.id === selectedSubjectId ? 'contained' : 'text'}
                onClick={() => setSelectedSubjectId(s.id)}
                sx={{ justifyContent: 'flex-start' }}
              >
                {s.name} · {s.deliveryType === 'BRIGADE' ? 'Brigade' : 'Individual'}
              </Button>
            ))}
            {subjects.length === 0 ? <Typography color="text.secondary">No subjects.</Typography> : null}
          </Stack>
        </Card>

        <Card sx={{ flex: 1, p: 2.5, borderRadius: 3 }}>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ md: 'center' }}>
            <Box>
              <Typography fontWeight={700} sx={{ mb: 0.5 }} variant="h6">
                {selectedSubject ? `${selectedSubject.name}` : 'Select subject'}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {queueDeliveryType === 'BRIGADE' ? 'Queue by brigades' : 'Queue by students'}
              </Typography>
            </Box>
            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" gap={1}>
              <FormControl size="small" sx={{ minWidth: 160 }}>
                <InputLabel>Type</InputLabel>
                <Select
                  label="Type"
                  value={queueKind}
                  onChange={(e) => setQueueKind(e.target.value as any)}
                >
                  <MenuItem value="COMMON">Common</MenuItem>
                  <MenuItem value="SUBGROUP">Subgroup</MenuItem>
                </Select>
              </FormControl>
              {queueKind === 'SUBGROUP' ? (
                <FormControl size="small" sx={{ minWidth: 100 }}>
                  <InputLabel>Subgroup</InputLabel>
                  <Select
                    label="Subgroup"
                    value={subgroupNum}
                    onChange={(e) => setSubgroupNum(Number(e.target.value))}
                  >
                    <MenuItem value={1}>1</MenuItem>
                    <MenuItem value={2}>2</MenuItem>
                  </Select>
                </FormControl>
              ) : null}
              {inQueue ? (
                <Button variant="outlined" onClick={handleLeave} disabled={statusLoading}>
                  Leave
                </Button>
              ) : (
                <Button variant="contained" onClick={handleJoin} disabled={statusLoading}>
                  Join
                </Button>
              )}
              <Button variant="contained" onClick={openSwapDialog} disabled={!selectedSubjectId}>
                Swap
              </Button>
              {isAdminLike ? (
                <Button variant="outlined" onClick={openMarkDialog} disabled={queueItems.length === 0}>
                  Mark
                </Button>
              ) : null}
            </Stack>
          </Stack>

          <Box sx={{ mt: 2.5 }}>
            {queueLoading ? (
              <Box display="flex" justifyContent="center" p={2}>
                <CircularProgress />
              </Box>
            ) : (
              <Stack spacing={1}>
                {queueItems.length === 0 ? (
                  <Typography color="text.secondary">Queue is empty.</Typography>
                ) : (
                  queueItems.map((it) => (
                    <Box key={it.id} sx={{ display: 'flex', justifyContent: 'space-between', py: 0.5 }}>
                      <Typography>
                        <b>{it.position}.</b>{' '}
                        {selectedSubject?.deliveryType === 'BRIGADE' ? it.displayName : it.fio}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {selectedSubject?.deliveryType === 'INDIVIDUAL' ? `subgroup ${it.subgroup}` : 'brigade'}
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
        <DialogTitle>Swap Request</DialogTitle>
        <DialogContent>
          {!selectedSubject ? null : selectedSubject.deliveryType === 'INDIVIDUAL' ? (
            <Stack spacing={2} sx={{ mt: 1 }}>
              <FormControl fullWidth>
                <InputLabel>Select student</InputLabel>
                <Select
                  label="Select student"
                  value={selectedTargetStudentId}
                  onChange={(e) => setSelectedTargetStudentId(e.target.value as any)}
                >
                  {queueItems.map((it) => (
                    <MenuItem key={it.id} value={it.id}>
                      {it.fio} (subgroup {it.subgroup})
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Stack>
          ) : (
            <Stack spacing={2} sx={{ mt: 1 }}>
              <FormControl fullWidth>
                <InputLabel>Brigade</InputLabel>
                <Select
                  label="Brigade"
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
                  <InputLabel>Notify member</InputLabel>
                  <Select
                    label="Notify member"
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
            Cancel
          </Button>
          <Button onClick={handleCreateSwap} variant="contained">
            Send
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={markDialogOpen} onClose={() => setMarkDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Mark Last Passed</DialogTitle>
        <DialogContent>
          {!selectedSubject ? null : selectedSubject.deliveryType === 'INDIVIDUAL' ? (
            <FormControl fullWidth sx={{ mt: 1 }}>
              <InputLabel>Student</InputLabel>
              <Select
                label="Student"
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
          ) : (
            <FormControl fullWidth sx={{ mt: 1 }}>
              <InputLabel>Brigade</InputLabel>
              <Select
                label="Brigade"
                value={markBrigadeId}
                onChange={(e) => setMarkBrigadeId(Number(e.target.value))}
              >
                {markBrigadeOptions.map((o) => (
                  <MenuItem key={o.id} value={o.id}>
                    {o.displayName}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setMarkDialogOpen(false)} variant="outlined">
            Cancel
          </Button>
          <Button
            onClick={handleMarkLastPassed}
            variant="contained"
            disabled={selectedSubject?.deliveryType === 'INDIVIDUAL' ? markPhysicalStudentId === '' : markBrigadeId === ''}
          >
            Save
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
