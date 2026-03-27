import { Component } from '@angular/core';
import { CardModule } from 'primeng/card';

@Component({
  selector: 'app-requirements-footnote-card',
  standalone: true,
  imports: [CardModule],
  templateUrl: './requirements-footnote-card.component.html',
  styleUrl: './requirements-footnote-card.component.css',
})
export class RequirementsFootnoteCardComponent {}
