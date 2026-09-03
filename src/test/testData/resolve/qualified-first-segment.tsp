namespace Foo {
  model Bar {}
}

model User {
  firstSegment: <caret>Foo.Bar;
}
