/** Wire types mirroring the API's response records. */

export type RoleCode =
  | 'PLATFORM_ADMIN'
  | 'TENANT_ADMIN'
  | 'AUTHOR'
  | 'INSTRUCTOR'
  | 'MANAGER'
  | 'LEARNER';

export type CourseStatus = 'DRAFT' | 'IN_REVIEW' | 'PUBLISHED' | 'ARCHIVED';
export type CourseLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
export type DeliveryType =
  | 'SELF_PACED'
  | 'ILT_VIRTUAL'
  | 'ILT_CLASSROOM'
  | 'BLENDED'
  | 'LEARNING_PATH';
export type EnrollmentMode = 'MANUAL' | 'SELF' | 'INVITE';
export type EnrollmentStatus = 'ACTIVE' | 'COMPLETED' | 'EXPIRED' | 'WITHDRAWN' | 'WAITLISTED';
export type LessonContentType = 'VIDEO' | 'PDF' | 'HTML' | 'AUDIO' | 'SCORM' | 'LINK' | 'QUIZ';
export type ProgressStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED';
export type UserStatus = 'INVITED' | 'ACTIVE' | 'SUSPENDED';
export type QuestionType =
  | 'SINGLE_CHOICE'
  | 'MULTI_CHOICE'
  | 'TRUE_FALSE'
  | 'SHORT_ANSWER'
  | 'ESSAY';

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  hasNext: boolean;
}

export interface TenantPublic {
  slug: string;
  name: string;
  logoUrl?: string | null;
  logoDarkUrl?: string | null;
  faviconUrl?: string | null;
  brandHue: number;
  brandChroma: number;
  accentHue: number;
  defaultTheme: 'LIGHT' | 'DARK' | 'SYSTEM';
  loginHeadline?: string | null;
  loginSubtext?: string | null;
  supportEmail?: string | null;
  selfEnrollmentEnabled: boolean;
  publicCatalogEnabled: boolean;
}

export interface Profile {
  id: string;
  tenantId: string;
  tenantSlug: string;
  tenantName: string;
  email: string;
  firstName: string;
  lastName?: string | null;
  displayName: string;
  jobTitle?: string | null;
  avatarUrl?: string | null;
  roles: RoleCode[];
  primaryRole: RoleCode;
  orgUnitId?: string | null;
  orgUnitName?: string | null;
  locale?: string | null;
  timezone?: string | null;
  lastLoginAt?: string | null;
}

export interface Session {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: string;
  user: Profile;
}

export interface CourseSummary {
  id: string;
  code?: string | null;
  title: string;
  slug: string;
  summary?: string | null;
  thumbnailUrl?: string | null;
  level: CourseLevel;
  deliveryType: DeliveryType;
  status: CourseStatus;
  enrollmentMode: EnrollmentMode;
  categoryId?: string | null;
  categoryName?: string | null;
  categoryColor?: string | null;
  tags: string[];
  estimatedMinutes: number;
  lessonCount: number;
  mandatory: boolean;
  certificateEnabled: boolean;
  ownerName?: string | null;
  enrolledCount: number;
  averageProgress?: number | null;
  publishedAt?: string | null;
  updatedAt: string;
}

export interface LessonDetail {
  id: string;
  moduleId: string;
  title: string;
  contentType: LessonContentType;
  contentUrl?: string | null;
  contentHtml?: string | null;
  assetId?: string | null;
  durationSeconds: number;
  sortOrder: number;
  preview: boolean;
  mandatory: boolean;
  assessmentId?: string | null;
}

export interface ModuleDetail {
  id: string;
  title: string;
  summary?: string | null;
  sortOrder: number;
  lessons: LessonDetail[];
}

export interface CourseStats {
  enrolled: number;
  completed: number;
  inProgress: number;
  overdue: number;
  averageProgress: number;
  certificatesIssued: number;
  averageScore?: number | null;
}

export interface CourseDetail {
  summary: CourseSummary;
  description?: string | null;
  language: string;
  version: number;
  seatLimit?: number | null;
  passingScore: number;
  ownerId?: string | null;
  certificateTemplateId?: string | null;
  prerequisiteIds: string[];
  prerequisites: Array<{ id: string; title: string; status: CourseStatus }>;
  instructors: Array<{ id: string; name: string; email: string; avatarUrl?: string | null }>;
  modules: ModuleDetail[];
  stats: CourseStats;
}

export interface Category {
  id: string;
  name: string;
  slug: string;
  description?: string | null;
  color?: string | null;
  sortOrder: number;
  courseCount: number;
}

export interface EnrollmentSummary {
  id: string;
  courseId: string;
  courseTitle: string;
  courseSlug?: string | null;
  thumbnailUrl?: string | null;
  categoryName?: string | null;
  deliveryType: DeliveryType;
  userId: string;
  learnerName?: string | null;
  learnerEmail?: string | null;
  status: EnrollmentStatus;
  source: string;
  progressPercent: number;
  lessonsCompleted: number;
  lessonCount: number;
  estimatedMinutes: number;
  mandatory: boolean;
  overdue: boolean;
  dueAt?: string | null;
  completedAt?: string | null;
  lastAccessedAt?: string | null;
  enrolledAt: string;
}

