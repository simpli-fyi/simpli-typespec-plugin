model Base {
  id: string;
}

model Derived {
  ...<caret>Base;
}
