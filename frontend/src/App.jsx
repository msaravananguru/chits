import { HashRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth.jsx';
import Layout from './components/Layout.jsx';
import SignIn from './pages/SignIn.jsx';
import Members from './pages/Members.jsx';
import MonthlySheet from './pages/MonthlySheet.jsx';

function RequireAuth({ children }) {
  const { user, ready } = useAuth();
  if (!ready) return <div className="splash">Loading…</div>;
  if (!user) return <Navigate to="/signin" replace />;
  return children;
}

export default function App() {
  return (
    <HashRouter>
      <AuthProvider>
        <Routes>
          <Route path="/signin" element={<SignIn />} />
          <Route element={<RequireAuth><Layout /></RequireAuth>}>
            <Route path="/" element={<Navigate to="/members" replace />} />
            <Route path="/members" element={<Members />} />
            <Route path="/sheet" element={<MonthlySheet />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </HashRouter>
  );
}
