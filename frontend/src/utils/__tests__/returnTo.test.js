import { describe, expect, it } from 'vitest';
import { returnToQuery, safeReturnTo } from '../returnTo.js';

describe('safeReturnTo', () => {
  it.each([
    ['/orders', '/orders'],
    ['/orders?x=1#top', '/orders?x=1#top'],
    ['/books/7', '/books/7'],
  ])('keeps the in-app path %s', (value, expected) => {
    expect(safeReturnTo(value)).toBe(expected);
  });

  it.each([
    [null],
    [undefined],
    [''],
    ['orders'],
    ['https://evil.example/'],
    ['//evil.example/path'],
    ['/\\evil.example'],
    ['/ok\nSet-Cookie: x=1'],
    ['/login'],
    ['/login?returnTo=%2Forders'],
    ['/register'],
  ])('falls back to the home page for %j', (value) => {
    expect(safeReturnTo(value)).toBe('/');
  });
});

describe('returnToQuery', () => {
  it('is empty for the home page and encodes any other path', () => {
    expect(returnToQuery('/')).toBe('');
    expect(returnToQuery('/orders?x=1')).toBe('?returnTo=%2Forders%3Fx%3D1');
  });
});
