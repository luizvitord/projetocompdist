import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './styles/index.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <main className="grid min-h-screen place-items-center p-6">
      <p className="text-slate-600">DistriSchool está sendo preparado.</p>
    </main>
  </StrictMode>
);
