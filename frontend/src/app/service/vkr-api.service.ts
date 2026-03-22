import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, throwError, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { environment } from '../config/env';
import { ErrorResponse, ValidationResult } from '../models/validation';

@Injectable({ providedIn: 'root' })
export class VkrApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  validateDocument(file: File): Observable<ValidationResult> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http
      .post<ValidationResult>(`${this.baseUrl}/validate`, formData, { observe: 'response' })
      .pipe(
        map((res) => {
          if (!res.body) {
            throw new Error('Пустой ответ сервера');
          }
          return res.body;
        }),
        catchError((err: HttpErrorResponse) => {
          if (err.status === 422 && err.error && this.looksLikeValidationResult(err.error)) {
            return of(err.error as ValidationResult);
          }
          return throwError(() => err);
        })
      );
  }

  generateReportPdf(
    validationResult: ValidationResult,
    options?: { include_recommendations?: boolean }
  ): Observable<Blob> {
    return this.http.post(
      `${this.baseUrl}/report`,
      {
        validation_data: validationResult,
        options: { include_recommendations: options?.include_recommendations ?? true },
      },
      { responseType: 'blob' }
    );
  }

  static parseErrorMessage(err: HttpErrorResponse): string {
    const body = err.error;
    if (body && typeof body === 'object' && 'message' in body) {
      return (body as ErrorResponse).message;
    }
    if (typeof body === 'string' && body.length > 0) {
      return body;
    }
    return err.message || `HTTP ${err.status}`;
  }

  private looksLikeValidationResult(x: unknown): boolean {
    if (typeof x !== 'object' || x === null) {
      return false;
    }
    const o = x as Record<string, unknown>;
    return 'summary' in o && 'errors' in o;
  }
}
