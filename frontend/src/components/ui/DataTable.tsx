import type { ReactNode } from 'react';

export type DataTableColumn<T> = { header: string; render: (item: T) => ReactNode };

export function DataTable<T>({ columns, items, keyFor }: { columns: DataTableColumn<T>[]; items: T[]; keyFor: (item: T) => string | number }) {
  return <div className="overflow-x-auto rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
    <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
      <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500"><tr>{columns.map((column) => <th key={column.header} className="px-4 py-3 font-semibold">{column.header}</th>)}</tr></thead>
      <tbody className="divide-y divide-slate-100">{items.map((item) => <tr key={keyFor(item)}>{columns.map((column) => <td key={column.header} className="px-4 py-3 text-slate-700">{column.render(item)}</td>)}</tr>)}</tbody>
    </table>
  </div>;
}
