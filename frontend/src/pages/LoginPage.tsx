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
  IconButton,
} from '@mui/material';
import { Brightness4, Brightness7 } from '@mui/icons-material';
import { api } from '../api/client';
import { useNavigate } from 'react-router-dom';
import { useTheme } from '../theme/themeContext';

type Step = 'LOGIN_TAG' | 'LOGIN_FIO' | 'LOGIN_TOKEN' | 'SET_PASSWORD_TAG' | 'SET_PASSWORD_FIO' | 'PASSWORD_REQUIRED_TAG' | 'PASSWORD_REQUIRED_FIO' | 'BOOTSTRAP_KEY' | 'BOOTSTRAP_REGISTER' | 'TELEGRAM_TOKEN';
type LoginMethod = 'telegram' | 'fio' | 'token';

export function LoginPage({ onAuthed }: { onAuthed: (me: any) => void }) {
  const { toggleTheme, isDarkMode } = useTheme();
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
  const [telegramToken, setTelegramToken] = useState('');

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
      else if (msg.includes('NOT_REGISTERED')) setError('Tag not registered. Contact admin.');
      else if (msg.includes('UNAUTHORIZED')) setError('Login error.');
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
      else if (msg.includes('NOT_REGISTERED')) setError('User not found. Contact admin.');
      else if (msg.includes('UNAUTHORIZED')) setError('Login error.');
      else setError(msg);
    }
  }

  async function handleLoginWithToken() {
    setError(null);
    try {
      await api.loginWithTelegramToken(telegramToken);
      const me = await api.getMe();
      onAuthed(me);
      navigate('/');
    } catch (e: any) {
      const msg = String(e?.message || e);
      if (msg.includes('TOKEN_EXPIRED')) setError('Token expired. Please request a new one.');
      else if (msg.includes('INVALID_TOKEN')) setError('Invalid token.');
      else if (msg.includes('STUDENT_NOT_FOUND')) setError('Student not found.');
      else setError(msg);
    }
  }

  async function handleSetPasswordTag() {
    setError(null);
    if (password.length < 6) {
      setError('Password too short (min 6 chars).');
      return;
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match.');
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
      setError('Password too short (min 6 chars).');
      return;
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match.');
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
        setError('Invalid super key.');
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
        setError('Enter telegram tag');
        return;
      }
      payload.telegramTag = telegramTag;
    } else {
      if (!fio || fio.trim() === '') {
        setError('Enter name');
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

  if (step === 'BOOTSTRAP_KEY' || step === 'BOOTSTRAP_REGISTER') {
    return (
      <Box display="flex" justifyContent="center" padding={3} bgcolor="background.default" minHeight="100vh" position="relative">
        <IconButton
          onClick={toggleTheme}
          size="large"
          sx={{
            position: 'fixed',
            top: 16,
            right: 16,
            zIndex: 1000,
            bgcolor: 'background.paper',
            boxShadow: 3,
            '&:hover': { bgcolor: 'action.hover' }
          }}
        >
          {isDarkMode ? <Brightness7 /> : <Brightness4 />}
        </IconButton>

        <Card sx={{ width: 420, padding: 3, borderRadius: 3 }}>
          <Typography variant="h4" fontWeight={700} color="primary.main" gutterBottom>
            QueueBot
          </Typography>

          {step === 'BOOTSTRAP_KEY' ? (
            <>
              <Typography sx={{ mb: 2 }} color="text.secondary">
                No super admins yet. Enter super key:
              </Typography>
              <Stack spacing={2}>
                <TextField
                  label="Super Key"
                  value={superKey}
                  onChange={(e) => setSuperKey(e.target.value)}
                  fullWidth
                />
                <Button variant="contained" onClick={handleVerifySuperKey}>
                  Continue
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
                Register first super admin:
              </Typography>

              <Tabs value={bootstrapMethod} onChange={(_, v) => setBootstrapMethod(v)} sx={{ mb: 2 }}>
                <Tab value="telegram" label="Telegram" />
                <Tab value="fio" label="Name" />
              </Tabs>

              <Stack spacing={2}>
                {bootstrapMethod === 'telegram' ? (
                  <TextField
                    label="Telegram"
                    value={telegramTag}
                    onChange={(e) => setTelegramTag(e.target.value)}
                    fullWidth
                    placeholder="username"
                  />
                ) : (
                  <>
                    <TextField
                      label="Name"
                      value={fio}
                      onChange={(e) => setFio(e.target.value)}
                      fullWidth
                      placeholder="Ivanov Ivan"
                    />
                    <FormControl fullWidth>
                      <InputLabel>Subgroup</InputLabel>
                      <Select
                        value={subgroup}
                        label="Subgroup"
                        onChange={(e) => setSubgroup(e.target.value as number)}
                      >
                        <MenuItem value={1}>1</MenuItem>
                        <MenuItem value={2}>2</MenuItem>
                      </Select>
                    </FormControl>
                  </>
                )}

                <TextField label="Password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} fullWidth />
                <Button variant="contained" onClick={handleRegisterSuperAdmin}>
                  Register
                </Button>
                <Typography variant="body2" color="text.secondary">
                  After registration, execute /start in the bot.
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

  return (
    <Box display="flex" justifyContent="center" padding={3} bgcolor="background.default" minHeight="100vh" position="relative">
      <IconButton
        onClick={toggleTheme}
        size="large"
        sx={{
          position: 'fixed',
          top: 16,
          right: 16,
          zIndex: 1000,
          bgcolor: 'background.paper',
          boxShadow: 3,
          '&:hover': { bgcolor: 'action.hover' }
        }}
      >
        {isDarkMode ? <Brightness7 /> : <Brightness4 />}
      </IconButton>

      <Card sx={{ width: 420, padding: 3, borderRadius: 3 }}>
        <Typography variant="h4" fontWeight={700} color="primary.main" gutterBottom>
          QueueBot
        </Typography>

        <Tabs value={loginMethod} onChange={(_, v) => {
          setLoginMethod(v);
          // Reset step and fields when switching methods
          if (v === 'token') {
            setStep('LOGIN_TOKEN');
            setTelegramToken('');
            setTelegramTag('');
            setFio('');
            setPassword('');
            setConfirmPassword('');
          } else if (v === 'telegram') {
            setStep('LOGIN_TAG');
            setTelegramTag('');
            setTelegramToken('');
            setPassword('');
            setConfirmPassword('');
          } else if (v === 'fio') {
            setStep('LOGIN_FIO');
            setFio('');
            setTelegramTag('');
            setTelegramToken('');
            setPassword('');
            setConfirmPassword('');
          }
        }} sx={{ mb: 2 }}>
          <Tab value="telegram" label="Telegram" />
          <Tab value="token" label="Token" />
          <Tab value="fio" label="Name" />
        </Tabs>

        <Stack spacing={2}>
          {loginMethod === 'telegram' ? (
            <>
              <TextField
                label="Telegram"
                value={telegramTag}
                onChange={(e) => setTelegramTag(e.target.value)}
                disabled={step === 'SET_PASSWORD_TAG' || step === 'PASSWORD_REQUIRED_TAG'}
                fullWidth
                placeholder="username"
              />
              {step === 'LOGIN_TAG' && (
                <Button variant="contained" onClick={handleLoginTagOnly} disabled={!telegramTag.trim()}>
                  Sign In
                </Button>
              )}
            </>
          ) : loginMethod === 'token' ? (
            <>
              <TextField
                label="Telegram Token"
                value={telegramToken}
                onChange={(e) => setTelegramToken(e.target.value)}
                fullWidth
                placeholder="Enter token from bot (/auth)"
                helperText="Get token by sending /auth to the bot"
              />
              <Button variant="contained" onClick={handleLoginWithToken} disabled={!telegramToken.trim()}>
                Sign In with Token
              </Button>
            </>
          ) : (
            <>
              <TextField
                label="Name"
                value={fio}
                onChange={(e) => setFio(e.target.value)}
                disabled={step === 'SET_PASSWORD_FIO' || step === 'PASSWORD_REQUIRED_FIO'}
                fullWidth
                placeholder="Ivanov Ivan"
              />
              <FormControl fullWidth>
                <InputLabel>Subgroup</InputLabel>
                <Select
                  value={subgroup}
                  label="Subgroup"
                  onChange={(e) => setSubgroup(e.target.value as number)}
                  disabled={step === 'SET_PASSWORD_FIO' || step === 'PASSWORD_REQUIRED_FIO'}
                >
                  <MenuItem value={1}>1</MenuItem>
                  <MenuItem value={2}>2</MenuItem>
                </Select>
              </FormControl>
              {step === 'LOGIN_FIO' && (
                <Button variant="contained" onClick={handleLoginFioOnly} disabled={!fio.trim()}>
                  Sign In
                </Button>
              )}
            </>
          )}

          {/* Password fields - show only when needed */}
          {(step === 'SET_PASSWORD_TAG' && loginMethod === 'telegram') || (step === 'SET_PASSWORD_FIO' && loginMethod === 'fio') ? (
            <>
              <TextField label="Password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} fullWidth />
              <TextField
                label="Confirm Password"
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                fullWidth
              />
              <Button variant="contained" onClick={step === 'SET_PASSWORD_TAG' ? handleSetPasswordTag : handleSetPasswordFio}>
                Save Password
              </Button>
            </>
          ) : null}

          {(step === 'PASSWORD_REQUIRED_TAG' && loginMethod === 'telegram') || (step === 'PASSWORD_REQUIRED_FIO' && loginMethod === 'fio') ? (
            <>
              <TextField label="Password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} fullWidth />
              <Button variant="contained" onClick={step === 'PASSWORD_REQUIRED_TAG' ? handlePasswordRequiredLoginTag : handlePasswordRequiredLoginFio}>
                Sign In
              </Button>
            </>
          ) : null}

          <FormControlLabel
            control={<Checkbox checked={rememberDevice} onChange={(e) => setRememberDevice(e.target.checked)} />}
            label="Remember device"
          />

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
