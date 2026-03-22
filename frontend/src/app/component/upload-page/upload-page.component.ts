import { Component } from '@angular/core';

import { HeaderComponent } from '../header/header.component';

@Component({
  selector: 'app-upload-page',
  standalone: true,
  imports: [HeaderComponent],
  templateUrl: './upload-page.component.html',
  styleUrl: './upload-page.component.css',
})
export class UploadPageComponent {}
