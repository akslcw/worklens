import { describe, expect, it } from 'vitest'
import { hongKongDateString, hongKongDateTimeString, hongKongTimeString } from './hongKongTime'

describe('hongKongTime', () => {
  it('computes the date in Asia/Hong_Kong regardless of the local zone', () => {
    // 2026-07-31T18:00Z is 2026-08-01 02:00 in Hong Kong
    expect(hongKongDateString(new Date('2026-07-31T18:00:00.000Z'))).toBe('2026-08-01')
  })

  it('formats date-times in Asia/Hong_Kong', () => {
    expect(hongKongDateTimeString('2026-07-31T18:30:00.000Z')).toBe('2026/08/01 02:30')
  })

  it('formats times in Asia/Hong_Kong', () => {
    expect(hongKongTimeString('2026-07-31T18:30:00.000Z')).toBe('02:30')
  })
})
