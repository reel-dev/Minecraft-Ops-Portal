import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { App } from './app/app';
import { routes } from './app/routes';
import { sessionInterceptor } from './app/core/session';

bootstrapApplication(App, { providers: [provideHttpClient(withInterceptors([sessionInterceptor])), provideRouter(routes)] })
  .catch(() => { document.body.textContent = 'Das Portal konnte nicht gestartet werden.'; });
