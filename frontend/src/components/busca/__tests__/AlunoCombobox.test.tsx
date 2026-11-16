import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { AlunoCombobox } from '../AlunoCombobox';

// Simula a API de alunos com 150 cadastros ordenados por nome: filtro nome (parte, sem diferenciar
// maiúsculas), filtro matricula (igual) e paginação, como o gestao-escolar-service.
const nomes = Array.from({ length: 149 }, (_, i) => `Aluno ${String(i + 1).padStart(3, '0')}`).concat('Zuleica Prado');
const alunos = nomes.map((nome, i) => ({ id: i + 1, matricula: `2026${String(i + 1).padStart(6, '0')}`, nome }));
const requisicoes: URL[] = [];

const server = setupServer(http.get('/api/alunos', ({ request }) => {
  const url = new URL(request.url); requisicoes.push(url);
  const nome = url.searchParams.get('nome')?.toLowerCase(); const matricula = url.searchParams.get('matricula');
  const size = Number(url.searchParams.get('size') ?? 20); const page = Number(url.searchParams.get('page') ?? 0);
  const filtrados = alunos.filter((aluno) => (!nome || aluno.nome.toLowerCase().includes(nome)) && (!matricula || aluno.matricula === matricula));
  return HttpResponse.json({ content: filtrados.slice(page * size, (page + 1) * size), page: { size, number: page, totalElements: filtrados.length, totalPages: Math.ceil(filtrados.length / size) } });
}));
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => { server.resetHandlers(); requisicoes.length = 0; });
afterAll(() => server.close());

function renderizar() {
  render(<form aria-label="formulário"><AlunoCombobox required /></form>);
  return screen.getByRole('combobox', { name: 'Aluno' });
}
function digitar(campo: HTMLElement, texto: string) { fireEvent.focus(campo); fireEvent.change(campo, { target: { value: texto } }); }
const valorEnviado = () => new FormData(screen.getByRole('form', { name: 'formulário' }) as HTMLFormElement).get('matriculaAluno');

describe('AlunoCombobox', () => {
  it('busca por parte do nome depois da pausa na digitação e envia o número de matrícula', async () => {
    const campo = renderizar();
    fireEvent.focus(campo);
    fireEvent.change(campo, { target: { value: 'zu' } });
    fireEvent.change(campo, { target: { value: 'zul' } });
    fireEvent.change(campo, { target: { value: 'zule' } });

    fireEvent.click(await screen.findByRole('option', { name: 'Zuleica Prado — 2026000150' }));

    // Uma única consulta, com o último texto, filtro nome e no máximo 10 resultados
    expect(requisicoes).toHaveLength(1);
    expect(requisicoes[0].searchParams.get('nome')).toBe('zule');
    expect(requisicoes[0].searchParams.get('size')).toBe('10');
    expect(campo).toHaveValue('Zuleica Prado — 2026000150');
    expect(valorEnviado()).toBe('2026000150');
  });

  it('usa o filtro matricula quando o texto tem só dígitos', async () => {
    const campo = renderizar();
    digitar(campo, '2026000042');

    expect(await screen.findByRole('option', { name: 'Aluno 042 — 2026000042' })).toBeVisible();
    expect(requisicoes[0].searchParams.get('matricula')).toBe('2026000042');
    expect(requisicoes[0].searchParams.has('nome')).toBe(false);
  });

  it('avisa quando nenhum aluno é encontrado', async () => {
    const campo = renderizar();
    digitar(campo, 'inexistente');

    expect(await screen.findByText('Nenhum aluno encontrado.')).toBeVisible();
    expect(screen.queryByRole('option')).not.toBeInTheDocument();
    expect(campo).toHaveAttribute('aria-expanded', 'false');
    expect(valorEnviado()).toBe('');
  });

  it('encontra um aluno que ficaria fora dos 100 primeiros em ordem alfabética', async () => {
    // Na listagem antiga (page=0&size=100, ordem por nome) a Zuleica não aparecia
    const primeiraPagina = alunos.slice(0, 100).map((aluno) => aluno.nome);
    expect(primeiraPagina).not.toContain('Zuleica Prado');

    const campo = renderizar();
    digitar(campo, 'Zuleica');

    expect(await screen.findByRole('option', { name: 'Zuleica Prado — 2026000150' })).toBeVisible();
    expect(requisicoes.every((url) => url.searchParams.get('size') === '10')).toBe(true);
  });

  it('navega pelas sugestões com as setas e escolhe com Enter', async () => {
    const campo = renderizar();
    digitar(campo, 'Aluno 00');
    await screen.findAllByRole('option');
    expect(campo).toHaveAttribute('aria-expanded', 'true');

    fireEvent.keyDown(campo, { key: 'ArrowDown' });
    fireEvent.keyDown(campo, { key: 'ArrowDown' });
    const ativa = screen.getByRole('option', { name: 'Aluno 002 — 2026000002' });
    expect(ativa).toHaveAttribute('aria-selected', 'true');
    expect(campo).toHaveAttribute('aria-activedescendant', ativa.id);

    fireEvent.keyDown(campo, { key: 'ArrowUp' });
    fireEvent.keyDown(campo, { key: 'Enter' });

    expect(campo).toHaveValue('Aluno 001 — 2026000001');
    expect(campo).toHaveAttribute('aria-expanded', 'false');
    expect(valorEnviado()).toBe('2026000001');
  });

  it('não consulta a API com menos de 2 caracteres e invalida texto sem escolha', async () => {
    const campo = renderizar();
    digitar(campo, 'Z');
    expect(await screen.findByText('Digite ao menos 2 caracteres para buscar.')).toBeInTheDocument();
    await new Promise((resolve) => setTimeout(resolve, 400));
    expect(requisicoes).toHaveLength(0);
    await waitFor(() => expect((campo as HTMLInputElement).validity.valid).toBe(false));
  });
});
