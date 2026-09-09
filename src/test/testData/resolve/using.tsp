namespace Common {
  model Address {}
}

namespace MyOrg {
  using Common;

  model Order {
    address: <caret>Address;
  }
}
