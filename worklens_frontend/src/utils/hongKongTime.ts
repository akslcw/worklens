const HONG_KONG_TIME_ZONE = 'Asia/Hong_Kong'

function parts(date: Date, options: Intl.DateTimeFormatOptions): Intl.DateTimeFormatPart[] {
  return new Intl.DateTimeFormat('en-US', {
    timeZone: HONG_KONG_TIME_ZONE,
    ...options,
  }).formatToParts(date)
}

function partValue(parts: Intl.DateTimeFormatPart[], type: Intl.DateTimeFormatPartTypes): string {
  return parts.find((part) => part.type === type)?.value ?? ''
}

function hourValue(parts: Intl.DateTimeFormatPart[]): string {
  const hour = partValue(parts, 'hour')
  return hour === '24' ? '00' : hour.padStart(2, '0')
}

/** Formats a date as YYYY-MM-DD in Asia/Hong_Kong, independent of the local zone. */
export function hongKongDateString(date: Date): string {
  const formatted = parts(date, { year: 'numeric', month: '2-digit', day: '2-digit' })
  return `${partValue(formatted, 'year')}-${partValue(formatted, 'month')}-${partValue(formatted, 'day')}`
}

/** Formats an ISO date-time string as YYYY/MM/DD HH:mm in Asia/Hong_Kong. */
export function hongKongDateTimeString(value: string): string {
  const formatted = parts(new Date(value), {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
  return (
    `${partValue(formatted, 'year')}/${partValue(formatted, 'month')}/${partValue(formatted, 'day')} ` +
    `${hourValue(formatted)}:${partValue(formatted, 'minute')}`
  )
}

/** Formats an ISO date-time string as HH:mm in Asia/Hong_Kong. */
export function hongKongTimeString(value: string): string {
  const formatted = parts(new Date(value), { hour: '2-digit', minute: '2-digit', hour12: false })
  return `${hourValue(formatted)}:${partValue(formatted, 'minute')}`
}
