import "./models.tsp";
using TypeSpec.Http;

namespace Demo.Widgets {
  model `my-model`<T> extends Base {
    id: string;
    `first-name`?: string;
  }
}
