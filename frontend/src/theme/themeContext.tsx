import React, { createContext, useContext, useState, useEffect, useMemo } from 'react';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { CssBaseline } from '@mui/material';

interface ThemeContextType {
  theme: ReturnType<typeof createTheme>;
  toggleTheme: () => void;
  isDarkMode: boolean;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export const useTheme = () => {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return context;
};

export const ThemeProviderWrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [isDarkMode, setIsDarkMode] = useState(() => {
    const saved = localStorage.getItem('theme');
    return saved ? saved === 'dark' : false;
  });

  useEffect(() => {
    localStorage.setItem('theme', isDarkMode ? 'dark' : 'light');
  }, [isDarkMode]);

  const theme = useMemo(() => {
    const baseTheme = createTheme({
      palette: {
        mode: isDarkMode ? 'dark' : 'light',
        primary: {
          main: isDarkMode ? '#90caf9' : '#1976d2',
        },
        secondary: {
          main: isDarkMode ? '#bbdefb' : '#1565c0',
        },
        background: {
          default: isDarkMode ? '#121212' : '#f5faff',
          paper: isDarkMode ? '#1e1e1e' : '#ffffff',
        },
        text: {
          primary: isDarkMode ? '#ffffff' : '#000000',
          secondary: isDarkMode ? '#b0b0b0' : '#666666',
        },
      },
      typography: {
        fontFamily: '"Google Sans", "Roboto", "Arial", sans-serif',
        button: {
          textTransform: 'none',
          fontWeight: 500,
        },
      },
      shape: {
        borderRadius: 12,
      },
      components: {
        MuiButton: {
          styleOverrides: {
            root: {
              borderRadius: 20,
              padding: '8px 24px',
              fontSize: '0.875rem',
            },
          },
        },
        MuiTextField: {
          styleOverrides: {
            root: {
              '& .MuiOutlinedInput-root': {
                borderRadius: 20,
              },
            },
          },
        },
        MuiSelect: {
          styleOverrides: {
            root: {
              borderRadius: 20,
            },
          },
        },
        MuiCard: {
          styleOverrides: {
            root: {
              borderRadius: 16,
              boxShadow: isDarkMode
                ? '0px 4px 6px rgba(0, 0, 0, 0.3)'
                : '0px 2px 4px rgba(0, 0, 0, 0.1)',
            },
          },
        },
        MuiChip: {
          styleOverrides: {
            root: {
              borderRadius: 16,
              fontWeight: 500,
            },
          },
        },
        MuiDialog: {
          styleOverrides: {
            paper: {
              borderRadius: 16,
            },
          },
        },
      },
    });

    return baseTheme;
  }, [isDarkMode]);

  const toggleTheme = () => {
    setIsDarkMode(!isDarkMode);
  };

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme, isDarkMode }}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ThemeContext.Provider>
  );
}
