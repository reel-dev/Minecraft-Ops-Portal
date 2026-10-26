import { Component, input } from '@angular/core';

const paths: Record<string, string> = {
  dashboard: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  console: 'M4 4h16v16H4z M7 8l3 3-3 3 M12 15h5',
  players: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M16 4a4 4 0 0 1 0 8 M22 21v-2a4 4 0 0 0-3-3.87 M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
  mods: 'M8 3h8v5h5v8h-5v5H8v-5H3V8h5z',
  files: 'M3 7V4h6l2 3h10v13H3z',
  configuration: 'M4 6h16 M4 12h16 M4 18h16 M8 3v6 M16 9v6 M9 15v6',
  backups: 'M3 6l9-4 9 4-9 4z M3 6v12l9 4 9-4V6 M12 10v12',
  monitoring: 'M3 3v18h18 M6 14l4-5 4 4 6-8',
  settings: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8 M9 3l-1 3-3 1-2 3 2 2-1 3 3 2 1 3h4l1-3 3-1 2-3-2-2 1-3-3-2-1-3z',
  jobs: 'M9 3h6v4H9z M9 5H5v16h14V5h-4 M8 12l2 2 5-5 M8 18h8',
  audit: 'M4 3h16v18H4z M8 7h8 M8 12h8 M8 17h5',
  cpu: 'M6 6h12v12H6z M9 9h6v6H9z M9 2v4 M15 2v4 M9 18v4 M15 18v4 M2 9h4 M2 15h4 M18 9h4 M18 15h4',
  ram: 'M2 5h20v13H2z M6 9v4 M10 9v4 M14 9v4 M18 9v4 M5 18v3 M10 18v3 M15 18v3 M20 18v3',
  clock: 'M12 8v5l3 2 M21 12a9 9 0 1 1-9-9 9 9 0 0 1 9 9',
  network: 'M7 3v17 M3 7l4-4 4 4 M17 21V4 M13 17l4 4 4-4',
  refresh: 'M20 7V3l-4 4 M4 17v4l4-4 M20 7a9 9 0 0 0-15-2 M4 17a9 9 0 0 0 15 2',
  play: 'M7 3l14 9-14 9z', stop: 'M5 5h14v14H5z', plus: 'M12 4v16 M4 12h16',
  arrow: 'M4 12h16 M14 6l6 6-6 6', shield: 'M12 2l8 4v6c0 5-8 10-8 10S4 17 4 12V6z M8 12l3 3 5-6',
  globe: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0 M3 12h18 M12 3c-5 5-5 13 0 18 5-5 5-13 0-18',
  check: 'M4 12l5 5L20 6', user: 'M20 21v-2a7 7 0 0 0-14 0v2 M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
  log: 'M4 3h16v18H4z M8 7h8 M8 12h8 M8 17h8', warning: 'M12 3L2 21h20z M12 9v5 M12 17v1',
  map: 'M3 5l6-3 6 3 6-3v17l-6 3-6-3-6 3z M9 2v17 M15 5v17',
};
@Component({ selector: 'smp-icon', template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path [attr.d]="path()" /></svg>`, styles: [':host{display:inline-flex;width:20px;height:20px;flex-shrink:0;vertical-align:middle}svg{width:100%;height:100%}'] })
export class Icon { readonly name = input('dashboard'); path() { return paths[this.name()] ?? paths['settings']; } }
