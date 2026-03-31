import React, { useEffect, useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';

import { api, type ApiMe } from './api/client';
import { LoginPage } from './pages/LoginPage';
import { QueuePage } from './pages/QueuePage';
import { AdminPage } from './pages/AdminPage';
import { ThemeProviderWrapper } from './theme/themeContext';

export function App() {
  const [me, setMe] = useState<ApiMe | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    api
      .getMe()
      .then((res) => {
        if (!alive) return;
        setMe(res);
      })
      .catch(() => {
        if (!alive) return;
        setMe(null);
      })
      .finally(() => {
        if (!alive) return;
        setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  const isAuthed = !!me;

  return (
    <ThemeProviderWrapper>
      <BrowserRouter>
        {loading ? null : (
          <Routes>
            <Route path="/login" element={<LoginPage onAuthed={setMe} />} />
            <Route
              path="/admin"
              element={
                isAuthed ? (
                  me!.role === 'USER' ? (
                    <Navigate to="/" replace />
                  ) : (
                    <AdminPage me={me!} onMeChange={setMe} />
                  )
                ) : (
                  <Navigate to="/login" replace />
                )
              }
            />
            <Route
              path="/"
              element={
                isAuthed ? <QueuePage me={me!} onMeChange={setMe} /> : <Navigate to="/login" replace />
              }
            />
          </Routes>
        )}
      </BrowserRouter>
    </ThemeProviderWrapper>
  );
}
