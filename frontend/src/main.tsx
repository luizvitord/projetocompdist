import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';
import { router } from './app/router';
import { FeedbackProvider } from './components/feedback/FeedbackProvider';
import './styles/index.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <FeedbackProvider><RouterProvider router={router} /></FeedbackProvider>
  </StrictMode>
);
