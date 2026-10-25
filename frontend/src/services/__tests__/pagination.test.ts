import { describe, expect, it } from 'vitest';
import { toPage } from '../pagination';

describe('toPage', () => {
  it('normalizes PagedModel content and metadata, including an empty last page', () => {
    const page = toPage({
      content: [],
      page: { size: 10, number: 2, totalElements: 20, totalPages: 2 }
    });

    expect(page).toEqual({ content: [], size: 10, number: 2, totalElements: 20, totalPages: 2 });
  });
});
