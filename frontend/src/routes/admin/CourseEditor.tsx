import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@ds/components/forms/Button';
import { Input } from '@ds/components/forms/Input';
import { Label } from '@ds/components/forms/Label';
import { Select } from '@ds/components/forms/Select';
import { Textarea } from '@ds/components/forms/Textarea';
import { Switch } from '@ds/components/forms/Switch';
import { Badge } from '@ds/components/feedback/Badge';
import { Alert } from '@ds/components/feedback/Alert';
import { Dialog } from '@ds/components/overlays/Dialog';
import { Tabs } from '@ds/components/navigation/Tabs';
import { StatTile } from '@ds/components/core/StatTile';

import { ApiError, api } from '@/lib/api';
import { formatSeconds } from '@/lib/format';
import type { Category, CourseDetail, LessonDetail } from '@/lib/types';
import { ErrorState, FullPageSpinner, PageHeader } from '@/components/states';
import { IconChevronLeft, IconClose, IconPlus } from '@/components/icons';

type Tab = 'structure' | 'details' | 'insights';

export default function CourseEditor() {
  const { courseId } = useParams<{ courseId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [tab, setTab] = useState<Tab>('structure');
  const [notice, setNotice] = useState<string | null>(null);

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['course', courseId],
    queryFn: () => api.get<CourseDetail>(`/courses/${courseId}`),
    enabled: !!courseId,
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['course', courseId] });
    queryClient.invalidateQueries({ queryKey: ['admin-courses'] });
  };

  const changeStatus = useMutation({
    mutationFn: (status: string) => api.patch(`/courses/${courseId}/status`, { status }),
    onSuccess: invalidate,
    onError: (caught) =>
      setNotice(caught instanceof ApiError ? caught.message : 'Could not change the status.'),
  });

  if (isLoading) return <FullPageSpinner inline />;
  if (error) return <ErrorState error={error} onRetry={refetch} />;
  if (!data) return null;

  const course = data.summary;

  return (
    <div className="app-inner-wide">
      <button
        type="button"
        className="link row row-gap-2"
        style={{ background: 'none', border: 0, padding: 0, marginBottom: 12, cursor: 'pointer' }}
        onClick={() => navigate('/admin/courses')}
      >
        <IconChevronLeft size={14} />
        All courses
      </button>

      <PageHeader
        title={course.title}
        subtitle={course.summary}
        actions={
          <>
            <Badge status={course.status} size="md" />
            {course.status !== 'PUBLISHED' ? (
              <Button loading={changeStatus.isPending} onClick={() => changeStatus.mutate('PUBLISHED')}>
                Publish
              </Button>
            ) : (
              <Button variant="outline" onClick={() => changeStatus.mutate('DRAFT')}>
                Unpublish
              </Button>
            )}
          </>
        }
      />

      {notice && (
        <div style={{ marginBottom: 16 }}>
          <Alert tone="warning" title="Could not publish" onDismiss={() => setNotice(null)}>
            {notice}
          </Alert>
        </div>
      )}

      <div style={{ marginBottom: 18 }}>
        <Tabs
          value={tab}
          onValueChange={(value) => setTab(value as Tab)}
          tabs={[
            { value: 'structure', label: 'Structure', count: course.lessonCount },
            { value: 'details', label: 'Details' },
            { value: 'insights', label: 'Insights', count: data.stats.enrolled },
          ]}
        />
      </div>

      {tab === 'structure' && <Structure course={data} onChanged={invalidate} />}
      {tab === 'details' && <Details course={data} onSaved={invalidate} />}
      {tab === 'insights' && <Insights course={data} />}
    </div>
  );
}

// =====================================================================
// Structure
// =====================================================================

