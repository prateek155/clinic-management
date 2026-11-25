import { useEffect, useMemo, useState } from 'react';
import { Bell, CheckCheck, Clock3, RefreshCw } from 'lucide-react';
import { useAuth } from '../auth/AuthContext.jsx';
import RoleLayout from '../layouts/RoleLayout.jsx';
import { api } from '../services/api.js';

export default function NotificationsPage() {
  const { session } = useAuth();
  const role = useMemo(() => ['DOCTOR', 'STAFF', 'PATIENT'].find((item) => session?.roles?.includes(item)) || 'PATIENT', [session]);
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    let current = true; setLoading(true); setError('');
    api.get('/notifications').then(({ data }) => { if (current) setItems(data); })
      .catch((cause) => { if (current) setError(errorMessage(cause)); })
      .finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [refresh]);
  async function markRead(item) {
    try { await api.patch(`/notifications/${item.id}/read`); setItems((old) => old.map((row) => row.id === item.id ? { ...row, readAt: new Date().toISOString() } : row)); }
    catch (cause) { setError(errorMessage(cause)); }
  }
  async function markAllRead() {
    try { await api.patch('/notifications/read-all'); setItems((old) => old.map((item) => ({ ...item, readAt: item.readAt || new Date().toISOString() }))); }
    catch (cause) { setError(errorMessage(cause)); }
  }
  const unread = items.filter((item) => !item.readAt).length;
  return <RoleLayout role={role}>
    <div className="page-heading"><div><p className="eyebrow">YOUR WORKSPACE</p><h1>Notifications</h1><p>Appointment, consultation, and billing updates for your account.</p></div><button className="button-secondary" onClick={() => setRefresh((value) => value + 1)}><RefreshCw size={16} /> Refresh</button></div>
    {error && <div className="inline-alert inline-alert-error" role="status">{error}</div>}
    <section className="workspace-panel"><div className="section-title"><span className="metric-icon metric-icon-0"><Bell size={18} /></span><div><h2>Recent updates</h2><p>{unread} unread notification{unread === 1 ? '' : 's'}.</p></div>{unread > 0 && <button className="button-secondary section-action" onClick={markAllRead}><CheckCheck size={15} /> Mark all read</button>}</div>
      {loading ? <p className="empty-state">Loading notifications…</p> : items.length ? <div className="notification-list">{items.map((item) => <article className={`notification-card ${item.readAt ? '' : 'notification-unread'}`} key={item.id}><div className="notification-icon"><Bell size={16} /></div><div className="notification-copy"><div className="notification-heading"><strong>{item.title}</strong><time><Clock3 size={13} />{new Date(item.createdAt).toLocaleString()}</time></div><p>{item.body}</p><small>{item.category.replaceAll('_', ' ')}</small></div>{!item.readAt && <button className="button-secondary" onClick={() => markRead(item)}>Mark read</button>}</article>)}</div> : <p className="empty-state">You have no notifications yet.</p>}
    </section>
  </RoleLayout>;
}
function errorMessage(error) { return error.response?.data?.error || error.response?.data?.message || error.message || 'Request failed.'; }
