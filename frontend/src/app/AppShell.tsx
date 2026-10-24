import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';

export function AppShell() {
  return <div className="min-h-screen lg:flex"><Sidebar /><div className="min-w-0 flex-1"><Topbar /><main className="p-5 sm:p-8"><Outlet /></main></div></div>;
}