function Structure({ course, onChanged }: { course: CourseDetail; onChanged: () => void }) {
  const courseId = course.summary.id;
  const [addingTo, setAddingTo] = useState<string | null>(null);
  const [editing, setEditing] = useState<{ moduleId: string; lesson: LessonDetail } | null>(null);

  const addModule = useMutation({
    mutationFn: () => api.post(`/courses/${courseId}/modules`, { title: 'New section' }),
    onSuccess: onChanged,
  });

  const deleteModule = useMutation({
    mutationFn: (moduleId: string) => api.delete(`/courses/${courseId}/modules/${moduleId}`),
    onSuccess: onChanged,
  });

  const deleteLesson = useMutation({
    mutationFn: (lessonId: string) => api.delete(`/courses/${courseId}/lessons/${lessonId}`),
    onSuccess: onChanged,
  });

  const renameModule = useMutation({
    mutationFn: ({ moduleId, title }: { moduleId: string; title: string }) =>
      api.put(`/courses/${courseId}/modules/${moduleId}`, { title }),
    onSuccess: onChanged,
  });

  return (
    <div className="stack stack-4">
      {course.modules.map((module) => (
        <section key={module.id} className="surface">
          <div className="surface-header">
            <input
              defaultValue={module.title}
              onBlur={(event) => {
                const title = event.target.value.trim();
                if (title && title !== module.title) {
                  renameModule.mutate({ moduleId: module.id, title });
                }
              }}
              aria-label="Section title"
              style={{
                fontSize: 'var(--text-base)',
                fontWeight: 'var(--font-weight-semibold)',
                border: 0,
                background: 'transparent',
                color: 'var(--foreground)',
                padding: '2px 0',
                minWidth: 0,
                flex: 1,
              }}
            />
            <div className="row row-gap-2">
              <Button size="sm" variant="outline" onClick={() => setAddingTo(module.id)}>
                <IconPlus size={14} />
                Lesson
              </Button>
              {course.modules.length > 1 && (
                <button
                  type="button"
                  className="icon-button"
                  aria-label={`Delete ${module.title}`}
                  onClick={() => deleteModule.mutate(module.id)}
                >
                  <IconClose size={15} />
                </button>
              )}
            </div>
          </div>

          {module.lessons.length === 0 ? (
            <div style={{ padding: '20px 18px', fontSize: 'var(--text-sm)', color: 'var(--muted-foreground)' }}>
              No lessons in this section yet.
            </div>
          ) : (
            module.lessons.map((lesson, index) => (
              <div key={lesson.id} className="surface-row">
                <span
                  aria-hidden="true"
                  style={{
                    width: 24,
                    height: 24,
                    borderRadius: 'var(--radius-sm)',
                    background: 'var(--muted)',
                    color: 'var(--muted-foreground)',
                    display: 'grid',
                    placeItems: 'center',
                    fontSize: 'var(--text-xs)',
                    flexShrink: 0,
                  }}
                >
                  {index + 1}
                </span>

                <div style={{ flex: 1, minWidth: 0 }}>
                  <div className="truncate" style={{ fontSize: 'var(--text-sm)', fontWeight: 500 }}>
                    {lesson.title}
                  </div>
                  <div className="meta" style={{ marginTop: 2 }}>
                    <Badge tone="neutral" size="sm">
                      {lesson.contentType}
                    </Badge>
                    {lesson.durationSeconds > 0 && (
                      <>
                        <span className="meta-dot" />
                        <span>{formatSeconds(lesson.durationSeconds)}</span>
                      </>
                    )}
                    {!lesson.mandatory && (
                      <>
                        <span className="meta-dot" />
                        <span>Optional</span>
                      </>
                    )}
                    {lesson.preview && (
                      <>
                        <span className="meta-dot" />
                        <span>Preview</span>
                      </>
                    )}
                  </div>
                </div>

                <Button size="sm" variant="ghost" onClick={() => setEditing({ moduleId: module.id, lesson })}>
                  Edit
                </Button>
                <button
                  type="button"
                  className="icon-button"
                  aria-label={`Delete ${lesson.title}`}
                  onClick={() => deleteLesson.mutate(lesson.id)}
                >
                  <IconClose size={15} />
                </button>
              </div>
            ))
          )}
        </section>
      ))}

      <div>
        <Button variant="outline" onClick={() => addModule.mutate()} loading={addModule.isPending}>
          <IconPlus size={15} />
          Add section
        </Button>
      </div>

      <LessonDialog
        courseId={courseId}
        moduleId={addingTo ?? editing?.moduleId ?? null}
        lesson={editing?.lesson ?? null}
        open={!!addingTo || !!editing}
        onClose={() => {
          setAddingTo(null);
          setEditing(null);
        }}
        onSaved={onChanged}
      />
    </div>
  );
}

