// M5c-achievable subset of src/test/testData/lexer/kitchen-sink.tsp — NOT the full file.
// Constructs deferred to M5d (const, #suppress directives, function-type expressions) are
// deliberately absent.
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

op read(@path id: string): Widget<string> | Error;

enum Color { Red: "red", Green: "green" }

alias Greeting = "hi\nthere";

op transform(input: Widget<string>[]): void;

interface Store {
  list(): Widget<string>[];
  get(id: string): Widget<string> | Error;
}

// trailing line comment
/* block
   comment */
