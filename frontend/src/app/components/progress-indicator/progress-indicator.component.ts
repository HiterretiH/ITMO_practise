import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { ProgressBarModule } from 'primeng/progressbar';

@Component({
  selector: 'app-progress-indicator',
  standalone: true,
  imports: [CommonModule, ProgressBarModule],
  templateUrl: './progress-indicator.component.html',
  styleUrl: './progress-indicator.component.css',
})
export class ProgressIndicatorComponent {
  @Input() status: 'idle' | 'uploading' | 'analyzing' | 'done' = 'idle';
}

