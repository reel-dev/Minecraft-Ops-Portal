import { Job, Operation } from './api';

export const operationLabel = (type: Operation): string => ({ START: 'Starten', STOP: 'Stoppen', RESTART: 'Neustarten' })[type];
export const jobStateLabel = (state: Job['state']): string => ({
  QUEUED: 'Wartet', RUNNING: 'Wird ausgeführt', SUCCEEDED: 'Erfolgreich', FAILED: 'Fehlgeschlagen', UNKNOWN: 'Ergebnis ungeklärt'
})[state];
export function jobExplanation(job: Job): string {
  if (job.state === 'UNKNOWN') return 'Der tatsächliche Ausgang muss am Host geprüft werden. Weitere Serveraktionen bleiben gesperrt. Dieser Job wird nicht automatisch wiederholt.';
  if (job.state === 'QUEUED' || job.state === 'RUNNING') return 'Dieser Job sperrt weitere Serveraktionen, bis sein Ergebnis feststeht.';
  if (job.state === 'FAILED') return 'Die Aktion ist fehlgeschlagen. Ursache und Serverzustand vor einer neuen Aktion prüfen.';
  return 'Die Aktion wurde mit geprüftem Zielzustand abgeschlossen.';
}
export function berlinTime(value: string): string {
  return new Intl.DateTimeFormat('de-DE', { dateStyle: 'short', timeStyle: 'medium', timeZone: 'Europe/Berlin' }).format(new Date(value));
}
export function auditActionLabel(action: string): string {
  return action === 'INVENTORY_UPDATED' ? 'Bestandsangabe dokumentiert' : operationLabel(action as Operation) ?? 'Unbekannte Aktion';
}
export function auditOutcomeLabel(outcome: string): string {
  return outcome === 'RECORDED' ? 'Dokumentiert' : jobStateLabel(outcome as Job['state']) ?? 'Unbekannter Status';
}
