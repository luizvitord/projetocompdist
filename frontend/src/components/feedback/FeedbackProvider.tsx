import { createContext, useContext, useMemo, useState, type PropsWithChildren } from 'react';
import { FeedbackBanner, type FeedbackVariant } from './FeedbackBanner';

type FeedbackContextValue = {
  showSuccess: (message: string) => void;
  showError: (message: string) => void;
  showWarning: (message: string) => void;
};

const FeedbackContext = createContext<FeedbackContextValue | null>(null);

export function FeedbackProvider({ children }: PropsWithChildren) {
  const [feedback, setFeedback] = useState<{ message: string; variant: FeedbackVariant } | null>(null);
  const value = useMemo<FeedbackContextValue>(() => ({
    showSuccess: (message) => setFeedback({ message, variant: 'success' }),
    showError: (message) => setFeedback({ message, variant: 'error' }),
    showWarning: (message) => setFeedback({ message, variant: 'warning' })
  }), []);
  return <FeedbackContext.Provider value={value}>
    {feedback ? <FeedbackBanner {...feedback} onClose={() => setFeedback(null)} /> : null}
    {children}
  </FeedbackContext.Provider>;
}

export function useFeedback() {
  const context = useContext(FeedbackContext);
  if (!context) throw new Error('useFeedback deve ser usado dentro de FeedbackProvider');
  return context;
}
