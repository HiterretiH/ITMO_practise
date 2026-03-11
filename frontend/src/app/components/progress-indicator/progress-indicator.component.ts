import { Component, Input } from '@angular/core';
import { ProgressBarModule } from 'primeng/progressbar';

@Component({
  selector: 'app-progress-indicator',
  standalone: true,
  imports: [ProgressBarModule],
  templateUrl: './progress-indicator.component.html',
  styleUrl: './progress-indicator.component.css',
})
export class ProgressIndicatorComponent {
  @Input() status: 'idle' | 'uploading' | 'analyzing' | 'done' = 'idle';
}

