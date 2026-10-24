import { afterEach, describe, expect, it } from 'vitest';
import { hasDemoSession, signIn, signOut } from '../demoAuth';

describe('demoAuth', () => {
  afterEach(() => signOut());

  it('stores a session only for the fixed credentials', () => {
    expect(signIn('coordenacao@distrischool.com', '123456')).toBe(true);
    expect(hasDemoSession()).toBe(true);
  });

  it('does not create a session for invalid credentials', () => {
    expect(signIn('invalid@example.com', 'errada')).toBe(false);
    expect(hasDemoSession()).toBe(false);
  });

  it('removes the session when the user signs out', () => {
    signIn('coordenacao@distrischool.com', '123456');
    signOut();

    expect(hasDemoSession()).toBe(false);
  });
});