function LessonDialog({
  courseId,
  moduleId,
  lesson,
  open,
  onClose,
  onSaved,
}: {
  courseId: string;
  moduleId: string | null;
  lesson: LessonDetail | null;
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [title, setTitle] = useState('');
  const [contentType, setContentType] = useState('HTML');
  const [contentUrl, setContentUrl] = useState('');
  const [contentHtml, setContentHtml] = useState('');
  const [durationSeconds, setDurationSeconds] = useState(0);
  const [mandatory, setMandatory] = useState(true);
  const [preview, setPreview] = useState(false);

  useEffect(() => {
    if (!open) return;
    setTitle(lesson?.title ?? '');
    setContentType(lesson?.contentType ?? 'HTML');
    setContentUrl(lesson?.contentUrl ?? '');
    setContentHtml(lesson?.contentHtml ?? '');
    setDurationSeconds(lesson?.durationSeconds ?? 0);
    setMandatory(lesson?.mandatory ?? true);
    setPreview(lesson?.preview ?? false);
  }, [open, lesson]);

  const save = useMutation({
    mutationFn: () => {
      const body = {
        title: title.trim(),
        contentType,
        contentUrl: contentUrl.trim() || null,
        contentHtml: contentHtml.trim() || null,
        assetId: lesson?.assetId ?? null,
        durationSeconds,
        preview,
        mandatory,
      };
      return lesson
        ? api.put(`/courses/${courseId}/lessons/${lesson.id}`, body)
        : api.post(`/courses/${courseId}/modules/${moduleId}/lessons`, body);
    },
    onSuccess: () => {
      onSaved();
      onClose();
    },
  });

  const needsUrl = ['VIDEO', 'AUDIO', 'PDF', 'LINK', 'SCORM'].includes(contentType);

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={lesson ? 'Edit lesson' : 'New lesson'}
      size="lg"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button loading={save.isPending} disabled={!title.trim()} onClick={() => save.mutate()}>
            {lesson ? 'Save lesson' : 'Add lesson'}
          </Button>
        </>
      }
    >
      <div className="field">
        <Label htmlFor="lesson-title" required>
          Title
        </Label>
        <Input
          id="lesson-title"
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          autoFocus
        />
      </div>

      <div className="row row-gap-3" style={{ alignItems: 'flex-start' }}>
        <div className="field" style={{ flex: 1 }}>
          <Label htmlFor="lesson-type">Type</Label>
          <Select
            id="lesson-type"
            value={contentType}
            onValueChange={setContentType}
            options={[
              { value: 'HTML', label: 'Written lesson' },
              { value: 'VIDEO', label: 'Video' },
              { value: 'AUDIO', label: 'Audio' },
              { value: 'PDF', label: 'PDF' },
              { value: 'LINK', label: 'External link' },
              { value: 'QUIZ', label: 'Quiz' },
              { value: 'SCORM', label: 'SCORM package' },
            ]}
          />
        </div>
        <div className="field" style={{ flex: 1 }}>
          <Label htmlFor="lesson-duration" hint="Seconds">
            Duration
          </Label>
          <Input
            id="lesson-duration"
            type="number"
            min={0}
            value={durationSeconds}
            onChange={(event) => setDurationSeconds(Number(event.target.value))}
          />
        </div>
      </div>

      {needsUrl && (
        <div className="field">
          <Label htmlFor="lesson-url" hint="Paste a URL, or upload to the media library">
            Content URL
          </Label>
          <Input
            id="lesson-url"
            value={contentUrl}
            onChange={(event) => setContentUrl(event.target.value)}
            placeholder="https://…"
          />
        </div>
      )}

      {contentType === 'HTML' && (
        <div className="field">
          <Label htmlFor="lesson-html" hint="HTML is allowed">
            Lesson content
          </Label>
          <Textarea
            id="lesson-html"
            rows={10}
            value={contentHtml}
            onChange={(event) => setContentHtml(event.target.value)}
            placeholder="<h2>Section</h2><p>…</p>"
          />
        </div>
      )}

      {contentType === 'QUIZ' && (
        <Alert tone="info" title="Quiz lessons">
          Create the questions from the course's Details tab. A learner cannot complete this lesson
          until they pass the quiz.
        </Alert>
      )}

      <div className="stack stack-3" style={{ marginTop: 8 }}>
        <Switch
          checked={mandatory}
          onCheckedChange={setMandatory}
          label="Required for completion"
          description="Optional lessons do not count toward course progress."
        />
        <Switch
          checked={preview}
          onCheckedChange={setPreview}
          label="Free preview"
          description="Visible in the catalog before enrolling."
        />
      </div>
    </Dialog>
  );
}

