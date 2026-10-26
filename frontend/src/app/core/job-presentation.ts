import { Job, Operation } from './api';

export const operationLabel = (type: Operation): string => ({ START: 'Starten', STOP: 'Stoppen', RESTART: 'Neustarten',
  WHITELIST_ADD: 'Zur Whitelist hinzufügen', WHITELIST_REMOVE: 'Aus Whitelist entfernen', OP_GRANT: 'OP vergeben', OP_REVOKE: 'OP entziehen', CONSOLE: 'Minecraft-Kommando', BACKUP: 'Sicherung erstellen', RESTORE_TEST: 'Isolierter Proberestore',CONFIG_APPLY:'Konfiguration speichern',RESTORE_APPLY:'Snapshot wiederherstellen',MOD_APPLY:'Modsatz ändern' })[type];
export const jobStateLabel = (state: Job['state']): string => ({
  QUEUED: 'Wartet', RUNNING: 'Wird ausgeführt', SUCCEEDED: 'Erfolgreich', FAILED: 'Fehlgeschlagen', UNKNOWN: 'Ergebnis ungeklärt'
})[state];
export function jobExplanation(job: Job): string {
  if (job.state === 'UNKNOWN') return 'Der tatsächliche Ausgang muss am Host geprüft werden. Weitere Serveraktionen bleiben gesperrt. Dieser Job wird nicht automatisch wiederholt.';
  if (job.state === 'QUEUED' || job.state === 'RUNNING') return 'Dieser Job sperrt weitere Serveraktionen, bis sein Ergebnis feststeht.';
  if (job.state === 'FAILED') return 'Die Aktion ist fehlgeschlagen. Ursache und Serverzustand vor einer neuen Aktion prüfen.';
  return job.type === 'CONSOLE' ? 'Minecraft hat auf das Kommando geantwortet. Die Antwort zeigt, ob Minecraft es akzeptiert oder einen Fehler gemeldet hat.' : job.type === 'RESTORE_TEST' ? 'Die Dateien wurden isoliert wiederhergestellt und verifiziert. Die konkrete Antwort und Backupansicht zeigen, ob zusätzlich ein Weltstart geprüft wurde. Die laufende Ursprungswelt wurde nicht ersetzt.' : job.type === 'BACKUP' ? 'Die Datenkopie wurde verschlüsselt gesichert und das Repository vollständig gelesen und geprüft. Der tatsächliche Umfang steht in der Backupansicht.' : 'Die Aktion wurde mit geprüftem Zielzustand abgeschlossen.';
}
export function berlinTime(value: string): string {
  return new Intl.DateTimeFormat('de-DE', { dateStyle: 'short', timeStyle: 'medium', timeZone: 'Europe/Berlin' }).format(new Date(value));
}
export function auditActionLabel(action: string): string {
  if(action==='MOD_IMPORT_PLAN_CREATED')return 'Mod-Importplan erstellt';
  if(action==='MOD_PLAN_CREATED')return 'Modplan erstellt';
  if(action==='MOD_PLAN_CANCELLED')return 'Modplan abgebrochen';
  if(action==='RESTORE_PLAN_CREATED')return 'Wiederherstellungsplan erstellt';
  if(action==='RESTORE_PLAN_CANCELLED')return 'Wiederherstellungsplan abgebrochen';
  return action === 'JOB_RECONCILED' ? 'Job anhand des Hosts abgeglichen' : action === 'INVENTORY_UPDATED' ? 'Bestandsangabe dokumentiert' : action === 'ROLE_UPDATED' ? 'Portalrolle gespeichert' : action === 'ROLE_REVOKED' ? 'Portalzugriff entzogen' : operationLabel(action as Operation) ?? 'Unbekannte Aktion';
}
export function auditOutcomeLabel(outcome: string): string {
  return outcome === 'RECORDED' ? 'Dokumentiert' : jobStateLabel(outcome as Job['state']) ?? 'Unbekannter Status';
}
