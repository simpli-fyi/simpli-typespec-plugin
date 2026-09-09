namespace Foo {
  model Bar {}
}

model User {
  lastSegment: Foo.<caret>Bar;
}
