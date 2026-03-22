import { Component } from '@angular/core';

import { HeaderComponent } from '../header/header.component';

@Component({
  selector: 'app-check-results-page',
  standalone: true,
  imports: [HeaderComponent],
  templateUrl: './check-results-page.component.html',
  styleUrl: './check-results-page.component.css',
})
export class CheckResultsPageComponent {}
