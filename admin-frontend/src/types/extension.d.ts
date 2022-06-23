import type { Extension } from "@halo-dev/admin-shared";

export type LinkGroupSpec = {
  displayName: string;
  priority: number;
};

export type LinkSpec = {
  url: string;
  displayName: string;
  logo: string;
  description: string;
  groupName: string;
  priority: number;
};

export type LinkGroup = Extension<LinkGroupSpec>;

export type Link = Extension<LinkSpec>;
