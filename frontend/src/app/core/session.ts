import { Injectable, inject, signal } from '@angular/core';
import { HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
@Injectable({providedIn:'root'})
export class SessionState { readonly name=signal<string|null>(null); }
export const sessionInterceptor:HttpInterceptorFn=(request,next)=>{
 const session=inject(SessionState);
 return next(request).pipe(catchError(error=>{if(error.status===401)session.name.set(null);return throwError(()=>error);}));
};
