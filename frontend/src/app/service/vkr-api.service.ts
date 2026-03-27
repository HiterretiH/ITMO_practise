import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of, throwError, timer } from 'rxjs';
import { catchError, filter, switchMap, take } from 'rxjs/operators';

import { environment } from '../config/env';
import {
  ErrorResponse,
  ValidateJobCreatedResponse,
  ValidateJobStatusResponse,
  ValidationResult,
} from '../models/validation';

@Injectable({ providedIn: 'root' })
export class VkrApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  validateDocument(file: File): Observable<ValidationResult> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http
      .post<ValidateJobCreatedResponse>(`${this.baseUrl}/validate`, formData, { observe: 'response' })
      .pipe(
        switchMap((res) => {
          if (res.status === 202) {
            const id = res.body?.job_id;
            if (!id) {
              return throwError(() => new Error('Сервер не вернул идентификатор задания'));
            }
            return this.pollValidationJob(id);
          }
          return throwError(() => new Error(`Неожиданный ответ сервера: HTTP ${res.status}`));
        }),
        catchError((err: HttpErrorResponse) => {
          if (err.status === 422 && err.error && this.looksLikeValidationResult(err.error)) {
            return of(err.error as ValidationResult);
          }
          return throwError(() => err);
        })
      );
  }

  private pollValidationJob(jobId: string): Observable<ValidationResult> {
    return timer(0, 800).pipe(
      switchMap(() =>
        this.http.get<ValidateJobStatusResponse>(`${this.baseUrl}/validate/jobs/${jobId}`).pipe(
          catchError((err: HttpErrorResponse) => {
            if (err.status === 404) {
              return throwError(
                () =>
                  new HttpErrorResponse({
                    status: 404,
                    error: {
                      message:
                        'Результат проверки больше недоступен (истёк срок хранения). Загрузите файл снова.',
                    },
                  })
              );
            }
            return throwError(() => err);
          })
        )
      ),
      filter((r) => r.status === 'completed' || r.status === 'failed'),
      take(1),
      switchMap((r) => {
        if (r.status === 'failed') {
          return throwError(
            () =>
              new HttpErrorResponse({
                status: 500,
                error: { code: 'JOB_FAILED', message: r.message ?? 'Ошибка обработки документа' },
              })
          );
        }
        const body = r.result;
        if (!body) {
          return throwError(() => new Error('Пустой результат проверки'));
        }
        return of(body);
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

  static parseErrorMessage(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      const body = err.error;
      if (body && typeof body === 'object' && 'message' in body) {
        return (body as ErrorResponse).message;
      }
      if (typeof body === 'string' && body.length > 0) {
        return body;
      }
      return err.message || `HTTP ${err.status}`;
    }
    if (err instanceof Error) {
      return err.message;
    }
    return String(err);
  }

  private looksLikeValidationResult(x: unknown): boolean {
    if (typeof x !== 'object' || x === null) {
      return false;
    }
    const o = x as Record<string, unknown>;
    return 'summary' in o && 'errors' in o;
  }
}
