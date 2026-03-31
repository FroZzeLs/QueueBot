import React, { useEffect, useState } from 'react';
import {
  Box,
  Button,
  Card,
  Checkbox,
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
  Tabs,
  Tab,
} from '@mui/material';
import { Brightness4, Brightness7, ArrowBack, Edit, Delete } from '@mui/icons-material';
import { api, type ApiMe } from '../api/client';
import { useTheme } from '../theme/themeContext';
import { useNavigate } from 'react-router-dom';

type StudentRow = {
  id: number;
  fio: string;
  subgroup: number;
  telegramTag: string;
  isAdmin: boolean;
  isSuperAdmin: boolean;
};

type SubjectRow = { id: number; name: string; deliveryType: string };

type EditStudentState = {
  open: boolean;
  studentId: number | null;
  fio: string;
  telegramTag: string;
  subgroup: number;
};

export function AdminPage({ me, onMeChange }: { me: ApiMe; onMeChange: (me: ApiMe | null) => void }) {
  const { toggleTheme, isDarkMode } = useTheme();
  const navigate = useNavigate();
  const [tab, setTab] = useState(0);
  const [loading, setLoading] = useState(true);
  const [students, setStudents] = useState<StudentRow[]>([]);
  const [subjects, setSubjects] = useState<SubjectRow[]>([]);
  const [error, setError] = useState<string | null>(null);

  const [newFio, setNewFio] = useState('');
  const [newTag, setNewTag] = useState('');
  const [newSubgroup, setNewSubgroup] = useState<1 | 2>(1);

  const [newSubjectName, setNewSubjectName] = useState('');
  const [newSubjectDeliveryType, setNewSubjectDeliveryType] = useState<'INDIVIDUAL' | 'BRIGADE'>('INDIVIDUAL');

  const [editDialog, setEditDialog] = useState<EditStudentState>({
    open: false,
    studentId: null,
    fio: '',
    telegramTag: '',
    subgroup: 1,
  });

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deleteStudentId, setDeleteStudentId] = useState<number | null>(null);

  const brigadeSubjects = subjects.filter((s) => s.deliveryType === 'BRIGADE');
  const [selectedBrigadeSubjectId, setSelectedBrigadeSubjectId] = useState<number | null>(brigadeSubjects[0]?.id ?? null);

  const [builderSelected, setBuilderSelected] = useState<Set<number>>(new Set());
  const [groups, setGroups] = useState<number[][]>([]);
  const [brigadesLoading, setBrigadesLoading] = useState(false);

  const selectedBrigadeSubject = subjects.find((s) => s.id === selectedBrigadeSubjectId) || null;

  // Load brigades for selected subject
  useEffect(() => {
    if (!selectedBrigadeSubjectId) {
      setGroups([]);
      return;
    }
    setBrigadesLoading(true);
    api.getBrigadesForSubject(selectedBrigadeSubjectId)
      .then(res => {
        // Convert brigades to groups of student IDs
        const loadedGroups = res.brigades.map((b: any) => b.memberIds);
        setGroups(loadedGroups);
        setBuilderSelected(new Set());
      })
      .catch(() => {
        setGroups([]);
      })
      .finally(() => {
        setBrigadesLoading(false);
      });
  }, [selectedBrigadeSubjectId]);

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
  }, []);

  useEffect(() => {
    if (brigadeSubjects.length > 0 && selectedBrigadeSubjectId == null) {
      setSelectedBrigadeSubjectId(brigadeSubjects[0].id);
    }
  }, [brigadeSubjects, selectedBrigadeSubjectId]);

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

  function openEditDialog(student: StudentRow) {
    setEditDialog({
      open: true,
      studentId: student.id,
      fio: student.fio,
      telegramTag: student.telegramTag || '',
      subgroup: student.subgroup,
    });
  }

  async function handleSaveEdit() {
    setError(null);
    if (!editDialog.studentId) return;

    const payload = {
      fio: editDialog.fio,
      telegramTag: editDialog.telegramTag,
      subgroup: editDialog.subgroup,
    };

    const res = await fetch(`/api/admin/students/${editDialog.studentId}`, {
      method: 'PUT',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(String(data?.error || data?.message || 'Failed'));

    setEditDialog({ ...editDialog, open: false });
    await fetchStudents();
  }

  async function handleDeleteStudent() {
    setError(null);
    if (!deleteStudentId) return;

    const res = await fetch(`/api/admin/students/${deleteStudentId}`, {
      method: 'DELETE',
      credentials: 'include',
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(String(data?.error || data?.message || 'Failed'));

    setDeleteDialogOpen(false);
    setDeleteStudentId(null);
    await fetchStudents();
  }

  function confirmDelete(studentId: number) {
    setDeleteStudentId(studentId);
    setDeleteDialogOpen(true);
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

  function toggleBuilderStudent(id: number) {
    setBuilderSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  const assignedStudentIds = new Set(groups.flatMap((g) => g));

  function addGroupFromSelected() {
    if (builderSelected.size === 0) return;
    const ids = Array.from(builderSelected).sort((a, b) => a - b);
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
      setError('Each student must be in exactly one brigade.');
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
    <Box sx={{ p: 3, minHeight: '100vh', bgcolor: 'background.default' }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
        <Stack direction="row" spacing={1.5} alignItems="center">
          <IconButton onClick={() => navigate('/')} size="small">
            <ArrowBack />
          </IconButton>
          <Typography variant="h4" fontWeight={700} color="primary.main">
            Admin
          </Typography>
        </Stack>
        <Stack direction="row" spacing={1.5} alignItems="center">
          <Chip label={me.role === 'SUPER_ADMIN' ? 'Super' : 'Admin'} color="primary" size="small" />
          <IconButton onClick={toggleTheme} color="inherit" size="small">
            {isDarkMode ? <Brightness7 /> : <Brightness4 />}
          </IconButton>
        </Stack>
      </Stack>

      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
        <Tab label="Students" />
        <Tab label="Subjects" />
        <Tab label="Brigades" disabled={brigadeSubjects.length === 0} />
        <Tab label="Roles" disabled={me.role !== 'SUPER_ADMIN'} />
      </Tabs>

      {tab === 0 ? (
        <Stack spacing={2}>
          <Card sx={{ p: 2.5, borderRadius: 3 }}>
            <Typography fontWeight={700} sx={{ mb: 1.5 }} variant="h6">
              Add Student
            </Typography>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <TextField
                label="Name"
                value={newFio}
                onChange={(e) => setNewFio(e.target.value)}
                fullWidth
              />
              <TextField
                label="Telegram"
                value={newTag}
                onChange={(e) => setNewTag(e.target.value)}
                fullWidth
                placeholder="username"
              />
              <FormControl sx={{ minWidth: 140 }}>
                <InputLabel>Subgroup</InputLabel>
                <Select
                  value={newSubgroup}
                  label="Subgroup"
                  onChange={(e) => setNewSubgroup(Number(e.target.value))}
                >
                  <MenuItem value={1}>1</MenuItem>
                  <MenuItem value={2}>2</MenuItem>
                </Select>
              </FormControl>
              <Button variant="contained" onClick={async () => await handleAddStudent()}>
                Add
              </Button>
            </Stack>
            {error && (
              <Typography color="error" sx={{ mt: 1 }}>
                {error}
              </Typography>
            )}
          </Card>

          <Card sx={{ p: 2.5, borderRadius: 3 }}>
            <Typography fontWeight={700} sx={{ mb: 1.5 }} variant="h6">
              Students
            </Typography>
            <Stack spacing={1}>
              {students.map((st) => (
                <Box key={st.id} sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
                  <Typography>
                    {st.fio} · subgroup {st.subgroup} · @{st.telegramTag || 'no tag'}
                  </Typography>
                  <Stack direction="row" spacing={1} alignItems="center">
                    {st.isSuperAdmin ? (
                      <Chip label="Super" color="secondary" size="small" />
                    ) : null}
                    {st.isAdmin ? (
                      <Chip label="Admin" color="primary" size="small" />
                    ) : null}
                    <IconButton size="small" onClick={() => openEditDialog(st)} disabled={st.isSuperAdmin && st.id !== me.id}>
                      <Edit />
                    </IconButton>
                    <IconButton size="small" color="error" onClick={() => confirmDelete(st.id)} disabled={st.isSuperAdmin}>
                      <Delete />
                    </IconButton>
                  </Stack>
                </Box>
              ))}
              {students.length === 0 ? (
                <Typography color="text.secondary">No students.</Typography>
              ) : null}
            </Stack>
          </Card>
        </Stack>
      ) : null}

      {tab === 1 ? (
        <Stack spacing={2}>
          <Card sx={{ p: 2.5, borderRadius: 3 }}>
            <Typography fontWeight={700} sx={{ mb: 1.5 }} variant="h6">
              Add Subject
            </Typography>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <TextField
                label="Subject"
                value={newSubjectName}
                onChange={(e) => setNewSubjectName(e.target.value)}
                fullWidth
              />
              <FormControl sx={{ minWidth: 220 }}>
                <InputLabel>Type</InputLabel>
                <Select
                  value={newSubjectDeliveryType}
                  label="Type"
                  onChange={(e) => setNewSubjectDeliveryType(e.target.value as any)}
                >
                  <MenuItem value="INDIVIDUAL">Individual</MenuItem>
                  <MenuItem value="BRIGADE">Brigade</MenuItem>
                </Select>
              </FormControl>
              <Button variant="contained" onClick={async () => await handleAddSubject()}>
                Add
              </Button>
            </Stack>
            {error && (
              <Typography color="error" sx={{ mt: 1 }}>
                {error}
              </Typography>
            )}
          </Card>

          <Card sx={{ p: 2.5, borderRadius: 3 }}>
            <Typography fontWeight={700} sx={{ mb: 1.5 }} variant="h6">
              Subjects
            </Typography>
            <Stack spacing={1}>
              {subjects.map((subj) => (
                <Box key={subj.id} sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
                  <Typography>
                    {subj.name} · {subj.deliveryType === 'BRIGADE' ? 'Brigade' : 'Individual'}
                  </Typography>
                  {subj.deliveryType === 'BRIGADE' ? (
                    <Button size="small" variant="outlined" onClick={() => setSelectedBrigadeSubjectId(subj.id)}>
                      Configure
                    </Button>
                  ) : null}
                </Box>
              ))}
              {subjects.length === 0 ? (
                <Typography color="text.secondary">No subjects.</Typography>
              ) : null}
            </Stack>
          </Card>
        </Stack>
      ) : null}

      {tab === 2 ? (
        <Stack spacing={2}>
          <Card sx={{ p: 2.5, borderRadius: 3 }}>
            <Typography fontWeight={700} sx={{ mb: 1.5 }} variant="h6">
              Create Brigades
            </Typography>
            {selectedBrigadeSubject ? (
              <Typography color="text.secondary" sx={{ mb: 2 }}>
                Subject: <b>{selectedBrigadeSubject.name}</b>
              </Typography>
            ) : (
              <Typography color="text.secondary">
                Select a subject to configure brigades.
              </Typography>
            )}

            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <Card sx={{ flex: 1, p: 2.5, borderRadius: 3 }}>
                <Typography fontWeight={700} sx={{ mb: 1 }} variant="subtitle1">
                  New Brigade
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
                  Add Brigade
                </Button>
              </Card>

              <Card sx={{ flex: 1, p: 2.5, borderRadius: 3 }}>
                <Typography fontWeight={700} sx={{ mb: 1 }} variant="subtitle1">
                  Brigades
                </Typography>
                <Stack spacing={1}>
                  {groups.length === 0 ? <Typography color="text.secondary">No brigades yet.</Typography> : null}
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
                        Delete
                      </Button>
                    </Box>
                  ))}
                </Stack>

                <Box sx={{ mt: 2 }}>
                  <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                    Assigned: {assignedStudentIds.size} / {students.length}
                  </Typography>
                  <Button variant="contained" onClick={saveBrigades} disabled={!selectedBrigadeSubjectId}>
                    Save
                  </Button>
                  {error && (
                    <Typography color="error" sx={{ mt: 1 }}>
                      {error}
                    </Typography>
                  )}
                </Box>
              </Card>
            </Stack>
          </Card>
        </Stack>
      ) : null}

      {tab === 3 ? (
        <Stack spacing={2}>
          <Card sx={{ p: 2.5, borderRadius: 3 }}>
            <Typography fontWeight={700} sx={{ mb: 1.5 }} variant="h6">
              Admin Management
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

      <Dialog open={editDialog.open} onClose={() => setEditDialog({ ...editDialog, open: false })} maxWidth="sm" fullWidth>
        <DialogTitle>Edit Student</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Name"
              value={editDialog.fio}
              onChange={(e) => setEditDialog({ ...editDialog, fio: e.target.value })}
              fullWidth
            />
            <TextField
              label="Telegram"
              value={editDialog.telegramTag}
              onChange={(e) => setEditDialog({ ...editDialog, telegramTag: e.target.value })}
              fullWidth
              placeholder="username"
            />
            <FormControl fullWidth>
              <InputLabel>Subgroup</InputLabel>
              <Select
                value={editDialog.subgroup}
                label="Subgroup"
                onChange={(e) => setEditDialog({ ...editDialog, subgroup: Number(e.target.value) })}
              >
                <MenuItem value={1}>1</MenuItem>
                <MenuItem value={2}>2</MenuItem>
              </Select>
            </FormControl>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditDialog({ ...editDialog, open: false })}>Cancel</Button>
          <Button variant="contained" onClick={handleSaveEdit}>Save</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)}>
        <DialogTitle>Confirm Delete</DialogTitle>
        <DialogContent>
          <Typography>Delete this student?</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" color="error" onClick={handleDeleteStudent}>Delete</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
