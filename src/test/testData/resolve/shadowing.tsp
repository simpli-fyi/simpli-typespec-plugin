model Foo {
  outer: boolean;
}

namespace Inner {
  model Foo {
    inner: boolean;
  }

  model User {
    f: <caret>Foo;
  }
}
