import { Component, Input } from '@angular/core';
import { NgIf } from '@angular/common';

@Component({
  selector: 'app-results-summary',
  standalone: true,
  imports: [NgIf],
  templateUrl: './results-summary.component.html',
  styleUrl: './results-summary.component.css',
})
export class ResultsSummaryComponent {
  @Input() totalErrors = 0;
  @Input() criticalErrors = 0;
}

