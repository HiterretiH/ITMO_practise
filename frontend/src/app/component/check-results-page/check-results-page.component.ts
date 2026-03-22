import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { CheckboxModule } from 'primeng/checkbox';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';

import { HeaderComponent } from '../header/header.component';
import { ValidationResult } from '../../models/validation';
import { VkrApiService } from '../../service/vkr-api.service';
import { ValidationSessionService } from '../../service/validation-session.service';

@Component({
  selector: 'app-check-results-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    HeaderComponent,
    ToastModule,
    CardModule,
    ButtonModule,
    TableModule,
    TagModule,
    CheckboxModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './check-results-page.component.html',
  styleUrl: './check-results-page.component.css',
})
export class CheckResultsPageComponent implements OnInit {
  private readonly session = inject(ValidationSessionService);
  private readonly api = inject(VkrApiService);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);

  result: ValidationResult | null = null;
  includeRecommendationsInPdf = true;
  loadingPdf = false;

  ngOnInit(): void {
    this.result = this.session.load();
  }

  downloadPdf(): void {
    if (!this.result) {
      return;
    }
    this.loadingPdf = true;
    this.api
      .generateReportPdf(this.result, {
        include_recommendations: this.includeRecommendationsInPdf,
      })
      .subscribe({
        next: (blob) => {
          this.loadingPdf = false;
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = 'vkr-report.pdf';
          a.click();
          URL.revokeObjectURL(url);
          this.messages.add({ severity: 'success', summary: 'Готово', detail: 'PDF сохранён.' });
        },
        error: (err: HttpErrorResponse) => {
          this.loadingPdf = false;
          this.messages.add({
            severity: 'error',
            summary: 'Ошибка PDF',
            detail: VkrApiService.parseErrorMessage(err),
          });
        },
      });
  }

  newCheck(): void {
    this.session.clear();
    void this.router.navigate(['/']);
  }

  statusSeverity(status: string): 'success' | 'warn' | 'danger' | 'secondary' {
    switch (status) {
      case 'passed':
        return 'success';
      case 'warning':
        return 'warn';
      case 'failed':
        return 'danger';
      default:
        return 'secondary';
    }
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'passed':
        return 'Без критических ошибок';
      case 'warning':
        return 'Есть замечания';
      case 'failed':
        return 'Есть критические ошибки';
      default:
        return status;
    }
  }

  severityLabel(s: string | undefined): string {
    return s === 'critical' ? 'Критично' : 'Замечание';
  }
}
