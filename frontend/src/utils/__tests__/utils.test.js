import { describe, expect, it } from 'vitest';
import { formatMoney, formatPoints } from '../money.js';
import { formatDeliveryDate, formatOrderDate } from '../date.js';
import { clearGuestBasket, readGuestBasket, writeGuestBasket } from '../storage.js';

describe('formatMoney', () => {
  it.each([
    ['0.00', '₹0.00'],
    ['5.50', '₹5.50'],
    ['999.99', '₹999.99'],
    ['1090.00', '₹1,090.00'],
    ['99999.00', '₹99,999.00'],
    ['100000.00', '₹1,00,000.00'],
    ['12345678.90', '₹1,23,45,678.90'],
  ])('formats %s as %s', (input, expected) => {
    expect(formatMoney(input)).toBe(expected);
  });

  it('pads a single decimal and keeps a whole number valid', () => {
    expect(formatMoney('10.5')).toBe('₹10.50');
    expect(formatMoney('10')).toBe('₹10.00');
  });

  it('does not lose precision on large values', () => {
    expect(formatMoney('9007199254740993.01')).toBe('₹9,00,71,99,25,47,40,993.01');
  });

  it.each([[''], [null], [undefined], ['abc'], ['-5.00'], ['1.234']])('returns "" for %s', (input) => {
    expect(formatMoney(input)).toBe('');
  });
});

describe('formatPoints', () => {
  it('groups thousands and rejects non-integers', () => {
    expect(formatPoints(1000)).toBe('1,000');
    expect(formatPoints(0)).toBe('0');
    expect(formatPoints(1.5)).toBe('');
    expect(formatPoints(-1)).toBe('');
  });
});

describe('formatDeliveryDate', () => {
  it('formats the calendar date as "Mon, 21 Jul" independent of time zone', () => {
    expect(formatDeliveryDate('2026-07-21')).toBe('Tue, 21 Jul');
    expect(formatDeliveryDate('2026-11-12')).toBe('Thu, 12 Nov');
    expect(formatDeliveryDate('2026-01-01')).toBe('Thu, 1 Jan');
  });

  it.each([[''], [null], ['21/07/2026'], ['2026-02-30'], ['2026-13-01']])('returns "" for %s', (input) => {
    expect(formatDeliveryDate(input)).toBe('');
  });
});

describe('formatOrderDate', () => {
  it('formats an instant in India time', () => {
    expect(formatOrderDate('2026-11-12T10:15:30Z')).toBe('12 Nov 2026, 15:45');
  });

  it('returns "" for an invalid instant', () => {
    expect(formatOrderDate('not a date')).toBe('');
  });
});

describe('guest basket storage', () => {
  it('returns an empty basket when nothing is stored', () => {
    expect(readGuestBasket()).toEqual([]);
  });

  it('round-trips lines and keeps only bookId and quantity', () => {
    writeGuestBasket([{ bookId: 7, quantity: 2, title: 'ignored', price: '500.00' }]);
    expect(readGuestBasket()).toEqual([{ bookId: 7, quantity: 2 }]);
    expect(window.localStorage.getItem('bookygeek.guestBasket')).not.toContain('title');
  });

  it('survives corrupt JSON and wrong shapes', () => {
    window.localStorage.setItem('bookygeek.guestBasket', '{not json');
    expect(readGuestBasket()).toEqual([]);
    window.localStorage.setItem('bookygeek.guestBasket', '{"a":1}');
    expect(readGuestBasket()).toEqual([]);
  });

  it('drops invalid, out-of-range and duplicate lines', () => {
    window.localStorage.setItem(
      'bookygeek.guestBasket',
      JSON.stringify([
        { bookId: 1, quantity: 2 },
        { bookId: 1, quantity: 5 },
        { bookId: 2, quantity: 0 },
        { bookId: 3, quantity: 11 },
        { bookId: 'x', quantity: 1 },
        null,
        { bookId: 4, quantity: 10 },
      ]),
    );
    expect(readGuestBasket()).toEqual([
      { bookId: 1, quantity: 2 },
      { bookId: 4, quantity: 10 },
    ]);
  });

  it('clears the basket', () => {
    writeGuestBasket([{ bookId: 7, quantity: 1 }]);
    clearGuestBasket();
    expect(readGuestBasket()).toEqual([]);
  });
});
