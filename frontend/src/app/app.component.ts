import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { FileUploadComponent } from './components/file-upload/file-upload.component';
import { ProgressIndicatorComponent } from './components/progress-indicator/progress-indicator.component';
import { ResultsSummaryComponent } from './components/results-summary/results-summary.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, FileUploadComponent, ProgressIndicatorComponent, ResultsSummaryComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  title = 'Автоматическая проверка шаблона ВКР';
}
