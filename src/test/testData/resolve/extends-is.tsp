model Base {
  id: string;
}

scalar StringLike extends string;

model Extended extends <caret>Base {
  extra: string;
}
