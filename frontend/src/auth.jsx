import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { clearUserId, getUserId, post, setUserId } from './api.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);

  // restore the session on page load
  useEffect(() => {
    (async () => {
      if (getUserId()) {
        try { setUser(await post('/auth/me')); } catch { clearUserId(); }
      }
      setReady(true);
    })();
  }, []);

  // any 401 from the server signs the user out
  useEffect(() => {
    const onUnauthorized = () => { clearUserId(); setUser(null); };
    window.addEventListener('chits:unauthorized', onUnauthorized);
    return () => window.removeEventListener('chits:unauthorized', onUnauthorized);
  }, []);

  const signIn = useCallback(async (id) => {
    setUserId(id);
    try {
      setUser(await post('/auth/me'));
    } catch (e) {
      clearUserId();
      throw e;
    }
  }, []);

  const signOut = useCallback(() => { clearUserId(); setUser(null); }, []);

  const value = useMemo(() => ({ user, ready, signIn, signOut }), [user, ready, signIn, signOut]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
