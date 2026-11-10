import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ConfirmDialog } from '../ConfirmDialog';

describe('ConfirmDialog', () => {
  it('only runs the action after explicit confirmation', async () => {
    const confirm = vi.fn();
    render(<ConfirmDialog title="Confirmar envio" description="Deseja salvar?" onConfirm={confirm} trigger={<button>Salvar</button>} />);

    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }));
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(confirm).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }));
    await waitFor(() => expect(confirm).toHaveBeenCalledOnce());
  });
});
