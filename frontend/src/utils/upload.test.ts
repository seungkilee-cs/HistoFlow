import { describe, expect, it } from 'vitest';
import { createPartNumberAllocator } from './upload';

describe('createPartNumberAllocator', () => {
  it('claims each part exactly once before returning null', () => {
    const claimPart = createPartNumberAllocator(5);

    const claimed = Array.from({ length: 7 }, () => claimPart()).filter((value): value is number => value !== null);

    expect(claimed).toEqual([1, 2, 3, 4, 5]);
    expect(claimPart()).toBeNull();
  });
});
