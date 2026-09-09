interface Store {
  list(): string[];
  get(id: string): string;
}
