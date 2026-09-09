model `model` {
  street: string;
}

model Order {
  address: `<caret>model`;
}
