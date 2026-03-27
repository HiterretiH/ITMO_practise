import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';

import { HeaderComponent } from '../header/header.component';
import { RequirementsFootnoteCardComponent } from '../requirements-footnote-card/requirements-footnote-card.component';
import { ValidationResult } from '../../models/validation';
import { VkrApiService } from '../../service/vkr-api.service';
import { ValidationSessionService } from '../../service/validation-session.service';

@Component({
  selector: 'app-check-results-page',
  standalone: true,
  imports: [
    CommonModule,
    HeaderComponent,
    RequirementsFootnoteCardComponent,
    ToastModule,
    CardModule,
    ButtonModule,
    TableModule,
    TagModule,
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
        include_recommendations: true,
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

  goToUpload(): void {
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
    if (s === 'critical') {
      return 'Критично';
    }
    if (s === 'warning') {
      return 'Замечание';
    }
    return 'Важность не указана';
  }

  severityIconClass(s: string | undefined): string {
    if (s === 'critical') {
      return 'pi pi-times-circle severity-icon severity-icon--critical';
    }
    if (s === 'warning') {
      return 'pi pi-exclamation-circle severity-icon severity-icon--warning';
    }
    return 'pi pi-minus-circle severity-icon severity-icon--unknown';
  }

  /** Совпадает с подписью типа в PDF (ReportService.errorTypeRu). */
  errorTypeLabel(type: string | undefined): string {
    if (!type) {
      return '—';
    }
    const labels: Record<string, string> = {
      MISSING_SECTION: 'Отсутствует раздел',
      FONT_MISMATCH: 'Неверный шрифт',
      MARGIN_MISMATCH: 'Неверные поля страницы',
      LINE_SPACING_ERROR: 'Неверный межстрочный интервал',
      INDENT_ERROR: 'Неверный абзацный отступ',
      HEADER_STYLE_ERROR: 'Оформление заголовка',
      PAGE_NUMBER_MISSING: 'Нумерация страниц',
      TABLE_CAPTION_ERROR: 'Подпись таблицы',
      FIGURE_CAPTION_ERROR: 'Подпись рисунка',
      CONTENT_TITLE_MISMATCH: 'Содержание / заголовки',
      INVALID_LIST_FORMAT: 'Оформление списка',
      FORMULA_ERROR: 'Оформление формулы',
      CITATION_ERROR: 'Ссылки и источники',
    };
    return labels[type] ?? type;
  }
}
