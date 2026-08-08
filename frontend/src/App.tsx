import { Suspense, lazy } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';

import { AppShell } from './components/AppShell';
import { FullPageSpinner, WorkspaceNotFound } from './components/states';
import { useAuth } from './lib/auth';
import { useTenant } from './lib/tenant';
import type { RoleCode } from './lib/types';

// Auth screens are needed on first paint; everything behind the shell is split
// so a learner never downloads the admin console.
import { SignIn } from './routes/auth/SignIn';
import { AcceptInvite } from './routes/auth/AcceptInvite';
import { ForgotPassword } from './routes/auth/ForgotPassword';
import { ResetPassword } from './routes/auth/ResetPassword';
import { Landing } from './routes/Landing';
import { VerifyCertificate } from './routes/VerifyCertificate';

const Dashboard = lazy(() => import('./routes/learner/Dashboard'));
const MyLearning = lazy(() => import('./routes/learner/MyLearning'));
const Catalog = lazy(() => import('./routes/learner/Catalog'));
const Certificates = lazy(() => import('./routes/learner/Certificates'));
const Profile = lazy(() => import('./routes/learner/Profile'));
const Player = lazy(() => import('./routes/learner/Player'));
const Attempt = lazy(() => import('./routes/learner/Attempt'));

const AdminOverview = lazy(() => import('./routes/admin/Overview'));
const AdminCourses = lazy(() => import('./routes/admin/Courses'));
const CourseEditor = lazy(() => import('./routes/admin/CourseEditor'));
const AdminPeople = lazy(() => import('./routes/admin/People'));
const AdminEnrollments = lazy(() => import('./routes/admin/Enrollments'));
const AdminReports = lazy(() => import('./routes/admin/Reports'));
const AdminBranding = lazy(() => import('./routes/admin/Branding'));
const AdminSettings = lazy(() => import('./routes/admin/Settings'));
const AdminAudit = lazy(() => import('./routes/admin/Audit'));
const AdminGrading = lazy(() => import('./routes/admin/Grading'));

const PlatformOverview = lazy(() => import('./routes/platform/Overview'));
const PlatformTenants = lazy(() => import('./routes/platform/Tenants'));

export function App() {
  const { user, initialising } = useAuth();
  const { loading: tenantLoading, unknown, slug } = useTenant();

  if (initialising || tenantLoading) {
    return <FullPageSpinner />;
  }

  // A host that resolves to no workspace is a dead end for everything except
  // the marketing page and public certificate verification.
  if (unknown && slug) {
    return (
      <Routes>
        <Route path="/verify/:code" element={<VerifyCertificate />} />
        <Route path="*" element={<WorkspaceNotFound slug={slug} />} />
      </Routes>
    );
  }

  return (
    <Routes>
      {/* ---- public ---- */}
      <Route path="/verify/:code" element={<VerifyCertificate />} />
      <Route path="/welcome" element={<Landing />} />
      <Route path="/sign-in" element={user ? <Navigate to="/" replace /> : <SignIn />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route path="/accept-invite" element={<AcceptInvite />} />

      {/* ---- the player runs full-bleed, outside the shell ---- */}
      <Route
        path="/learn/:courseId"
        element={
          <RequireAuth>
            <Suspense fallback={<FullPageSpinner />}>
              <Player />
            </Suspense>
          </RequireAuth>
        }
      />
      <Route
        path="/assessments/:assessmentId/attempt"
        element={
          <RequireAuth>
            <Suspense fallback={<FullPageSpinner />}>
              <Attempt />
            </Suspense>
          </RequireAuth>
        }
      />

      {/* ---- everything else sits inside the shell ---- */}
      <Route
        path="/*"
        element={
          <RequireAuth>
            <AppShell>
              <Suspense fallback={<FullPageSpinner inline />}>
                <Routes>
                  <Route index element={<Dashboard />} />
                  <Route path="my-learning" element={<MyLearning />} />
                  <Route path="catalog" element={<Catalog />} />
                  <Route path="certificates" element={<Certificates />} />
                  <Route path="profile" element={<Profile />} />

                  <Route
                    path="admin"
                    element={
                      <RequireRole roles={['TENANT_ADMIN', 'PLATFORM_ADMIN']}>
                        <AdminOverview />
                      </RequireRole>
                    }
                  />
                  <Route
                    path="admin/courses"
                    element={
                      <RequireRole roles={['TENANT_ADMIN', 'PLATFORM_ADMIN', 'AUTHOR', 'INSTRUCTOR']}>
                        <AdminCourses />
                      </RequireRole>
                    }
                  />
                  <Route
                    path="admin/courses/:courseId"
                    element={
                      <RequireRole roles={['TENANT_ADMIN', 'PLATFORM_ADMIN', 'AUTHOR', 'INSTRUCTOR']}>
                        <CourseEditor />
                      </RequireRole>
                    }
                  />
                  <Route
                    path="admin/grading"
                    element={
                      <RequireRole roles={['TENANT_ADMIN', 'PLATFORM_ADMIN', 'INSTRUCTOR']}>
                        <AdminGrading />
                      </RequireRole>
                    }
                  />
                  <Route
                    path="admin/enrollments"
                    element={
                      <RequireRole roles={['TENANT_ADMIN', 'PLATFORM_ADMIN', 'INSTRUCTOR', 'MANAGER']}>
                        <AdminEnrollments />
                      </RequireRole>
                    }
                  />
                  <Route
                    path="admin/reports"
                    element={
                      <RequireRole roles={['TENANT_ADMIN', 'PLATFORM_ADMIN', 'INSTRUCTOR', 'MANAGER']}>
                        <AdminReports />
                      </RequireRole>
                    }
                  />
                  <Route
                    path="admin/people"
                    element={
                      <RequireRole roles={['TENANT_ADMIN', 'PLATFORM_ADMIN']}>
                        <AdminPeople />
                      </RequireRole>
                    }
                  />
                  <Route
                    path="admin/branding"
                    element={
                      <RequireRole roles={['TENANT_ADMIN', 'PLATFORM_ADMIN']}>
                        <AdminBranding />
                      </RequireRole>
                    }
                  />
                  <Route
                    path="admin/settings"
                    element={
                      <RequireRole roles={['TENANT_ADMIN', 'PLATFORM_ADMIN']}>
                        <AdminSettings />
                      </RequireRole>
                    }
                  />
                  <Route
                    path="admin/audit"
                    element={
                      <RequireRole roles={['TENANT_ADMIN', 'PLATFORM_ADMIN']}>
                        <AdminAudit />
                      </RequireRole>
                    }
                  />

                  <Route
                    path="platform"
                    element={
                      <RequireRole roles={['PLATFORM_ADMIN']}>
                        <PlatformOverview />
                      </RequireRole>
                    }
                  />
                  <Route
                    path="platform/tenants"
                    element={
                      <RequireRole roles={['PLATFORM_ADMIN']}>
                        <PlatformTenants />
                      </RequireRole>
                    }
                  />

                  <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
              </Suspense>
            </AppShell>
          </RequireAuth>
        }
      />
    </Routes>
  );
}

function RequireAuth({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const location = useLocation();

  if (!user) {
    // Remember where they were headed so sign-in can return them there.
    return <Navigate to="/sign-in" replace state={{ from: location.pathname + location.search }} />;
  }
  return <>{children}</>;
}

function RequireRole({ roles, children }: { roles: RoleCode[]; children: React.ReactNode }) {
  const { hasRole } = useAuth();
  if (!hasRole(...roles)) {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}
