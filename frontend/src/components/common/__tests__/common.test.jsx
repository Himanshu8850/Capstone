import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import Button from '../Button.jsx';
import EmptyState from '../EmptyState.jsx';
import ErrorMessage from '../ErrorMessage.jsx';
import Field from '../Field.jsx';
import Modal from '../Modal.jsx';
import Spinner from '../Spinner.jsx';

describe('Button', () => {
  it('defaults to type="button" and fires onClick', async () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Save</Button>);
    const button = screen.getByRole('button', { name: 'Save' });
    expect(button).toHaveAttribute('type', 'button');
    await userEvent.click(button);
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('applies the variant class and respects disabled', async () => {
    const onClick = vi.fn();
    render(
      <Button variant="secondary" disabled onClick={onClick}>
        Cancel
      </Button>,
    );
    const button = screen.getByRole('button', { name: 'Cancel' });
    expect(button).toHaveClass('btn--secondary');
    await userEvent.click(button);
    expect(onClick).not.toHaveBeenCalled();
  });
});

describe('Field', () => {
  it('associates the label with the control', () => {
    render(<Field label="Full name" />);
    expect(screen.getByLabelText('Full name')).toBeInTheDocument();
  });

  it('links the error text with aria-describedby and marks the control invalid', () => {
    render(<Field label="Phone" error="Phone must be exactly 10 digits." />);
    const input = screen.getByLabelText('Phone');
    const error = screen.getByRole('alert');
    expect(error).toHaveTextContent('Phone must be exactly 10 digits.');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input.getAttribute('aria-describedby')).toContain(error.id);
  });

  it('links the hint and renders a select through the `as` prop', () => {
    render(
      <Field label="State" as="select" hint="Choose your state">
        <option value="KA">Karnataka</option>
      </Field>,
    );
    const select = screen.getByLabelText('State');
    expect(select.tagName).toBe('SELECT');
    expect(select).not.toHaveAttribute('aria-invalid');
    expect(select.getAttribute('aria-describedby')).toBeTruthy();
    expect(screen.getByText('Choose your state')).toBeInTheDocument();
  });
});

describe('Modal', () => {
  function Harness() {
    const [open, setOpen] = useState(false);
    return (
      <>
        <button onClick={() => setOpen(true)}>Open</button>
        {open ? (
          <Modal title="Confirm" onClose={() => setOpen(false)}>
            <button>First</button>
            <button>Last</button>
          </Modal>
        ) : null}
      </>
    );
  }

  it('is a labelled dialog and moves focus inside on open', async () => {
    render(<Harness />);
    await userEvent.click(screen.getByRole('button', { name: 'Open' }));
    expect(screen.getByRole('dialog', { name: 'Confirm' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'First' })).toHaveFocus();
  });

  it('traps Tab and Shift+Tab inside the dialog', async () => {
    render(<Harness />);
    await userEvent.click(screen.getByRole('button', { name: 'Open' }));
    await userEvent.tab();
    expect(screen.getByRole('button', { name: 'Last' })).toHaveFocus();
    await userEvent.tab();
    expect(screen.getByRole('button', { name: 'First' })).toHaveFocus();
    await userEvent.tab({ shift: true });
    expect(screen.getByRole('button', { name: 'Last' })).toHaveFocus();
  });

  it('closes on Escape and returns focus to the opener', async () => {
    render(<Harness />);
    const opener = screen.getByRole('button', { name: 'Open' });
    await userEvent.click(opener);
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(opener).toHaveFocus();
  });
});

describe('Spinner', () => {
  it('exposes a status role with a label', () => {
    render(<Spinner label="Loading books" />);
    expect(screen.getByRole('status')).toHaveTextContent('Loading books');
  });
});

describe('ErrorMessage', () => {
  it('announces the message and offers Retry only when onRetry is given', async () => {
    const onRetry = vi.fn();
    const { rerender } = render(<ErrorMessage message="Could not load." onRetry={onRetry} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Could not load.');
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(onRetry).toHaveBeenCalledTimes(1);

    rerender(<ErrorMessage message="Could not load." />);
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument();
  });

  it('falls back to a generic message', () => {
    render(<ErrorMessage />);
    expect(screen.getByRole('alert')).toHaveTextContent('Something went wrong');
  });
});

describe('EmptyState', () => {
  it('renders the title, message and children', () => {
    render(
      <EmptyState title="No orders yet" message="Your orders will appear here.">
        <a href="/">Continue your Shopping</a>
      </EmptyState>,
    );
    expect(screen.getByRole('heading', { name: 'No orders yet' })).toBeInTheDocument();
    expect(screen.getByText('Your orders will appear here.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Continue your Shopping' })).toBeInTheDocument();
  });
});
