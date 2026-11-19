import { describe, expect, it } from 'vitest';
import { PASSWORD, WRONG_PASSWORD } from '../../test/credentials';
import {
  validateCardNumber,
  validateConfirmPassword,
  validateCvv,
  validateEmail,
  validateExpiry,
  validateFullName,
  validateNameOnCard,
  validatePassword,
  validatePhone,
  validatePinCode,
  validateRequired,
  validateUpiId,
} from '../validators.js';

const NOW = new Date(2026, 10, 12); // 12 Nov 2026, local time

describe('registration rules (FR-AUTH-02)', () => {
  it('requires a full name of at most 100 characters', () => {
    expect(validateFullName('')).not.toBe('');
    expect(validateFullName('   ')).not.toBe('');
    expect(validateFullName('Asha Verma')).toBe('');
    expect(validateFullName('x'.repeat(101))).not.toBe('');
  });

  it('requires a well-formed e-mail', () => {
    expect(validateEmail('')).not.toBe('');
    expect(validateEmail('asha')).not.toBe('');
    expect(validateEmail('asha@example')).not.toBe('');
    expect(validateEmail('asha verma@example.com')).not.toBe('');
    expect(validateEmail('asha.verma@example.com')).toBe('');
  });

  it.each([
    ['Abc1234', 'too short'],
    ['a1' + 'b'.repeat(63), 'too long (65)'],
    ['12345678', 'no letter'],
    ['abcdefgh', 'no digit'],
  ])('rejects password %s (%s)', (password) => {
    expect(validatePassword(password)).not.toBe('');
  });

  it('accepts 8 and 64 character passwords with a letter and a digit', () => {
    expect(validatePassword('Abcdef12')).toBe('');
    expect(validatePassword('a1' + 'b'.repeat(62))).toBe('');
  });

  it('requires the confirmation to match', () => {
    expect(validateConfirmPassword(PASSWORD, '')).not.toBe('');
    expect(validateConfirmPassword(PASSWORD, WRONG_PASSWORD)).not.toBe('');
    expect(validateConfirmPassword(PASSWORD, PASSWORD)).toBe('');
  });
});

describe('address rules (FR-CHK-04)', () => {
  it('requires exactly 10 digits for phone', () => {
    for (const bad of ['', '987654321', '98765432101', '98765abcde', '9876 54321']) {
      expect(validatePhone(bad)).not.toBe('');
    }
    expect(validatePhone('9876543210')).toBe('');
  });

  it('requires exactly 6 digits for the PIN code', () => {
    for (const bad of ['', '56001', '5600011', '56000a']) {
      expect(validatePinCode(bad)).not.toBe('');
    }
    expect(validatePinCode('560001')).toBe('');
  });

  it('flags a blank required field using its label', () => {
    expect(validateRequired('  ', 'your city')).toBe('Enter your city.');
    expect(validateRequired('Pune', 'your city')).toBe('');
  });
});

describe('payment rules (PL-06)', () => {
  it('requires the XXXX-XXXX-XXXX-XXXX card format', () => {
    for (const bad of ['', '4111111111111111', '4111-1111-1111-111', '4111-1111-1111-11111', 'abcd-1111-1111-1111']) {
      expect(validateCardNumber(bad)).not.toBe('');
    }
    expect(validateCardNumber('4111-1111-1111-1111')).toBe('');
    expect(validateCardNumber('4000-0000-0000-0002')).toBe('');
  });

  it('requires a 2 to 60 character name on card', () => {
    expect(validateNameOnCard('A')).not.toBe('');
    expect(validateNameOnCard('x'.repeat(61))).not.toBe('');
    expect(validateNameOnCard('Asha Verma')).toBe('');
  });

  it('requires exactly 3 CVV digits', () => {
    for (const bad of ['', '12', '1234', 'abc']) expect(validateCvv(bad)).not.toBe('');
    expect(validateCvv('123')).toBe('');
  });

  it('requires MM/YYYY with a valid month, not earlier than the current month', () => {
    expect(validateExpiry('', NOW)).not.toBe('');
    expect(validateExpiry('1/2030', NOW)).not.toBe('');
    expect(validateExpiry('13/2030', NOW)).not.toBe('');
    expect(validateExpiry('00/2030', NOW)).not.toBe('');
    expect(validateExpiry('10/2026', NOW)).toBe('This card has expired.');
    expect(validateExpiry('12/2025', NOW)).toBe('This card has expired.');
    expect(validateExpiry('11/2026', NOW)).toBe('');
    expect(validateExpiry('12/2026', NOW)).toBe('');
    expect(validateExpiry('01/2031', NOW)).toBe('');
  });

  it('accepts the approved UPI success and decline ids and rejects malformed ids', () => {
    expect(validateUpiId('demo@upi')).toBe('');
    expect(validateUpiId('failure@upi')).toBe('');
    for (const bad of ['', 'demo', '@upi', 'a@upi', 'demo@', 'demo@1upi', 'de mo@upi']) {
      expect(validateUpiId(bad)).not.toBe('');
    }
  });
});
