export interface Metadata {
  name: string;
  generateName?: string;
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
  displayStyle: string;
  description: string;
  priority?: number;
  // @deprecated
  links: string[];
}

export interface LinkSpec {
  url: string;
  displayName: string;
  logo?: string;
  siteshot?: string;
  label?: string;
  labelColor?: string;
  description?: string;
  priority?: number;
  groupName?: string;
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
