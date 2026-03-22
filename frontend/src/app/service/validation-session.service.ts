import { Injectable } from '@angular/core';

import { ValidationResult } from '../models/validation';

const STORAGE_KEY = 'vkr_validation_result';

/**
 * Хранит последний результат проверки (sessionStorage), чтобы страница результатов
 * открывалась после редиректа и переживала обновление вкладки.
 */
@Injectable({ providedIn: 'root' })
export class ValidationSessionService {
  save(result: ValidationResult): void {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(result));
  }

  load(): ValidationResult | null {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as ValidationResult;
    } catch {
      return null;
    }
  }

  clear(): void {
    sessionStorage.removeItem(STORAGE_KEY);
  }
}
