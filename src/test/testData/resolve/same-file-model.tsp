model Address {
  street: string;
}

model Order {
  address: <caret>Address;
}
