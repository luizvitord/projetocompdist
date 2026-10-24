import { render, screen } from '@testing-library/react';
import { StatusBadge } from '../StatusBadge';

describe('StatusBadge', () => {
  it('renders semantic labels with non-color text', () => {
    render(<StatusBadge tone="warning">Trancada</StatusBadge>);

    expect(screen.getByText('Trancada')).toBeVisible();
  });
});