export interface LearnerDashboard {
  assigned: number;
  inProgress: number;
  completed: number;
  overdue: number;
  certificates: number;
  learningMinutes: number;
  currentStreakDays: number;
  continueLearning: EnrollmentSummary[];
  dueSoon: EnrollmentSummary[];
  upcomingSessions: Array<{
    id: string;
    courseId: string;
    courseTitle: string;
    title: string;
    provider: string;
    joinUrl?: string | null;
    startsAt: string;
    endsAt: string;
  }>;
}

export interface PlayerLesson {
  id: string;
  title: string;
  contentType: LessonContentType;
  contentUrl?: string | null;
  contentHtml?: string | null;
  assetId?: string | null;
  durationSeconds: number;
  mandatory: boolean;
  preview: boolean;
  status: ProgressStatus;
  lastPositionSeconds: number;
  secondsWatched: number;
  assessmentId?: string | null;
  assessmentPassed: boolean;
  assessmentScore?: number | null;
  locked: boolean;
}

export interface PlayerView {
  enrollmentId: string;
  courseId: string;
  courseTitle: string;
  courseSummary?: string | null;
  description?: string | null;
  deliveryType: DeliveryType;
  progressPercent: number;
  status: EnrollmentStatus;
  dueAt?: string | null;
  overdue: boolean;
  certificateEnabled: boolean;
  certificateId?: string | null;
  modules: Array<{
    id: string;
    title: string;
    summary?: string | null;
    sortOrder: number;
    lessons: PlayerLesson[];
  }>;
  nextLessonId?: string | null;
}

export interface ProgressResponse {
  lessonId: string;
  status: ProgressStatus;
  progressPercent: number;
  enrollmentStatus: EnrollmentStatus;
  courseCompleted: boolean;
  certificateId?: string | null;
  nextLessonId?: string | null;
}

export interface UserSummary {
  id: string;
  email: string;
  firstName: string;
  lastName?: string | null;
  displayName: string;
  jobTitle?: string | null;
  avatarUrl?: string | null;
  status: UserStatus;
  roles: RoleCode[];
  orgUnitId?: string | null;
  orgUnitName?: string | null;
  lastLoginAt?: string | null;
  createdAt: string;
}

export interface UserDetail extends UserSummary {
  phone?: string | null;
  managerId?: string | null;
  managerName?: string | null;
  locale?: string | null;
  timezone?: string | null;
  mfaEnabled: boolean;
  learning: {
    enrolled: number;
    completed: number;
    overdue: number;
    certificates: number;
    averageProgress: number;
  };
}

export interface OrgUnitNode {
  id: string;
  parentId?: string | null;
  name: string;
  code?: string | null;
  depth: number;
  memberCount: number;
  children: OrgUnitNode[];
}

export interface AssessmentSummary {
  id: string;
  courseId: string;
  lessonId?: string | null;
  title: string;
  description?: string | null;
  type: 'QUIZ' | 'EXAM' | 'SURVEY';
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  timeLimitMinutes?: number | null;
  maxAttempts: number;
  passingScore: number;
  questionsPerAttempt?: number | null;
  shuffleQuestions: boolean;
  shuffleOptions: boolean;
  negativeMarking: number;
  showCorrectAnswers: boolean;
  questionCount: number;
  totalPoints: number;
  attemptCount: number;
  averageScore?: number | null;
  awaitingGrading: number;
}

export interface QuestionDetail {
  id: string;
  type: QuestionType;
  prompt: string;
  explanation?: string | null;
  points: number;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
  sortOrder: number;
  options: Array<{ id: string; label: string; correct: boolean; sortOrder: number }>;
}

export interface AttemptView {
  attemptId: string;
  assessmentId: string;
  title: string;
  description?: string | null;
  attemptNumber: number;
  maxAttempts: number;
  timeLimitMinutes?: number | null;
  startedAt: string;
  expiresAt?: string | null;
  passingScore: number;
  questions: Array<{
    id: string;
    type: QuestionType;
    prompt: string;
    points: number;
    options: Array<{ id: string; label: string }>;
  }>;
  savedAnswers: Array<{ questionId: string; selectedOptions: string[]; textAnswer?: string | null }>;
}

export interface AttemptResult {
  attemptId: string;
  assessmentId: string;
  title: string;
  status: 'IN_PROGRESS' | 'SUBMITTED' | 'GRADED' | 'EXPIRED';
  score: number;
  maxScore: number;
  percentage: number;
  passed: boolean;
  requiresGrading: boolean;
  passingScore: number;
  attemptNumber: number;
  maxAttempts: number;
  attemptsRemaining: number;
  submittedAt?: string | null;
  timeSpentSeconds: number;
  review: Array<{
    questionId: string;
    prompt: string;
    type: QuestionType;
    points: number;
    pointsAwarded: number;
    correct?: boolean | null;
    selectedOptions: string[];
    correctOptions: string[];
    textAnswer?: string | null;
    explanation?: string | null;
    feedback?: string | null;
    options: Array<{ id: string; label: string; correct: boolean; sortOrder: number }>;
  }>;
}

