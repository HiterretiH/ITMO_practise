import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Injectable({
  providedIn: 'root',
})
export class CheckApiService {
  private readonly baseUrl = '/api/check';

  constructor(private http: HttpClient) {}

  // Методы работы с backend будут добавлены позже
}

