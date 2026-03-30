import React, { useEffect, useMemo, useState } from 'react';
import {
  Box,
  Button,
  Card,
  Checkbox,
  Chip,
  CircularProgress,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import { api, type ApiMe } from '../api/client';

type StudentRow = {
  id: number;
  fio: string;
  subgroup: number;
  telegramTag: string;
  isAdmin: boolean;
  isSuperAdmin: boolean;
};

type SubjectRow = { id: number; name: string; deliveryType: string };

export function AdminPage({ me, onMeChange }: { me: ApiMe; onMeChange: (me: ApiMe | null) => void }) {
  const [tab, setTab] = useState(0);
  const [loading, setLoading] = useState(true);
  const [students, setStudents] = useState<StudentRow[]>([]);
  const [subjects, setSubjects] = useState<SubjectRow[]>([]);
  const [error, setError] = useState<string | null>(null);

  // add student
  const [newFio, setNewFio] = useState('');
  const [newTag, setNewTag] = useState('');
  const [newSubgroup, setNewSubgroup] = useState<1 | 2>(1);

  // add subject
  const [newSubjectName, setNewSubjectName] = useState('');
  const [newSubjectDeliveryType, setNewSubjectDeliveryType] = useState<'INDIVIDUAL' | 'BRIGADE'>('INDIVIDUAL');

  const brigadeSubjects = useMemo(() => subjects.filter((s) => s.deliveryType === 'BRIGADE'), [subjects]);
  const [selectedBrigadeSubjectId, setSelectedBrigadeSubjectId] = useState<number | null>(brigadeSubjects[0]?.id ?? null);

  // brigades builder (re-create)
  const [builderSelected, setBuilderSelected] = useState<Set<number>>(new Set());
  const [groups, setGroups] = useState<number[][]>([]);

  const selectedBrigadeSubject = useMemo(() => subjects.find((s) => s.id === selectedBrigadeSubjectId) || null, [subjects, selectedBrigadeSubjectId]);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    Promise.all([fetchStudents(), fetchSubjects()])
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
    if (brigadeSubjects.length > 0 && selectedBrigadeSubjectId == null) {
      setSelectedBrigadeSubjectId(brigadeSubjects[0].id);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [brigadeSubjects]);

  async function fetchStudents() {
    const res = await fetch('/api/admin/students', { credentials: 'include' });
    if (!res.ok) throw new Error('Failed to load students');
    const data = await res.json();
    setStudents(data);
  }

  async function fetchSubjects() {
    const res = await fetch('/api/admin/subjects', { credentials: 'include' });
    if (!res.ok) throw new Error('Failed to load subjects');
    const data = await res.json();
    setSubjects(data);
  }

  async function handleAddStudent() {
    setError(null);
    const payload = { fio: newFio, telegramTag: newTag, subgroup: newSubgroup };
    const res = await fetch('/api/admin/students', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(String(data?.error || data?.message || 'Failed'));
    await fetchStudents();
    setNewFio('');
    setNewTag('');
    setNewSubgroup(1);
  }

  async function handleAddSubject() {
    setError(null);
    const payload = { name: newSubjectName, deliveryType: newSubjectDeliveryType };
    const res = await fetch('/api/admin/subjects', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(String(data?.error || data?.message || 'Failed'));
    await fetchSubjects();
    setNewSubjectName('');
    setNewSubjectDeliveryType('INDIVIDUAL');
  }

  async function handleToggleAdmin(studentId: number, enabled: boolean) {
    const res = await fetch('/api/admin/roles/admin', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ studentId, enabled }),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(String(data?.error || data?.message || 'Failed'));
    await fetchStudents();
    const me2 = await api.getMe();
    onMeChange(me2);
  }

  async function handleUpdateSubgroup(studentId: number, subgroup: 1 | 2) {
    setError(null);
    const res = await fetch(`/api/admin/students/${studentId}/subgroup`, {
      method: 'PATCH',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ subgroup }),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(String(data?.error || data?.message || 'Failed'));
    await fetchStudents();
  }

  function toggleBuilderStudent(id: number) {
    setBuilderSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  const assignedStudentIds = useMemo(() => new Set(groups.flatMap((g) => g)), [groups]);

  function addGroupFromSelected() {
    if (builderSelected.size === 0) return;
    const ids = Array.from(builderSelected).sort((a, b) => a - b);
    // avoid duplicates across groups
    for (const id of ids) {
      if (assignedStudentIds.has(id)) return;
    }
    setGroups((prev) => [...prev, ids]);
    setBuilderSelected(new Set());
  }

  function removeGroup(idx: number) {
    setGroups((prev) => prev.filter((_, i) => i !== idx));
  }

  async function saveBrigades() {
    if (!selectedBrigadeSubjectId) return;
    setError(null);

    const allIds = new Set(students.map((s) => s.id));
    const used = new Set(groups.flatMap((g) => g));

    if (used.size !== allIds.size) {
      setError('Каждый студент должен входить ровно в одну бригаду.');
      return;
    }

    const payload = { brigades: groups };
    const res = await fetch(`/api/admin/subjects/${selectedBrigadeSubjectId}/brigades`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(String(data?.error || data?.message || 'Failed'));

    setError(null);
    setGroups([]);
    setBuilderSelected(new Set());
    await fetchSubjects();
    await fetchStudents();
  }

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="60vh">
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box padding={3} bgcolor="#f5faff" minHeight="100vh">
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
        <Typography variant="h6" fontWeight={800} color="primary.main">
          Админка
        </Typography>
        <Stack direction="row" spacing={1}>
          <Chip label={me.role === 'SUPER_ADMIN' ? 'Суперадмин' : 'Админ'} color="primary" />
        </Stack>
      </Stack>

      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
        <Tab label="Студенты" />
        <Tab label="Предметы" />
        <Tab label="Бригады" disabled={brigadeSubjects.length === 0} />
        <Tab label="Роли (super)" disabled={me.role !== 'SUPER_ADMIN'} />
      </Tabs>

      {tab === 0 ? (
        <Stack spacing={2}>
          <Card sx={{ p: 2, borderRadius: 3 }}>
            <Typography fontWeight={700} sx={{ mb: 1 }}>
              Добавить студента
            </Typography>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <TextField label="ФИО" value={newFio} onChange={(e) => setNewFio(e.target.value)} fullWidth />
              <TextField label="telegram_tag" value={newTag} onChange={(e) => setNewTag(e.target.value)} fullWidth />
              <FormControl sx={{ minWidth: 140 }}>
                <InputLabel>Подгруппа</InputLabel>
                <Select value={newSubgroup} label="Подгруппа" onChange={(e) => setNewSubgroup(Number(e.target.value) as any)}>
                  <MenuItem value={1}>1</MenuItem>
                  <MenuItem value={2}>2</MenuItem>
                </Select>
              </FormControl>
              <Button variant="contained" onClick={async () => handleAddStudent()}>
                Добавить
              </Button>
            </Stack>
            {error ? (
              <Typography color="error" sx={{ mt: 1 }}>
                {error}
              </Typography>
            ) : null}
          </Card>

          <Card sx={{ p: 2, borderRadius: 3 }}>
            <Typography fontWeight={700} sx={{ mb: 1 }}>
              Список студентов
            </Typography>
            <Stack spacing={1}>
              {students.map((st) => (
                <Box key={st.id} sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
                  <Typography>
                    {st.fio} · подгруппа {st.subgroup} · @{st.telegramTag}
                  </Typography>
                  <Stack direction="row" spacing={1} alignItems="center">
                    {st.isSuperAdmin ? <Chip label="Super" color="secondary" size="small" /> : null}
                    {st.isAdmin ? <Chip label="Админ" color="primary" size="small" /> : null}
                    <FormControl size="small" sx={{ minWidth: 140 }}>
                      <InputLabel>Подгруппа</InputLabel>
                      <Select
                        label="Подгруппа"
                        value={st.subgroup}
                        onChange={(e) => handleUpdateSubgroup(st.id, Number(e.target.value) as any)}
                      >
                        <MenuItem value={1}>1</MenuItem>
                        <MenuItem value={2}>2</MenuItem>
                      </Select>
                    </FormControl>
                  </Stack>
                </Box>
              ))}
              {students.length === 0 ? <Typography color="text.secondary">Пока пусто.</Typography> : null}
            </Stack>
          </Card>
        </Stack>
      ) : null}

      {tab === 1 ? (
        <Stack spacing={2}>
          <Card sx={{ p: 2, borderRadius: 3 }}>
            <Typography fontWeight={700} sx={{ mb: 1 }}>
              Добавить предмет
            </Typography>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <TextField label="Аббревиатура предмета" value={newSubjectName} onChange={(e) => setNewSubjectName(e.target.value)} fullWidth />
              <FormControl sx={{ minWidth: 220 }}>
                <InputLabel>Тип сдачи</InputLabel>
                <Select value={newSubjectDeliveryType} label="Тип сдачи" onChange={(e) => setNewSubjectDeliveryType(e.target.value as any)}>
                  <MenuItem value="INDIVIDUAL">Индивидуальный</MenuItem>
                  <MenuItem value="BRIGADE">Бригадный</MenuItem>
                </Select>
              </FormControl>
              <Button variant="contained" onClick={async () => handleAddSubject()}>
                Добавить
              </Button>
            </Stack>
            {error ? (
              <Typography color="error" sx={{ mt: 1 }}>
                {error}
              </Typography>
            ) : null}
          </Card>

          <Card sx={{ p: 2, borderRadius: 3 }}>
            <Typography fontWeight={700} sx={{ mb: 1 }}>
              Предметы
            </Typography>
            <Stack spacing={1}>
              {subjects.map((subj) => (
                <Box key={subj.id} sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
                  <Typography>
                    {subj.name} · {subj.deliveryType === 'BRIGADE' ? 'Бригадный' : 'Индивидуальный'}
                  </Typography>
                  {subj.deliveryType === 'BRIGADE' ? (
                    <Button size="small" variant={selectedBrigadeSubjectId === subj.id ? 'contained' : 'outlined'} onClick={() => setSelectedBrigadeSubjectId(subj.id)}>
                      Настроить бригады
                    </Button>
                  ) : null}
                </Box>
              ))}
              {subjects.length === 0 ? <Typography color="text.secondary">Пока нет предметов.</Typography> : null}
            </Stack>
          </Card>
        </Stack>
      ) : null}

      {tab === 2 ? (
        <Stack spacing={2}>
          <Card sx={{ p: 2, borderRadius: 3 }}>
            <Typography fontWeight={700} sx={{ mb: 1 }}>
              Создание бригад для предмета
            </Typography>
            {selectedBrigadeSubject ? (
              <Typography color="text.secondary" sx={{ mb: 2 }}>
                Предмет: <b>{selectedBrigadeSubject.name}</b>
              </Typography>
            ) : (
              <Typography color="text.secondary">Выберите предмет.</Typography>
            )}

            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <Card sx={{ flex: 1, p: 2, borderRadius: 3 }}>
                <Typography fontWeight={700} sx={{ mb: 1 }}>
                  Собрать новую бригаду
                </Typography>
                <Stack spacing={1} sx={{ maxHeight: 320, overflow: 'auto' }}>
                  {students.map((st) => {
                    const disabled = assignedStudentIds.has(st.id);
                    return (
                      <Box key={st.id} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Checkbox
                          checked={builderSelected.has(st.id)}
                          disabled={disabled}
                          onChange={() => toggleBuilderStudent(st.id)}
                        />
                        <Typography>
                          {st.fio} · @{st.telegramTag}
                        </Typography>
                      </Box>
                    );
                  })}
                </Stack>
                <Button variant="outlined" sx={{ mt: 1 }} onClick={addGroupFromSelected} disabled={builderSelected.size === 0}>
                  Добавить бригаду (из выбранных)
                </Button>
              </Card>

              <Card sx={{ flex: 1, p: 2, borderRadius: 3 }}>
                <Typography fontWeight={700} sx={{ mb: 1 }}>
                  Созданные бригады
                </Typography>
                <Stack spacing={1}>
                  {groups.length === 0 ? <Typography color="text.secondary">Пока бригад нет. Соберите первую.</Typography> : null}
                  {groups.map((g, idx) => (
                    <Box key={idx} sx={{ display: 'flex', justifyContent: 'space-between', gap: 1, alignItems: 'center' }}>
                      <Typography variant="body2" sx={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {idx + 1}.{' '}
                        {g
                          .map((sid) => students.find((s) => s.id === sid)?.fio.split(' ')[0])
                          .filter(Boolean)
                          .join(', ')}
                      </Typography>
                      <Button size="small" variant="text" color="error" onClick={() => removeGroup(idx)}>
                        Удалить
                      </Button>
                    </Box>
                  ))}
                </Stack>

                <Box sx={{ mt: 2 }}>
                  <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                    Распределено студентов: {usedCountText(groups, students)}
                  </Typography>
                  <Button variant="contained" onClick={saveBrigades} disabled={!selectedBrigadeSubjectId}>
                    Сохранить бригады
                  </Button>
                  {error ? (
                    <Typography color="error" sx={{ mt: 1 }}>
                      {error}
                    </Typography>
                  ) : null}
                </Box>
              </Card>
            </Stack>
          </Card>
        </Stack>
      ) : null}

      {tab === 3 ? (
        <Stack spacing={2}>
          <Card sx={{ p: 2, borderRadius: 3 }}>
            <Typography fontWeight={700} sx={{ mb: 1 }}>
              Управление администраторами
            </Typography>
            <Stack spacing={1}>
              {students.map((st) => (
                <Box key={st.id} sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <Typography>{st.fio}</Typography>
                  <Checkbox
                    checked={st.isAdmin}
                    disabled={st.isSuperAdmin}
                    onChange={(e) => handleToggleAdmin(st.id, e.target.checked)}
                  />
                </Box>
              ))}
            </Stack>
          </Card>
        </Stack>
      ) : null}
    </Box>
  );
}

function usedCountText(groups: number[][], students: StudentRow[]) {
  const used = new Set(groups.flatMap((g) => g));
  return `${used.size} / ${students.length}`;
}

