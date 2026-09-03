namespace Common {
  model Widget {}
}

namespace MyOrg {
  using <caret>Common;

  model User {
    w: Widget;
  }
}
