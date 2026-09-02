import "./models.tsp";
using TypeSpec.Http;

namespace Demo.Widgets {
  model `my-model`<T> extends Base is Other {
    id: string;
    `first-name`?: string;
  }
}
