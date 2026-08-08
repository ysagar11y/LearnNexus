import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';

/**
 * Light / dark / follow-the-OS.
 *
 * The design system's tokens key off `data-theme` on the root element, with the
 * attribute absent meaning "follow `prefers-color-scheme`". This provider only
 * ever sets or removes that attribute — it never writes colours itself.
 */

export type ThemeChoice = 'light' | 'dark' | 'system';

const STORAGE_KEY = 'ln.theme';

interface ThemeContextValue {
  choice: ThemeChoice;
  /** What is actually on screen once "system" has been resolved. */
  resolved: 'light' | 'dark';
  setChoice: (choice: ThemeChoice) => void;
  toggle: () => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

function systemPrefersDark(): boolean {
  return window.matchMedia('(prefers-color-scheme: dark)').matches;
}

function readStored(): ThemeChoice {
  const stored = localStorage.getItem(STORAGE_KEY);
  return stored === 'light' || stored === 'dark' ? stored : 'system';
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [choice, setChoiceState] = useState<ThemeChoice>(readStored);
  const [systemDark, setSystemDark] = useState(systemPrefersDark);

  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    const listener = (event: MediaQueryListEvent) => setSystemDark(event.matches);
    media.addEventListener('change', listener);
    return () => media.removeEventListener('change', listener);
  }, []);

  useEffect(() => {
    const root = document.documentElement;
    if (choice === 'system') {
      root.removeAttribute('data-theme');
      localStorage.removeItem(STORAGE_KEY);
    } else {
      root.setAttribute('data-theme', choice);
      localStorage.setItem(STORAGE_KEY, choice);
    }
  }, [choice]);

  const resolved = choice === 'system' ? (systemDark ? 'dark' : 'light') : choice;

  const setChoice = useCallback((next: ThemeChoice) => setChoiceState(next), []);
  const toggle = useCallback(
    () => setChoiceState(resolved === 'dark' ? 'light' : 'dark'),
    [resolved],
  );

  const value = useMemo(
    () => ({ choice, resolved, setChoice, toggle }),
    [choice, resolved, setChoice, toggle],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useTheme must be used inside ThemeProvider');
  return context;
}
