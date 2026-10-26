import { Routes } from '@angular/router';
import { Dashboard } from './features/dashboard';
import { Jobs } from './features/jobs';
import { Audit } from './features/audit';
import { Inventory } from './features/inventory';
import { Players } from './features/players';
import { Logs } from './features/logs';
import { Monitoring } from './features/monitoring';
import { Console } from './features/console';
import { Settings } from './features/settings';
import { Backups } from './features/backups';
import { Login } from './features/login';
import { Configuration } from './features/configuration';
import { Mods } from './features/mods';

export const navigation = [
  { path: 'dashboard', label: 'Dashboard', icon: 'dashboard', milestone: 'M01–M04' },
  { path: 'console', label: 'Konsole', icon: 'console', milestone: 'M05' },
  { path: 'players', label: 'Spieler', icon: 'players', milestone: 'M06' },
  { path: 'mods', label: 'Mods', icon: 'mods', milestone: 'M10–M11' },
  { path: 'configuration', label: 'Konfiguration', icon: 'configuration', milestone: 'M09' },
  { path: 'backups', label: 'Backups', icon: 'backups', milestone: 'M07–M08' },
  { path: 'monitoring', label: 'Monitoring', icon: 'monitoring', milestone: 'M12' },
  { path: 'logs', label: 'Serverlogs', icon: 'log', milestone: 'M05 / M13' },
  { path: 'jobs', label: 'Serverjobs', icon: 'jobs', milestone: 'M04 / M13' },
  { path: 'audit', label: 'Audit-Protokoll', icon: 'audit', milestone: 'M13' },
  { path: 'inventory', label: 'Bestandsaufnahme', icon: 'files', milestone: 'M01' },
  { path: 'settings', label: 'Einstellungen', icon: 'settings', milestone: 'M02 / M13' }
];
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: 'sign-in', component: Login },
  { path: 'dashboard', component: Dashboard },
  { path: 'jobs', component: Jobs },
  { path: 'jobs/:jobId', component: Jobs },
  { path: 'audit', component: Audit },
  { path: 'inventory', component: Inventory },
  { path: 'players', component: Players },
  { path: 'logs', component: Logs },
  { path: 'monitoring', component: Monitoring },
  { path: 'console', component: Console },
  { path: 'settings', component: Settings },
  { path: 'backups', component: Backups },
  { path: 'configuration', component: Configuration },
  { path: 'mods', component: Mods },
  { path: '**', redirectTo: 'dashboard' }
];
