import { Routes } from '@angular/router';
import { Dashboard } from './features/dashboard';
import { PlannedFeature } from './features/planned-feature';
import { Jobs } from './features/jobs';
import { Audit } from './features/audit';
import { Inventory } from './features/inventory';

export const navigation = [
  { path: 'dashboard', label: 'Dashboard', milestone: 'M01–M04' },
  { path: 'inventory', label: 'Bestandsaufnahme', milestone: 'M01' },
  { path: 'jobs', label: 'Serverjobs', milestone: 'M04 / M13' },
  { path: 'audit', label: 'Audit-Protokoll', milestone: 'M13' },
  { path: 'console', label: 'Konsole', milestone: 'M05' },
  { path: 'players', label: 'Spieler', milestone: 'M06' },
  { path: 'backups', label: 'Backups', milestone: 'M07–M08' },
  { path: 'configuration', label: 'Konfiguration', milestone: 'M09' },
  { path: 'mods', label: 'Mods', milestone: 'M10–M11' },
  { path: 'logs', label: 'Logs', milestone: 'M05 / M13' },
  { path: 'monitoring', label: 'Monitoring', milestone: 'M12' },
  { path: 'settings', label: 'Einstellungen', milestone: 'M02 / M13' }
];
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: 'dashboard', component: Dashboard },
  { path: 'jobs', component: Jobs },
  { path: 'jobs/:jobId', component: Jobs },
  { path: 'audit', component: Audit },
  { path: 'inventory', component: Inventory },
  ...navigation.filter(item => !['dashboard', 'jobs', 'audit', 'inventory'].includes(item.path)).map(item => ({
    path: item.path, component: PlannedFeature, data: item
  })),
  { path: '**', redirectTo: 'dashboard' }
];
