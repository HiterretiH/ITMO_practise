import { Routes } from '@angular/router';

import { CheckResultsPageComponent } from './component/check-results-page/check-results-page.component';
import { PageNotFoundComponent } from './component/page-not-found/page-not-found.component';
import { UploadPageComponent } from './component/upload-page/upload-page.component';

export const routes: Routes = [
  { path: '', component: UploadPageComponent },
  { path: 'results', component: CheckResultsPageComponent },
  { path: '**', component: PageNotFoundComponent },
];
