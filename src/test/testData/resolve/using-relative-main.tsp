import "./using-relative-dep.tsp";

namespace MyOrg.Service;

using Models;

model Order {
  a: <caret>Address;
}
