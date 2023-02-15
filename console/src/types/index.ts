export interface Metadata {
  name: string;
  labels?: {
    [key: string]: string;
  } | null;
  annotations?: {
    [key: string]: string;
  } | null;
  version?: number | null;
  creationTimestamp?: string | null;
  deletionTimestamp?: string | null;
}

export interface LinkGroupSpec {
  displayName: string;
  priority?: number;
  links: string[];
}

export interface LinkSpec {
  url: string;
  displayName: string;
  logo?: string;
  description?: string;
  priority?: number;
}

export interface Link {
  spec: LinkSpec;
  apiVersion: string;
  kind: string;
  metadata: Metadata;
}

export interface LinkGroup {
  spec: LinkGroupSpec;
  apiVersion: string;
  kind: string;
  metadata: Metadata;
}

export interface LinkList {
  page: number;
  size: number;
  total: number;
  items: Array<Link>;
  first: boolean;
  last: boolean;
  hasNext: boolean;
  hasPrevious: boolean;
  totalPages: number;
}

export interface LinkGroupList {
  page: number;
  size: number;
  total: number;
  items: Array<LinkGroup>;
  first: boolean;
  last: boolean;
  hasNext: boolean;
  hasPrevious: boolean;
  totalPages: number;
}
