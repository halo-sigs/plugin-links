import type { RssSpec, VerificationSpec } from "@/api/generated";

export interface LinkFormState {
  url: string;
  displayName: string;
  logo?: string;
  description?: string;
  rss?: RssSpec;
  verification?: VerificationSpec;
  annotations?: Record<string, string>;
}

export interface GroupFormState {
  displayName: string;
  annotations?: Record<string, string>;
}
