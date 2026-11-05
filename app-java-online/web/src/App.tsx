import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireSession, SessionProvider } from './session/SessionContext';
import { ADMIN_PATHS, PUBLIC_PATHS, featureRoutes } from './routes';
import { PROGRAM_PATHS } from './programs';

export function AppRoutes() {
  return (
    <Routes>
      {featureRoutes.map((r) => {
        const path = r.path ?? '';
        const element = PUBLIC_PATHS.includes(path) ? (
          r.element
        ) : (
          <RequireSession admin={ADMIN_PATHS.includes(path)}>{r.element}</RequireSession>
        );
        return <Route key={path} path={path} element={element} />;
      })}
      <Route path="*" element={<Navigate to={PROGRAM_PATHS.COSGN00C} replace />} />
    </Routes>
  );
}

export function App() {
  return (
    <SessionProvider>
      <AppRoutes />
    </SessionProvider>
  );
}
