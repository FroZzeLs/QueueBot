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
  Tabs,
  Tab,
  InputLabel,
  FormControl,
  Select,
  MenuItem,
} from '@mui/material';

import { api } from '../api/client';
import { useNavigate } from 'react-router-dom';

type Step = 'LOGIN_TAG' | 'LOGIN_FIO' | 'SET_PASSWORD_TAG' | 'SET_PASSWORD_FIO' | 'PASSWORD_REQUIRED_TAG' | 'PASSWORD_REQUIRED_FIO' | 'BOOTSTRAP_KEY' | 'BOOTSTRAP_REGISTER';
type LoginMethod = 'telegram' | 'fio';

export function LoginPage({ onAuthed }: { onAuthed: (me: any) => void }) {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [hasSuperAdmin, setHasSuperAdmin] = useState<boolean | null>(null);
  const [step, setStep] = useState<Step>('LOGIN_TAG');
  const [loginMethod, setLoginMethod] = useState<LoginMethod>('telegram');

  const [telegramTag, setTelegramTag] = useState('');
  const [fio, setFio] = useState('');
  const [subgroup, setSubgroup] = useState<number>(1);
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [superKey, setSuperKey] = useState('');
  const [rememberDevice, setRememberDevice] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [bootstrapMethod, setBootstrapMethod] = useState<LoginMethod>('telegram');

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
      if (msg.includes('SET_PASSWORD_REQUIRED')) setStep('SET_PASSWORD_TAG');
      else if (msg.includes('PASSWORD_REQUIRED')) setStep('PASSWORD_REQUIRED_TAG');
      else if (msg.includes('NOT_REGISTERED')) setError('Тег не зарегистрирован. Обратитесь к администратору.');
      else if (msg.includes('UNAUTHORIZED')) setError('Ошибка входа.');
      else setError(msg);
    }
  }

  async function handleLoginFioOnly() {
    setError(null);
    try {
      await api.login({ fio, subgroup, password: null, rememberDevice });
      const me = await api.getMe();
      onAuthed(me);
      navigate('/');
    } catch (e: any) {
      const msg = String(e?.message || e);
      if (msg.includes('SET_PASSWORD_REQUIRED')) setStep('SET_PASSWORD_FIO');
      else if (msg.includes('PASSWORD_REQUIRED')) setStep('PASSWORD_REQUIRED_FIO');
      else if (msg.includes('NOT_REGISTERED')) setError('Пользователь не найден. Обратитесь к администратору.');
      else if (msg.includes('UNAUTHORIZED')) setError('Ошибка входа.');
      else setError(msg);
    }
  }

  async function handleSetPasswordTag() {
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

  async function handleSetPasswordFio() {
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
      await api.setPassword({ fio, subgroup, newPassword: password, rememberDevice });
      const me = await api.getMe();
      onAuthed(me);
      navigate('/');
    } catch (e: any) {
      setError(String(e?.message || e));
    }
  }

  async function handlePasswordRequiredLoginTag() {
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

  async function handlePasswordRequiredLoginFio() {
    setError(null);
    try {
      await api.login({ fio, subgroup, password, rememberDevice });
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
    const payload: any = { password, rememberDevice, superKey };
    
    if (bootstrapMethod === 'telegram') {
      if (!telegramTag || telegramTag.trim() === '') {
        setError('Введите telegram_tag');
        return;
      }
      payload.telegramTag = telegramTag;
    } else {
      if (!fio || fio.trim() === '') {
        setError('Введите ФИО');
        return;
      }
      payload.fio = fio;
      payload.subgroup = subgroup;
    }
    
    try {
      await api.registerSuperAdmin(payload);
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

  // Bootstrap режим - нет суперадминов
  if (step === 'BOOTSTRAP_KEY' || step === 'BOOTSTRAP_REGISTER') {
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
              <Typography sx={{ mb: 2 }} color="text.secondary">
                Регистрация первого суперадмина:
              </Typography>
              
              <Tabs value={bootstrapMethod} onChange={(_, v) => setBootstrapMethod(v)} sx={{ mb: 2 }}>
                <Tab value="telegram" label="Telegram тег" />
                <Tab value="fio" label="Имя и Фамилия" />
              </Tabs>

              <Stack spacing={2}>
                {bootstrapMethod === 'telegram' ? (
                  <TextField
                    label="telegram_tag"
                    value={telegramTag}
                    onChange={(e) => setTelegramTag(e.target.value)}
                    fullWidth
                    placeholder="username"
                  />
                ) : (
                  <>
                    <TextField
                      label="ФИО"
                      value={fio}
                      onChange={(e) => setFio(e.target.value)}
                      fullWidth
                      placeholder="Иванов Иван"
                    />
                    <FormControl fullWidth>
                      <InputLabel>Подгруппа</InputLabel>
                      <Select
                        value={subgroup}
                        label="Подгруппа"
                        onChange={(e) => setSubgroup(e.target.value as number)}
                      >
                        <MenuItem value={1}>1</MenuItem>
                        <MenuItem value={2}>2</MenuItem>
                      </Select>
                    </FormControl>
                  </>
                )}

                <TextField label="Пароль" type="password" value={password} onChange={(e) => setPassword(e.target.value)} fullWidth />
                <Button variant="contained" onClick={handleRegisterSuperAdmin}>
                  Зарегистрировать суперадмина
                </Button>
                <Typography variant="body2" color="text.secondary">
                  После регистрации выполните /start в боте (чтобы бот мог отправлять уведомления).
                </Typography>
                {error && (
                  <Typography color="error" variant="body2">
                    {error}
                  </Typography>
                )}
              </Stack>
            </>
          )}
        </Card>
      </Box>
    );
  }

  // Обычный режим входа
  return (
    <Box display="flex" justifyContent="center" padding={3} bgcolor="#f5faff" minHeight="100vh">
      <Card sx={{ width: 420, padding: 3, borderRadius: 3, border: '1px solid #d6e6ff' }}>
        <Typography variant="h5" fontWeight={700} color="primary.main" gutterBottom>
          QueueBot
        </Typography>

        <Tabs value={loginMethod} onChange={(_, v) => setLoginMethod(v)} sx={{ mb: 2 }}>
          <Tab value="telegram" label="Telegram тег" />
          <Tab value="fio" label="Имя и Фамилия" />
        </Tabs>

        <Stack spacing={2}>
          {loginMethod === 'telegram' ? (
            <>
              <TextField
                label="telegram_tag"
                value={telegramTag}
                onChange={(e) => setTelegramTag(e.target.value)}
                disabled={step === 'SET_PASSWORD_TAG' || step === 'PASSWORD_REQUIRED_TAG'}
                fullWidth
                placeholder="username"
              />
            </>
          ) : (
            <>
              <TextField
                label="ФИО"
                value={fio}
                onChange={(e) => setFio(e.target.value)}
                disabled={step === 'SET_PASSWORD_FIO' || step === 'PASSWORD_REQUIRED_FIO'}
                fullWidth
                placeholder="Иванов Иван"
              />
              <FormControl fullWidth>
                <InputLabel>Подгруппа</InputLabel>
                <Select
                  value={subgroup}
                  label="Подгруппа"
                  onChange={(e) => setSubgroup(e.target.value as number)}
                  disabled={step === 'SET_PASSWORD_FIO' || step === 'PASSWORD_REQUIRED_FIO'}
                >
                  <MenuItem value={1}>1</MenuItem>
                  <MenuItem value={2}>2</MenuItem>
                </Select>
              </FormControl>
            </>
          )}

          <FormControlLabel
            control={<Checkbox checked={rememberDevice} onChange={(e) => setRememberDevice(e.target.checked)} />}
            label="Запомнить устройство"
          />

          {step === 'LOGIN_TAG' && loginMethod === 'telegram' ? (
            <Button variant="contained" onClick={handleLoginTagOnly}>
              Войти
            </Button>
          ) : null}

          {step === 'LOGIN_FIO' && loginMethod === 'fio' ? (
            <Button variant="contained" onClick={handleLoginFioOnly}>
              Войти
            </Button>
          ) : null}

          {step === 'SET_PASSWORD_TAG' && loginMethod === 'telegram' ? (
            <>
              <TextField label="Пароль" type="password" value={password} onChange={(e) => setPassword(e.target.value)} fullWidth />
              <TextField
                label="Повторите пароль"
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                fullWidth
              />
              <Button variant="contained" onClick={handleSetPasswordTag}>
                Сохранить пароль
              </Button>
            </>
          ) : null}

          {step === 'SET_PASSWORD_FIO' && loginMethod === 'fio' ? (
            <>
              <TextField label="Пароль" type="password" value={password} onChange={(e) => setPassword(e.target.value)} fullWidth />
              <TextField
                label="Повторите пароль"
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                fullWidth
              />
              <Button variant="contained" onClick={handleSetPasswordFio}>
                Сохранить пароль
              </Button>
            </>
          ) : null}

          {step === 'PASSWORD_REQUIRED_TAG' && loginMethod === 'telegram' ? (
            <>
              <TextField label="Пароль" type="password" value={password} onChange={(e) => setPassword(e.target.value)} fullWidth />
              <Button variant="contained" onClick={handlePasswordRequiredLoginTag}>
                Войти
              </Button>
            </>
          ) : null}

          {step === 'PASSWORD_REQUIRED_FIO' && loginMethod === 'fio' ? (
            <>
              <TextField label="Пароль" type="password" value={password} onChange={(e) => setPassword(e.target.value)} fullWidth />
              <Button variant="contained" onClick={handlePasswordRequiredLoginFio}>
                Войти
              </Button>
            </>
          ) : null}

          {error && (
            <Typography color="error" variant="body2">
              {error}
            </Typography>
          )}
        </Stack>
      </Card>
    </Box>
  );
}
