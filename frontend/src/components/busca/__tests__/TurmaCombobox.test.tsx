import { fireEvent, render, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { TurmaCombobox } from '../TurmaCombobox';

const turmas = [
  { id: 1, codigo: '9A-2026', nome: '9º Ano A', anoLetivo: 2026, turno: 'MANHA', capacidade: 30, professores: [], disciplinas: [] },
  { id: 2, codigo: '9B-2026', nome: '9º Ano B', anoLetivo: 2026, turno: 'TARDE', capacidade: 30, professores: [], disciplinas: [] },
  { id: 3, codigo: '9A-2025', nome: '9º Ano A', anoLetivo: 2025, turno: 'MANHA', capacidade: 30, professores: [], disciplinas: [] }
];
const requisicoes: URL[] = [];
const server = setupServer(http.get('/api/turmas', ({ request }) => {
  const url = new URL(request.url); requisicoes.push(url);
  const ano = Number(url.searchParams.get('anoLetivo')); const busca = url.searchParams.get('busca')?.toLowerCase() ?? '';
  const content = turmas.filter((turma) => turma.anoLetivo === ano && (turma.codigo.toLowerCase().includes(busca) || turma.nome.toLowerCase().includes(busca)));
  return HttpResponse.json({ content, page: { size: 10, number: 0, totalElements: content.length, totalPages: 1 } });
}));
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => { server.resetHandlers(); requisicoes.length = 0; });
afterAll(() => server.close());

describe('TurmaCombobox', () => {
  it('filtra pelo ano letivo atual por padrão e por parte do código ou do nome', async () => {
    render(<TurmaCombobox anoInicial={2026} />);
    const campo = screen.getByRole('combobox', { name: 'Turma' });
    expect(screen.getByLabelText('Ano letivo')).toHaveValue(2026);

    fireEvent.focus(campo);
    expect(await screen.findAllByRole('option')).toHaveLength(2);
    fireEvent.change(campo, { target: { value: 'ano b' } });

    expect(await screen.findByRole('option', { name: '9B-2026 — 9º Ano B — 2026 — TARDE' })).toBeVisible();
    const ultima = requisicoes[requisicoes.length - 1];
    expect(ultima.searchParams.get('anoLetivo')).toBe('2026');
    expect(ultima.searchParams.get('busca')).toBe('ano b');
    expect(ultima.searchParams.get('size')).toBe('10');
  });

  it('troca o ano letivo e limpa a turma escolhida', async () => {
    let escolhida: unknown = 'inicial';
    render(<TurmaCombobox anoInicial={2026} onSelect={(turma) => { escolhida = turma; }} />);
    fireEvent.focus(screen.getByRole('combobox', { name: 'Turma' }));
    fireEvent.click(await screen.findByRole('option', { name: '9A-2026 — 9º Ano A — 2026 — MANHA' }));
    expect(escolhida).toMatchObject({ id: 1 });

    fireEvent.change(screen.getByLabelText('Ano letivo'), { target: { value: '2025' } });
    expect(escolhida).toBeNull();
    const campo = screen.getByRole('combobox', { name: 'Turma' });
    expect(campo).toHaveValue('');
    fireEvent.focus(campo);
    expect(await screen.findByRole('option', { name: '9A-2025 — 9º Ano A — 2025 — MANHA' })).toBeVisible();
  });

  it('não sugere a turma excluída e mantém o ano fixo', async () => {
    render(<TurmaCombobox label="Turma de destino" excluirId={1} anoFixo={2026} />);
    expect(screen.queryByRole('spinbutton', { name: 'Ano letivo' })).not.toBeInTheDocument();
    fireEvent.focus(screen.getByRole('combobox', { name: 'Turma de destino' }));

    expect(await screen.findByRole('option', { name: '9B-2026 — 9º Ano B — 2026 — TARDE' })).toBeVisible();
    expect(screen.queryByRole('option', { name: /9A-2026/ })).not.toBeInTheDocument();
  });

  it('mostra mensagem quando não há turmas no ano', async () => {
    render(<TurmaCombobox anoInicial={2030} />);
    fireEvent.focus(screen.getByRole('combobox', { name: 'Turma' }));
    expect(await screen.findByText('Nenhuma turma encontrada em 2030.')).toBeVisible();
  });
});
