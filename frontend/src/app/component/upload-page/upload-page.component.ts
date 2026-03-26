import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { AfterViewInit, Component, inject, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule, ButtonProps } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { FileUpload, FileUploadHandlerEvent, FileUploadModule } from 'primeng/fileupload';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ToastModule } from 'primeng/toast';

import { HeaderComponent } from '../header/header.component';
import { VkrApiService } from '../../service/vkr-api.service';
import { ValidationSessionService } from '../../service/validation-session.service';

@Component({
  selector: 'app-upload-page',
  standalone: true,
  imports: [
    CommonModule,
    HeaderComponent,
    ToastModule,
    CardModule,
    ButtonModule,
    FileUploadModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './upload-page.component.html',
  styleUrl: './upload-page.component.css',
})
export class UploadPageComponent implements AfterViewInit {
  private readonly api = inject(VkrApiService);
  private readonly session = inject(ValidationSessionService);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);

  @ViewChild('fileUpload') private fileUpload?: FileUpload;

  loading = false;

  uploadButtonProps: ButtonProps = { severity: 'secondary' };
  cancelButtonProps: ButtonProps = { severity: 'secondary' };

  ngAfterViewInit(): void {
    this.syncUploadButtonColors();
  }

  onQueueChanged(): void {
    queueMicrotask(() => this.syncUploadButtonColors());
  }

  private syncUploadButtonColors(): void {
    const hasFiles = this.fileUpload?.hasFiles?.() ?? false;
    const inactive = this.loading || !hasFiles;
    this.uploadButtonProps = inactive ? { severity: 'secondary' } : { severity: 'success' };
    this.cancelButtonProps = inactive ? { severity: 'secondary' } : { severity: 'danger' };
  }

  onUpload(event: FileUploadHandlerEvent): void {
    const file = event.files[0];
    if (!file) {
      return;
    }
    this.loading = true;
    this.syncUploadButtonColors();
    this.api.validateDocument(file).subscribe({
      next: (result) => {
        this.loading = false;
        this.session.save(result);
        this.messages.add({
          severity: result.summary.status === 'failed' ? 'warn' : 'success',
          summary: 'Проверка завершена',
          detail: 'Открываем страницу с результатами.',
        });
        void this.router.navigate(['/results']);
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.syncUploadButtonColors();
        this.messages.add({
          severity: 'error',
          summary: 'Не удалось выполнить проверку',
          detail: VkrApiService.parseErrorMessage(err),
        });
      },
    });
  }
}
