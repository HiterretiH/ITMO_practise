/** Модели по `specification.api` */

export interface ValidationResult {
  document_info: DocumentInfo;
  summary: ValidationSummary;
  errors: ErrorItem[];
}

export interface DocumentInfo {
  filename?: string;
  pages?: number;
  paragraphs_checked?: number;
}

export interface ValidationSummary {
  total_errors: number;
  critical_errors: number;
  status: 'passed' | 'warning' | 'failed';
}

export interface ErrorItem {
  id?: string;
  type?: string;
  severity?: 'critical' | 'warning';
  location?: {
    page?: number | null;
    paragraph?: number | null;
    element?: string | null;
  };
  description?: string;
  expected?: string;
  actual?: string;
  recommendation?: string;
}

export interface ErrorResponse {
  code: string;
  message: string;
}
