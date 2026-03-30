import React, { useEffect, useState } from 'react';
import {
  Box,
  Button,
  Card,
  Checkbox,
  CircularProgress,
  FormControlLabel,
  Stack,
  TextField,
  Typography,
} from '@mui/material';

import { api } from '../api/client';
import { useNavigate } from 'react-router-dom';

type Step = 'TAG_ONLY' | 'SET_PASSWORD' | 'PASSWORD_REQUIRED' | 'BOOTSTRAP_KEY' | 'BOOTSTRAP_REGISTER';

export function LoginPage({ onAuthed }: { onAuthed: (me: any) => void }) {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [hasSuperAdmin, setHasSuperAdmin] = useState<boolean | null>(null);
  const [step, setStep] = useState<Step>('TAG_ONLY');

  const [telegramTag, setTelegramTag] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [superKey, setSuperKey] = useState('');
  const [rememberDevice, setRememberDevice] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    api
      .getBootstrapStatus()
      .then((res) => {
        if (!alive) return;
        setHasSuperAdmin(res.hasSuperAdmin);
        if (!res.hasSuperAdmin) setStep('BOOTSTRAP_KEY');
      })
      .catch(() => {
        if (!alive) return;
        setHasSuperAdmin(true);
      })
      .finally(() => {
        if (!alive) return;
        setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  async function handleLoginTagOnly() {
    setError(null);
    try {
      await api.login({ telegramTag, password: null, rememberDevice });
      const me = await api.getMe();
      onAuthed(me);
      navigate('/');
    } catch (e: any) {
      const msg = String(e?.message || e);
      if (msg.includes('SET_PASSWORD_REQUIRED')) setStep('SET_PASSWORD');
      else if (msg.includes('PASSWORD_REQUIRED')) setStep('PASSWORD_REQUIRED');
      else if (msg.includes('NOT_REGISTERED')) setError('Тег не зарегистрирован. Обратитесь к администратору.');
      else if (msg.includes('UNAUTHORIZED')) setError('Ошибка входа.');
      else setError(msg);
    }
  }

  async function handleSetPassword() {
    setError(null);
    if (password.length < 6) {
      setError('Пароль слишком короткий (минимум 6 символов).');
      return;
    }
    if (password !== confirmPassword) {
      setError('Пароли не совпадают.');
      return;
    }
    try {
      await api.setPassword({ telegramTag, newPassword: password, rememberDevice });
      const me = await api.getMe();
      onAuthed(me);
      navigate('/');
    } catch (e: any) {
      setError(String(e?.message || e));
    }
  }

  async function handlePasswordRequiredLogin() {
    setError(null);
    try {
      await api.login({ telegramTag, password, rememberDevice });
      const me = await api.getMe();
      onAuthed(me);
      navigate('/');
    } catch (e: any) {
      setError(String(e?.message || e));
    }
  }

  async function handleVerifySuperKey() {
    setError(null);
    try {
      const res = await api.verifySuperKey(superKey);
      if (!res.ok) {
        setError('Неверный ключ супер-админа.');
        return;
      }
      setStep('BOOTSTRAP_REGISTER');
    } catch (e: any) {
      setError(String(e?.message || e));
    }
  }

  async function handleRegisterSuperAdmin() {
    setError(null);
    try {
      await api.registerSuperAdmin({ telegramTag, password, rememberDevice, superKey });
      const me = await api.getMe();
      onAuthed(me);
      navigate('/');
    } catch (e: any) {
      setError(String(e?.message || e));
    }
  }

  if (loading || hasSuperAdmin === null) {
    return (
      <Box display="flex" minHeight="60vh" justifyContent="center" alignItems="center">
        <CircularProgress />
      </Box>
    );
  }

  const showTagInput = step !== 'BOOTSTRAP_KEY';

  return (
    <Box display="flex" justifyContent="center" padding={3} bgcolor="#f5faff" minHeight="100vh">
      <Card sx={{ width: 420, padding: 3, borderRadius: 3, border: '1px solid #d6e6ff' }}>
        <Typography variant="h5" fontWeight={700} color="primary.main" gutterBottom>
          QueueBot
        </Typography>

        {step === 'BOOTSTRAP_KEY' ? (
          <>
            <Typography sx={{ mb: 2 }} color="text.secondary">
              Суперадминов ещё нет в системе. Укажите ключ для регистрации:
            </Typography>
            <Stack spacing={2}>
              <TextField
                label="Ключ супер-админа"
                value={superKey}
                onChange={(e) => setSuperKey(e.target.value)}
                fullWidth
              />
              <Button variant="contained" onClick={handleVerifySuperKey}>
                Продолжить
              </Button>
              {error && (
                <Typography color="error" variant="body2">
                  {error}
                </Typography>
              )}
            </Stack>
          </>
        ) : (
          <>
            <Stack spacing={2}>
              <TextField
                label="telegram_tag"
                value={telegramTag}
                onChange={(e) => setTelegramTag(e.target.value)}
                disabled={step === 'SET_PASSWORD' || step === 'PASSWORD_REQUIRED'}
                fullWidth
              />

              <FormControlLabel
                control={<Checkbox checked={rememberDevice} onChange={(e) => setRememberDevice(e.target.checked)} />}
                label="Запомнить устройство"
              />

              {step === 'TAG_ONLY' ? (
                <Button variant="contained" onClick={handleLoginTagOnly}>
                  Войти
                </Button>
              ) : null}

              {step === 'SET_PASSWORD' ? (
                <>
                  <TextField label="Пароль" type="password" value={password} onChange={(e) => setPassword(e.target.value)} fullWidth />
                  <TextField
                    label="Повторите пароль"
                    type="password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    fullWidth
                  />
                  <Button variant="contained" onClick={handleSetPassword}>
                    Сохранить пароль
                  </Button>
                </>
              ) : null}

              {step === 'PASSWORD_REQUIRED' ? (
                <>
                  <TextField label="Пароль" type="password" value={password} onChange={(e) => setPassword(e.target.value)} fullWidth />
                  <Button variant="contained" onClick={handlePasswordRequiredLogin}>
                    Войти
                  </Button>
                </>
              ) : null}

              {step === 'BOOTSTRAP_REGISTER' ? (
                <>
                  <TextField label="Пароль" type="password" value={password} onChange={(e) => setPassword(e.target.value)} fullWidth />
                  <Button variant="contained" onClick={handleRegisterSuperAdmin}>
                    Зарегистрировать суперадмина
                  </Button>
                  <Typography variant="body2" color="text.secondary">
                    После регистрации выполните /start в боте (чтобы бот мог отправлять уведомления).
                  </Typography>
                </>
              ) : null}

              {error && (
                <Typography color="error" variant="body2">
                  {error}
                </Typography>
              )}

              {hasSuperAdmin && step !== 'TAG_ONLY' && step !== 'SET_PASSWORD' && step !== 'PASSWORD_REQUIRED' ? null : null}
            </Stack>
          </>
        )}
      </Card>
    </Box>
  );
}