// =====================================================================
// Details
// =====================================================================

function Details({ course, onSaved }: { course: CourseDetail; onSaved: () => void }) {
  const summary = course.summary;
  const [form, setForm] = useState({
    title: summary.title,
    summary: summary.summary ?? '',
    description: course.description ?? '',
    categoryId: summary.categoryId ?? '',
    level: summary.level as string,
    deliveryType: summary.deliveryType as string,
    enrollmentMode: summary.enrollmentMode as string,
    passingScore: course.passingScore,
    mandatory: summary.mandatory,
    certificateEnabled: summary.certificateEnabled,
    tags: summary.tags.join(', '),
  });
  const [saved, setSaved] = useState(false);

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: () => api.get<Category[]>('/categories'),
  });

  const save = useMutation({
    mutationFn: () =>
      api.put(`/courses/${summary.id}`, {
        title: form.title.trim(),
        summary: form.summary.trim() || null,
        description: form.description.trim() || null,
        categoryId: form.categoryId || null,
        ownerId: course.ownerId,
        level: form.level,
        deliveryType: form.deliveryType,
        enrollmentMode: form.enrollmentMode,
        language: course.language,
        estimatedMinutes: summary.estimatedMinutes,
        seatLimit: course.seatLimit,
        passingScore: form.passingScore,
        mandatory: form.mandatory,
        certificateEnabled: form.certificateEnabled,
        certificateTemplateId: course.certificateTemplateId,
        tags: form.tags
          .split(',')
          .map((tag) => tag.trim())
          .filter(Boolean),
        prerequisiteIds: course.prerequisiteIds,
        instructorIds: course.instructors.map((instructor) => instructor.id),
      }),
    onSuccess: () => {
      setSaved(true);
      onSaved();
    },
  });

  return (
    <form
      className="surface"
      style={{ padding: 22, maxWidth: 760 }}
      onSubmit={(event) => {
        event.preventDefault();
        setSaved(false);
        save.mutate();
      }}
    >
      {saved && (
        <div style={{ marginBottom: 16 }}>
          <Alert tone="success" title="Course saved" onDismiss={() => setSaved(false)} />
        </div>
      )}

      <div className="field">
        <Label htmlFor="title" required>
          Title
        </Label>
        <Input
          id="title"
          value={form.title}
          onChange={(event) => setForm({ ...form, title: event.target.value })}
        />
      </div>

      <div className="field">
        <Label htmlFor="summary" hint="Catalog card">
          Summary
        </Label>
        <Textarea
          id="summary"
          rows={2}
          maxLength={600}
          value={form.summary}
          onChange={(event) => setForm({ ...form, summary: event.target.value })}
        />
      </div>

      <div className="field">
        <Label htmlFor="description">Full description</Label>
        <Textarea
          id="description"
          rows={6}
          value={form.description}
          onChange={(event) => setForm({ ...form, description: event.target.value })}
        />
      </div>

      <div className="row row-gap-3" style={{ alignItems: 'flex-start' }}>
        <div className="field" style={{ flex: 1 }}>
          <Label htmlFor="category">Category</Label>
          <Select
            id="category"
            value={form.categoryId}
            onValueChange={(value) => setForm({ ...form, categoryId: value })}
            options={[
              { value: '', label: 'Uncategorised' },
              ...(categories ?? []).map((category) => ({ value: category.id, label: category.name })),
            ]}
          />
        </div>
        <div className="field" style={{ flex: 1 }}>
          <Label htmlFor="level">Level</Label>
          <Select
            id="level"
            value={form.level}
            onValueChange={(value) => setForm({ ...form, level: value })}
            options={[
              { value: 'BEGINNER', label: 'Beginner' },
              { value: 'INTERMEDIATE', label: 'Intermediate' },
              { value: 'ADVANCED', label: 'Advanced' },
            ]}
          />
        </div>
      </div>

      <div className="row row-gap-3" style={{ alignItems: 'flex-start' }}>
        <div className="field" style={{ flex: 1 }}>
          <Label htmlFor="enrollment" hint="Who can join">
            Enrolment
          </Label>
          <Select
            id="enrollment"
            value={form.enrollmentMode}
            onValueChange={(value) => setForm({ ...form, enrollmentMode: value })}
            options={[
              { value: 'MANUAL', label: 'Assigned by an admin' },
              { value: 'SELF', label: 'Anyone can join' },
              { value: 'INVITE', label: 'Invitation only' },
            ]}
          />
        </div>
        <div className="field" style={{ flex: 1 }}>
          <Label htmlFor="passing" hint="Percent">
            Pass mark
          </Label>
          <Input
            id="passing"
            type="number"
            min={0}
            max={100}
            value={form.passingScore}
            onChange={(event) => setForm({ ...form, passingScore: Number(event.target.value) })}
          />
        </div>
      </div>

      <div className="field">
        <Label htmlFor="tags" hint="Comma separated">
          Tags
        </Label>
        <Input
          id="tags"
          value={form.tags}
          onChange={(event) => setForm({ ...form, tags: event.target.value })}
          placeholder="security, owasp, mandatory"
        />
      </div>

      <div className="stack stack-3" style={{ margin: '4px 0 20px' }}>
        <Switch
          checked={form.mandatory}
          onCheckedChange={(value) => setForm({ ...form, mandatory: value })}
          label="Required training"
          description="Flagged as mandatory in compliance reports."
        />
        <Switch
          checked={form.certificateEnabled}
          onCheckedChange={(value) => setForm({ ...form, certificateEnabled: value })}
          label="Award a certificate on completion"
        />
      </div>

      <Button type="submit" loading={save.isPending}>
        Save changes
      </Button>
    </form>
  );
}

// =====================================================================
// Insights
// =====================================================================

function Insights({ course }: { course: CourseDetail }) {
  const stats = course.stats;

  return (
    <div className="stat-grid">
      <StatTile label="Enrolled" value={stats.enrolled} />
      <StatTile label="Completed" value={stats.completed} caption={`${stats.inProgress} in progress`} />
      <StatTile
        label="Overdue"
        value={stats.overdue}
        deltaTone="negative"
        caption={stats.overdue ? 'Past the deadline' : 'None overdue'}
      />
      <StatTile label="Average progress" value={stats.averageProgress} unit="%" />
      <StatTile label="Certificates" value={stats.certificatesIssued} />
      <StatTile
        label="Average score"
        value={stats.averageScore ?? '—'}
        unit={stats.averageScore == null ? undefined : '%'}
        caption={stats.averageScore == null ? 'No graded attempts yet' : undefined}
      />
    </div>
  );
}
