export function normalizeTelefone(value: string) {
  return value.replace(/\D/g, '').slice(0, 11);
}

export function isValidTelefone(value: string) {
  const digits = normalizeTelefone(value);
  return /^(?:[1-9]\d)(?:\d{8}|\d{9})$/.test(digits) && !/^(\d)\1+$/.test(digits);
}

export function formatTelefone(value: string) {
  const digits = normalizeTelefone(value);
  if (digits.length === 0) return '';
  if (digits.length <= 2) return `(${digits}`;
  if (digits.length <= 6) return `(${digits.slice(0, 2)}) ${digits.slice(2)}`;
  if (digits.length <= 10) return `(${digits.slice(0, 2)}) ${digits.slice(2, 6)}-${digits.slice(6)}`;
  return `(${digits.slice(0, 2)}) ${digits.slice(2, 7)}-${digits.slice(7)}`;
}