export interface CertificateView {
  id: string;
  courseId: string;
  courseTitle: string;
  recipientName: string;
  serialNumber: string;
  verificationCode: string;
  verificationUrl: string;
  score?: number | null;
  issuedAt: string;
  expiresAt?: string | null;
  valid: boolean;
  expired: boolean;
  revoked: boolean;
  revokedReason?: string | null;
}

export interface VerificationResult {
  valid: boolean;
  status: 'VALID' | 'EXPIRED' | 'REVOKED';
  recipientName: string;
  courseTitle: string;
  issuerName: string;
  serialNumber: string;
  issuedAt: string;
  expiresAt?: string | null;
}

export interface AdminDashboard {
  headline: {
    activeLearners: number;
    publishedCourses: number;
    enrolments: number;
    completions: number;
    overdue: number;
    certificates: number;
    completionRate: number;
    learningHours: number;
    completionsDelta: number;
  };
  activity: Array<{ week: string; enrolled: number; completed: number }>;
  topCourses: DashboardCourseRow[];
  needsAttention: DashboardCourseRow[];
  recentActivity: Array<{ action: string; summary?: string | null; actorEmail?: string | null; at: string }>;
  awaitingGrading: number;
}

export interface DashboardCourseRow {
  courseId: string;
  title: string;
  enrolled: number;
  completed: number;
  completionRate: number;
  averageProgress: number;
  overdue: number;
}

export interface ReportColumn {
  key: string;
  label: string;
  type: 'TEXT' | 'NUMBER' | 'PERCENT' | 'DATE';
}

export interface ReportDefinition {
  key: string;
  title: string;
  description: string;
  columns: ReportColumn[];
}

export interface ReportResult {
  key: string;
  title: string;
  columns: ReportColumn[];
  rows: Array<Record<string, unknown>>;
  rowCount: number;
  generatedAt: string;
}

export interface TenantSettings {
  id: string;
  slug: string;
  name: string;
  customDomain?: string | null;
  status: 'TRIAL' | 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED';
  plan: 'FREE' | 'PRO' | 'ENTERPRISE';
  timezone: string;
  locale: string;
  currency: string;
  maxUsers: number;
  maxStorageBytes: number;
  apiRateLimit: number;
  features: Record<string, boolean>;
  trialEndsAt?: string | null;
  createdAt: string;
  usage: {
    activeUsers: number;
    invitedUsers: number;
    courses: number;
    publishedCourses: number;
    enrollments: number;
    certificates: number;
    storageBytes: number;
    seatUtilisationPercent: number;
    storageUtilisationPercent: number;
  };
}

export interface Branding {
  logoUrl?: string | null;
  logoDarkUrl?: string | null;
  faviconUrl?: string | null;
  brandHue: number;
  brandChroma: number;
  accentHue: number;
  defaultTheme: 'LIGHT' | 'DARK' | 'SYSTEM';
  loginHeadline?: string | null;
  loginSubtext?: string | null;
  supportEmail?: string | null;
  emailFromName?: string | null;
  emailFooter?: string | null;
  customCss?: string | null;
}

export interface NotificationItem {
  id: string;
  event: string;
  title: string;
  body?: string | null;
  link?: string | null;
  severity: 'INFO' | 'SUCCESS' | 'WARNING' | 'CRITICAL';
  read: boolean;
  createdAt: string;
}

export interface Inbox {
  notifications: Page<NotificationItem>;
  unreadCount: number;
}

export interface AuditEntry {
  id: number;
  action: string;
  entityType?: string | null;
  entityId?: string | null;
  summary?: string | null;
  actorId?: string | null;
  actorEmail?: string | null;
  ipAddress?: string | null;
  metadata?: Record<string, unknown> | null;
  createdAt: string;
}

export interface PlatformOverview {
  tenants: number;
  activeTenants: number;
  trialTenants: number;
  suspendedTenants: number;
  users: number;
  courses: number;
  enrolments: number;
  certificates: number;
  storageBytes: number;
  plans: Array<{ plan: string; tenants: number; users: number }>;
  signups: Array<{ month: string; tenants: number }>;
}

export interface TenantRow {
  id: string;
  slug: string;
  name: string;
  customDomain?: string | null;
  status: 'TRIAL' | 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED';
  plan: 'FREE' | 'PRO' | 'ENTERPRISE';
  users: number;
  maxUsers: number;
  courses: number;
  enrolments: number;
  trialEndsAt?: string | null;
  createdAt: string;
}

export interface GradingQueueItem {
  attemptId: string;
  assessmentId: string;
  assessmentTitle?: string | null;
  courseId?: string | null;
  courseTitle?: string | null;
  userId: string;
  learnerName: string;
  submittedAt: string;
  pendingQuestions: number;
}
