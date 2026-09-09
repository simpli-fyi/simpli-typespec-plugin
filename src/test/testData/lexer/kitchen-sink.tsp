import "@typespec/http";
using Http;

@service(#{ title: "Widget Service" })
namespace DemoService;

/** A widget. */
@doc("the widget model")
model Widget<T extends string> {
  id: string;
  weight?: int32 = 10;
  `if`: boolean;
  description: string | null;
  hex: int32 = 0x1f;
  ratio: float64 = 1.5e-3;
  ...CommonProps;
}

@@doc(Widget.id, "the id");

#suppress "deprecated" "legacy"
op read(@path id: string): Widget<string> | Error;

enum Color { Red: "red", Green: "green" }

const text = """
  multi
  line
""";

alias Greeting = "hi\nthere";

op transform(): void => void;

interface Store {
  values: #[1, 2, 3];
}

// trailing line comment
/* block
   comment */
