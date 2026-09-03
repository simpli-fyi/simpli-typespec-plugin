model Address {
  street: string;
}

model Page<T> {
  items: T[];
}

model AddressPage {
  page: Page<<caret>Address>;
}
