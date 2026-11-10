import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { FeedbackProvider, useFeedback } from '../FeedbackProvider';

function Actions() {
  const { showError, showSuccess, showWarning } = useFeedback();
  return <>
    <button onClick={() => showSuccess('Dados salvos.')}>Sucesso</button>
    <button onClick={() => showError('Não foi possível salvar.')}>Erro</button>
    <button onClick={() => showWarning('Confira os dados.')}>Aviso</button>
  </>;
}

describe('FeedbackProvider', () => {
  it('shows categorized, dismissible messages', () => {
    render(<FeedbackProvider><Actions /></FeedbackProvider>);

    fireEvent.click(screen.getByRole('button', { name: 'Sucesso' }));
    expect(screen.getByRole('status')).toHaveTextContent('Dados salvos.');

    fireEvent.click(screen.getByRole('button', { name: 'Fechar mensagem' }));
    expect(screen.queryByRole('status')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Erro' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Não foi possível salvar.');

    fireEvent.click(screen.getByRole('button', { name: 'Aviso' }));
    expect(screen.getByRole('status')).toHaveTextContent('Confira os dados.');
  });
});
