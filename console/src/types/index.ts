export interface LinkFormState {
  url: string;
  displayName: string;
  logo?: string;
  description?: string;
  annotations?: Record<string, string>;
}

export interface GroupFormState {
  displayName: string;
  annotations?: Record<string, string>;
}
