import '@testing-library/jest-dom/vitest';
import { transferableAbortController } from 'node:util';

// O ambiente jsdom do Vitest troca AbortController/AbortSignal pelos do jsdom, mas fetch e Request
// continuam sendo os do Node (undici), que recusam um AbortSignal do jsdom ("Expected signal to be
// an instance of AbortSignal"). Isso quebrava as navegações do React Router e os fetch com signal.
// No navegador as duas coisas vêm da mesma implementação; aqui os testes passam a usar as do Node.
const NodeAbortController = transferableAbortController().constructor as typeof AbortController;
const NodeAbortSignal = new NodeAbortController().signal.constructor as typeof AbortSignal;
Object.assign(globalThis, { AbortController: NodeAbortController, AbortSignal: NodeAbortSignal });
Object.assign(window, { AbortController: NodeAbortController, AbortSignal: NodeAbortSignal });
